#!/usr/bin/env bash
# =============================================================================
#  Escenario A5: el consumidor recibe DOS VECES el mismo evento.
#  Resultado observable exigido: los indicadores NO duplican el conteo.
#
#  El reenvio se hace publicando de nuevo, tal cual, un sobre ya procesado. Es
#  equivalente a pulsar "Publish message" en la consola de RabbitMQ, y ademas
#  reproducible sin intervencion manual.
#
#  Uso:  bash docs/evidencias/verificar-a5-idempotencia.sh
# =============================================================================
set -uo pipefail

INDICADORES=${INDICADORES:-http://localhost:8082}
KEYCLOAK=${KEYCLOAK:-http://localhost:8080}
RABBIT_API=${RABBIT_API:-http://localhost:15672/api}
RABBIT_USER=${RABBITMQ_USER:-solicitudes_local}
RABBIT_PASS=${RABBITMQ_PASSWORD:-Local#Rabbit2026}
EXCHANGE=solicitudes.events
SQL_PASS=${SQLSERVER_SA_PASSWORD:-Local#Demo2026}
SQLCMD="/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P $SQL_PASS -C -h -1 -W"

token() {
  curl -s -X POST "$KEYCLOAK/realms/solicitudes-gov/protocol/openid-connect/token" \
    -d client_id=karate-e2e -d grant_type=password \
    -d username=supervisor1 -d "password=Demo#2026" |
    node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>process.stdout.write(JSON.parse(d).access_token||''))"
}

resumen() {
  curl -s "$INDICADORES/api/v1/indicadores/resumen" -H "Authorization: Bearer $1"
}

contar() {
  docker exec solicitudes-sqlserver $SQLCMD -d indicadores_db \
    -Q "SET NOCOUNT ON; SELECT CAST(COUNT(*) AS VARCHAR) FROM $1;" 2>/dev/null |
    tr -d ' \r' | grep -E '^[0-9]+$' | head -1
}

T=$(token)
[ -z "$T" ] && { echo "No se pudo obtener token."; exit 1; }

# ---------------------------------------------------------------------------
dlq_mensajes() {
  docker exec solicitudes-rabbitmq rabbitmqctl list_queues name messages 2>/dev/null |
    grep 'indicadores.solicitudes.dlq' | awk '{print $2}'
}

echo "=============================================================="
echo " ANTES del reenvio"
echo "=============================================================="
ANTES_RESUMEN=$(resumen "$T")
ANTES_HECHOS=$(contar hecho_transicion)
ANTES_PROCESADOS=$(contar evento_procesado)
# La DLQ puede no estar en cero: otras pruebas de esta sesion (payloads
# invalidos, reintentos) pudieron dejar mensajes ahi antes de este escenario.
# Lo que A5 exige es que ESTE reenvio no le agregue uno, no que la cola este
# vacia en terminos absolutos.
ANTES_DLQ=$(dlq_mensajes); ANTES_DLQ=${ANTES_DLQ:-0}
echo "  resumen          : $ANTES_RESUMEN"
echo "  hecho_transicion : $ANTES_HECHOS filas"
echo "  evento_procesado : $ANTES_PROCESADOS filas"
echo "  dlq (referencia) : $ANTES_DLQ mensajes"
echo

# ---------------------------------------------------------------------------
# Se toma un sobre YA PROCESADO del outbox del productor. Conserva su eventId
# original, que es lo que hace que el reenvio sea un duplicado real y no un
# evento nuevo.
# ---------------------------------------------------------------------------
echo "=============================================================="
echo " Reenviando un evento ya procesado, con su eventId original"
echo "=============================================================="
# -y 0 evita que sqlcmd trunque la columna al ancho por defecto, que partiria el
# JSON. Es incompatible con -W y con -h, asi que se omiten ambos y la salida se
# limpia despues: se recortan espacios y se toma la unica linea que abre en '{'.
PAYLOAD=$(docker exec solicitudes-sqlserver /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "$SQL_PASS" -C -y 0 -d solicitudes_db \
  -Q "SET NOCOUNT ON; SELECT TOP 1 payload FROM outbox_evento WHERE tipo='SolicitudRegistrada' ORDER BY ocurrido_en DESC;" 2>/dev/null |
  tr -d '\r' | sed 's/[[:space:]]*$//' | grep '^{' | head -1)

if [ -z "$PAYLOAD" ]; then
  echo "  No hay eventos en el outbox. Ejecute antes verificar-escenarios.sh"
  exit 1
fi

EVENT_ID=$(echo "$PAYLOAD" | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>process.stdout.write(JSON.parse(d).eventId))")
ROUTING=$(echo "$PAYLOAD" | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>process.stdout.write('solicitud.'+JSON.parse(d).type.replace('Solicitud','').toLowerCase()))")
echo "  eventId reenviado : $EVENT_ID"
echo "  routing key       : $ROUTING"

CUERPO=$(node -e "
  const payload = process.argv[1];
  process.stdout.write(JSON.stringify({
    properties: {}, routing_key: process.argv[2], payload: payload, payload_encoding: 'string'
  }));
" "$PAYLOAD" "$ROUTING")

PUBLICACION=$(curl -s -u "$RABBIT_USER:$RABBIT_PASS" -H "Content-Type: application/json" \
  -X POST "$RABBIT_API/exchanges/%2F/$EXCHANGE/publish" -d "$CUERPO")
echo "  publicado en el exchange: $PUBLICACION"
echo

echo "  Esperando a que el consumidor lo procese..."
sleep 6
echo

# ---------------------------------------------------------------------------
echo "=============================================================="
echo " DESPUES del reenvio"
echo "=============================================================="
DESPUES_RESUMEN=$(resumen "$T")
DESPUES_HECHOS=$(contar hecho_transicion)
DESPUES_PROCESADOS=$(contar evento_procesado)
echo "  resumen          : $DESPUES_RESUMEN"
echo "  hecho_transicion : $DESPUES_HECHOS filas"
echo "  evento_procesado : $DESPUES_PROCESADOS filas"
echo

# ---------------------------------------------------------------------------
echo "=============================================================="
echo " VEREDICTO"
echo "=============================================================="
FALLOS=0
[ "$ANTES_RESUMEN" = "$DESPUES_RESUMEN" ] &&
  echo "  [OK] El resumen es identico: los conteos no se duplicaron." ||
  { echo "  [FALLO] El resumen cambio."; FALLOS=1; }

[ "$ANTES_HECHOS" = "$DESPUES_HECHOS" ] &&
  echo "  [OK] hecho_transicion sigue en $DESPUES_HECHOS filas: no se inserto el hecho." ||
  { echo "  [FALLO] Se insertaron hechos: $ANTES_HECHOS -> $DESPUES_HECHOS"; FALLOS=1; }

[ "$ANTES_PROCESADOS" = "$DESPUES_PROCESADOS" ] &&
  echo "  [OK] evento_procesado sigue en $DESPUES_PROCESADOS: la clave primaria rechazo el duplicado." ||
  { echo "  [FALLO] evento_procesado cambio: $ANTES_PROCESADOS -> $DESPUES_PROCESADOS"; FALLOS=1; }

DESPUES_DLQ=$(dlq_mensajes); DESPUES_DLQ=${DESPUES_DLQ:-0}
[ "$ANTES_DLQ" = "$DESPUES_DLQ" ] &&
  echo "  [OK] La DLQ no crecio con este reenvio ($ANTES_DLQ -> $DESPUES_DLQ): el duplicado se confirma, no es un error." ||
  { echo "  [FALLO] La DLQ crecio: $ANTES_DLQ -> $DESPUES_DLQ. El duplicado se trato como fallo."; FALLOS=1; }

echo
echo "  Traza del consumidor:"
docker logs indicadores-app 2>&1 | grep "ya estaba proyectado" | tail -2 | sed 's/^/    /'
echo
[ "$FALLOS" -eq 0 ] && echo "  RESULTADO: A5 SE CUMPLE" || echo "  RESULTADO: A5 NO SE CUMPLE"
