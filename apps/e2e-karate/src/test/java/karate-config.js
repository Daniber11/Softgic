/**
 * Configuracion global de Karate.
 *
 * Obtiene un token por usuario de prueba, una sola vez por corrida, contra el
 * cliente `karate-e2e` (direct grant, exclusivo para pruebas automatizadas —
 * BLUEPRINT 8.1). Los features consumen `tokenSolicitante`, `tokenAnalista`,
 * `tokenSupervisor` y `tokenSinRol`; ninguno vuelve a autenticarse por su cuenta.
 */
function fn() {
  var config = {
    baseUrl: karate.properties['baseUrl'] || 'http://localhost:8081/api/v1',
    keycloakTokenUrl:
      karate.properties['keycloakTokenUrl'] ||
      'http://localhost:8080/realms/solicitudes-gov/protocol/openid-connect/token'
  };

  function obtenerToken(usuario) {
    var resultado = karate.call('classpath:obtener-token.feature', {
      tokenUrl: config.keycloakTokenUrl,
      usuario: usuario,
      password: 'Demo#2026'
    });
    return resultado.token;
  }

  config.tokenSolicitante = obtenerToken('solicitante1');
  config.tokenAnalista = obtenerToken('analista1');
  config.tokenSupervisor = obtenerToken('supervisor1');
  config.tokenSinRol = obtenerToken('sinrol1');

  karate.configure('connectTimeout', 10000);
  karate.configure('readTimeout', 10000);

  return config;
}
