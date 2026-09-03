#!/usr/bin/env bash
# =============================================================================
#  Verificacion de los escenarios de aceptacion A1 a A4 contra el stack local.
#
#  Requiere el stack levantado y el Servicio de Solicitudes escuchando en 8081.
#  Obtiene tokens reales de Keycloak mediante el cliente karate-e2e, que existe
#  exclusivamente para pruebas automatizadas.
#
#  Uso:  bash docs/evidencias/verificar-escenarios.sh
# =============================================================================
set -uo pipefail

API=${API:-http://localhost:8081}
KEYCLOAK=${KEYCLOAK:-http://localhost:8080}
REALM=solicitudes-gov
CLIENTE=karate-e2e
CLAVE='Demo#2026'
CATEGORIA_SOPORTE='11111111-1111-4111-8111-111111111111'

token() {
  curl -s -X POST "$KEYCLOAK/realms/$REALM/protocol/openid-connect/token" \
    -d "client_id=$CLIENTE" -d "grant_type=password" \
    -d "username=$1" -d "password=$CLAVE" |
    node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>process.stdout.write(JSON.parse(d).access_token||''))"
}

# Imprime el codigo HTTP y los campos relevantes del cuerpo.
mostrar() {
  local etiqueta="$1" cuerpo="$2" http="$3"
  echo "  HTTP $http"
  echo "$cuerpo" | node -e "
    let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{
      try{
        const j=JSON.parse(d);
        for (const k of ['codigo','estado','title','detail','analistaId'])
          if (j[k] !== undefined) console.log('  '+k.padEnd(10)+': '+j[k]);
      }catch(e){ console.log('  (cuerpo vacio)'); }
    })"
}

peticion() {
  local metodo="$1" ruta="$2" tk="$3" cuerpo="${4:-}"
  if [ -n "$cuerpo" ]; then
    curl -s -w "\n%{http_code}" -X "$metodo" "$API$ruta" \
      -H "Authorization: Bearer $tk" -H "Content-Type: application/json" -d "$cuerpo"
  else
    curl -s -w "\n%{http_code}" -X "$metodo" "$API$ruta" -H "Authorization: Bearer $tk"
  fi
}

echo "Obteniendo tokens de Keycloak..."
T_SOL=$(token solicitante1)
T_AN1=$(token analista1)
T_AN2=$(token analista2)
T_SUP=$(token supervisor1)
T_NADIE=$(token sinrol1)
[ -z "$T_SOL" ] && { echo "No se pudo obtener token. Esta el stack arriba?"; exit 1; }
echo "Tokens obtenidos para los cinco usuarios de prueba."
echo

# ---------------------------------------------------------------------------
echo "=============================================================="
echo " A1  Solicitante autenticado registra datos validos"
echo " Esperado: 201, estado REGISTRADA, historial y evento creados"
echo "=============================================================="
RESP=$(peticion POST /api/v1/solicitudes "$T_SOL" \
  '{"asunto":"Servidor de nomina sin respuesta","descripcion":"El servicio no responde desde las 8am.","categoriaId":"'"$CATEGORIA_SOPORTE"'","prioridad":"ALTA"}')
HTTP=$(echo "$RESP" | tail -1)
CUERPO=$(echo "$RESP" | sed '$d')
mostrar "A1" "$CUERPO" "$HTTP"
ID=$(echo "$CUERPO" | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>process.stdout.write(JSON.parse(d).id))")
echo "  id        : $ID"
echo

# ---------------------------------------------------------------------------
echo "=============================================================="
echo " A3  Usuario sin rol intenta cerrar"
echo " Esperado: 403 y ningun cambio persistido ni evento emitido"
echo "=============================================================="
RESP=$(peticion POST "/api/v1/solicitudes/$ID/transiciones" "$T_NADIE" '{"accion":"CERRAR"}')
mostrar "A3" "$(echo "$RESP" | sed '$d')" "$(echo "$RESP" | tail -1)"
echo

# ---------------------------------------------------------------------------
echo "=============================================================="
echo " A4  Transicion imposible: CERRAR sobre una solicitud REGISTRADA"
echo " Esperado: 422 con Problem Details explicativo"
echo "=============================================================="
RESP=$(peticion POST "/api/v1/solicitudes/$ID/transiciones" "$T_SUP" '{"accion":"CERRAR"}')
mostrar "A4" "$(echo "$RESP" | sed '$d')" "$(echo "$RESP" | tail -1)"
echo

