package co.gov.solicitudes.infrastructure.adapter.in.rest;

import co.gov.solicitudes.infrastructure.idempotencia.RegistroIdempotencia;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Soporte de la cabecera {@code Idempotency-Key} en la creacion de solicitudes.
 *
 * <p><b>Que problema resuelve.</b> Un cliente envia POST /solicitudes, la solicitud se crea, y la
 * respuesta se pierde por un corte de red. El cliente no sabe si se creo y reintenta. Sin
 * idempotencia, ese reintento crea un expediente duplicado.
 *
 * <p><b>Como distingue el reintento del error.</b> Se guarda la llave junto al hash del cuerpo:
 *
 * <ul>
 *   <li>misma llave y mismo cuerpo: es el mismo comando; se devuelve la respuesta original tal
 *       cual, sin volver a ejecutar nada.
 *   <li>misma llave y cuerpo distinto: el cliente esta reutilizando una llave para otra cosa. Es
 *       un error suyo y se responde 409.
 * </ul>
 *
 * <p><b>Por que es un filtro y no un puerto de aplicacion.</b> {@code Idempotency-Key} es una
 * cabecera HTTP: pertenece al transporte. Modelarla como puerto de salida obligaria al nucleo a
 * conocer un detalle del protocolo, y los casos de uso pasarian a recibir un parametro que no
 * significa nada en el lenguaje del negocio (ADR-010).
 *
 * <p>La cabecera es opcional: si no viene, el filtro no hace nada. Es una garantia que el cliente
 * pide, no una que el servidor imponga.
 */
@Component
public class FiltroIdempotencia extends OncePerRequestFilter {

  static final String CABECERA = "Idempotency-Key";
  private static final String RUTA_PROTEGIDA = "/api/v1/solicitudes";
  private static final int LONGITUD_MAXIMA_LLAVE = 120;

  private final RegistroIdempotencia registro;

  public FiltroIdempotencia(RegistroIdempotencia registro) {
    this.registro = registro;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest peticion) {
    // Solo la creacion la necesita. Tomar una solicitud ya es idempotente por
    // naturaleza y las transiciones estan protegidas por la maquina de estados.
    return !(HttpMethod.POST.matches(peticion.getMethod())
        && RUTA_PROTEGIDA.equals(peticion.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
      throws ServletException, IOException {

    String llave = peticion.getHeader(CABECERA);
    if (llave == null || llave.isBlank() || llave.length() > LONGITUD_MAXIMA_LLAVE) {
      cadena.doFilter(peticion, respuesta);
      return;
    }

    // El cuerpo de una peticion HTTP se puede leer UNA sola vez. Para calcular el
    // hash hay que leerlo aqui, y entonces el controlador se lo encuentra vacio.
    //
    // ContentCachingRequestWrapper no sirve para este caso: memoriza lo que otro
    // lee, no reproduce lo ya leido. Se probo y el controlador recibia
    // "Required request body is missing". Hace falta un envoltorio que devuelva
    // un flujo nuevo sobre los mismos bytes cada vez que se lo pidan.
    byte[] cuerpo = peticion.getInputStream().readAllBytes();
    String hash = calcularHash(cuerpo);
    HttpServletRequest peticionReproducible = new PeticionConCuerpoReproducible(peticion, cuerpo);

    Optional<RegistroIdempotencia.RespuestaRegistrada> previa = registro.buscar(llave);
    if (previa.isPresent()) {
      responderDesdeElRegistro(respuesta, previa.get(), hash, peticion.getRequestURI());
      return;
    }

    ContentCachingResponseWrapper respuestaCacheada = new ContentCachingResponseWrapper(respuesta);
    cadena.doFilter(peticionReproducible, respuestaCacheada);

    // Solo se memoriza lo que salio bien. Registrar un error haria que el
    // reintento del cliente recibiera para siempre el mismo fallo transitorio.
    if (respuestaCacheada.getStatus() == HttpStatus.CREATED.value()) {
      registro.registrar(
          llave,
          hash,
          respuestaCacheada.getStatus(),
          new String(respuestaCacheada.getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    respuestaCacheada.copyBodyToResponse();
  }

  private void responderDesdeElRegistro(
      HttpServletResponse respuesta,
      RegistroIdempotencia.RespuestaRegistrada previa,
      String hashActual,
      String ruta)
      throws IOException {

    if (!previa.hashCuerpo().equals(hashActual)) {
      var problema =
          ProblemDetailsFactory.crear(
              HttpStatus.CONFLICT,
              "Llave de idempotencia reutilizada",
              "La llave Idempotency-Key ya se uso con un cuerpo distinto.",
              "CONFLICTO_CONCURRENCIA");
      problema.setInstance(java.net.URI.create(ruta));

      respuesta.setStatus(HttpStatus.CONFLICT.value());
      respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      respuesta.getWriter().write(problema.toString());
      return;
    }

    respuesta.setStatus(previa.estadoHttp());
    respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
    respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
    respuesta.setHeader("Idempotent-Replay", "true");
    respuesta.getWriter().write(previa.cuerpo());
  }

  private String calcularHash(byte[] cuerpo) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha256.digest(cuerpo));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 es obligatorio en toda JVM: si falta, el entorno esta roto.
      throw new IllegalStateException("La JVM no provee SHA-256.", e);
    }
  }

  /**
   * Envoltorio que permite leer el cuerpo mas de una vez.
   *
   * <p>Guarda los bytes ya consumidos y entrega un flujo nuevo sobre ellos en cada llamada, de
   * modo que el filtro pueda calcular el hash y el controlador siga recibiendo su JSON intacto.
   */
  private static final class PeticionConCuerpoReproducible extends HttpServletRequestWrapper {

    private final byte[] cuerpo;

    PeticionConCuerpoReproducible(HttpServletRequest original, byte[] cuerpo) {
      super(original);
      this.cuerpo = cuerpo.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream fuente = new ByteArrayInputStream(cuerpo);
      return new ServletInputStream() {
        @Override
        public int read() {
          return fuente.read();
        }

        @Override
        public boolean isFinished() {
          return fuente.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          // El cuerpo ya esta en memoria: no hay lectura asincrona que notificar.
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}