# ---------------------------------------------------------------------------
echo "=============================================================="
echo " A2  Dos analistas toman la misma solicitud EN PARALELO"
echo " Esperado: uno 201 y el otro 409, sin doble asignacion"
echo "=============================================================="
# Se lanzan de verdad en paralelo con &: secuencialmente el segundo fallaria
# por transicion invalida (422) y no por conflicto de concurrencia (409),
# que es lo que el escenario quiere demostrar.
peticion POST "/api/v1/solicitudes/$ID/asignaciones" "$T_AN1" > /tmp/a2_analista1.out &
P1=$!
peticion POST "/api/v1/solicitudes/$ID/asignaciones" "$T_AN2" > /tmp/a2_analista2.out &
P2=$!
wait $P1 $P2

for n in 1 2; do
  echo "  --- analista$n ---"
  mostrar "A2" "$(sed '$d' /tmp/a2_analista$n.out)" "$(tail -1 /tmp/a2_analista$n.out)"
done

# Se cuenta archivo por archivo. Concatenar los dos "tail -1" en una variable
# une las lineas cuando el ultimo no termina en salto, y el conteo daba 0.
EXITOS=0
CONFLICTOS=0
for n in 1 2; do
  CODIGO=$(tail -1 /tmp/a2_analista$n.out | tr -d '\r\n')
  [ "$CODIGO" = "201" ] && EXITOS=$((EXITOS + 1))
  [ "$CODIGO" = "409" ] && CONFLICTOS=$((CONFLICTOS + 1))
done
echo
echo "  Asignaciones exitosas : $EXITOS  (debe ser exactamente 1)"
echo "  Conflictos 409        : $CONFLICTOS  (debe ser exactamente 1)"
if [ "$EXITOS" -ne 1 ] || [ "$CONFLICTOS" -ne 1 ]; then
  echo "  RESULTADO: A2 NO SE CUMPLE"
else
  echo "  RESULTADO: A2 se cumple, no hubo doble asignacion"
fi
echo

# ---------------------------------------------------------------------------
echo "=============================================================="
echo " Recorrido completo REGISTRADA -> EN_ATENCION -> RESUELTA -> CERRADA"
echo "=============================================================="
GANADOR=$T_AN1
grep -q '^201$' <(tail -1 /tmp/a2_analista2.out) && GANADOR=$T_AN2

RESP=$(peticion POST "/api/v1/solicitudes/$ID/observaciones" "$GANADOR" '{"texto":"Se reinicio el servicio y se valido con el area usuaria."}')
echo "  observacion   -> HTTP $(echo "$RESP" | tail -1)"
RESP=$(peticion POST "/api/v1/solicitudes/$ID/transiciones" "$GANADOR" '{"accion":"RESOLVER"}')
echo "  RESOLVER      -> HTTP $(echo "$RESP" | tail -1)"
RESP=$(peticion POST "/api/v1/solicitudes/$ID/transiciones" "$T_SUP" '{"accion":"DEVOLVER","motivo":"Falta adjuntar la evidencia de la validacion."}')
echo "  DEVOLVER      -> HTTP $(echo "$RESP" | tail -1)"
RESP=$(peticion POST "/api/v1/solicitudes/$ID/transiciones" "$GANADOR" '{"accion":"RESOLVER"}')
echo "  RESOLVER      -> HTTP $(echo "$RESP" | tail -1)"
RESP=$(peticion POST "/api/v1/solicitudes/$ID/transiciones" "$T_SUP" '{"accion":"CERRAR"}')
echo "  CERRAR        -> HTTP $(echo "$RESP" | tail -1)"
echo

echo "=============================================================="
echo " Linea de tiempo final del expediente"
echo "=============================================================="
peticion GET "/api/v1/solicitudes/$ID" "$T_SUP" | sed '$d' | node -e "
  let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{
    const j=JSON.parse(d);
    console.log('  codigo:',j.codigo,'  estado final:',j.estado);
    j.historial.forEach(h=>console.log('   ',(h.estadoOrigen||'(inicio)').padEnd(12),'->',h.estadoDestino.padEnd(12),h.actorRol.padEnd(12),h.motivo||''));
    console.log('  observaciones:',j.observaciones.length);
  })"
echo

echo "=============================================================="
echo " Aislamiento por rol: el solicitante solo ve lo suyo"
echo "=============================================================="
peticion GET "/api/v1/solicitudes?size=100" "$T_SOL" | sed '$d' | node -e "
  let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{
    const j=JSON.parse(d);
    const ajenas=j.content.filter(s=>s.solicitanteId!==j.content[0]?.solicitanteId).length;
    console.log('  solicitudes visibles para solicitante1:',j.totalElements);
    console.log('  de otros solicitantes:',ajenas,'(debe ser 0)');
  })"
