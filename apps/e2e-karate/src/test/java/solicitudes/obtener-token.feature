Feature: obtener un token de Keycloak por direct grant

  # Invocado desde karate-config.js en tiempo de arranque, antes de que exista
  # un scenario propio: ni el paso de argumentos por karate.call(path, arg) ni
  # el objeto __arg quedan disponibles ahi (se probaron ambos y fallan con
  # ReferenceError). karate-config.js fija estas variables con karate.set(...)
  # -que si opera de forma fiable sobre el contexto de ejecucion actual- antes
  # de invocar este feature sin argumentos.
  # Keycloak (KC_HOSTNAME_STRICT=false) graba el claim "iss" del token segun
  # el header Host de ESTA peticion. Karate corre dentro de la red de compose
  # y llega a Keycloak por el nombre interno "keycloak:8080", pero los
  # backends validan el token contra el issuer que ve el NAVEGADOR
  # ("localhost:8080", ver application.yml de ambos servicios). Sin forzar el
  # header Host aqui, el token queda con iss=http://keycloak:8080/... y todo
  # backend lo rechaza con 401 aunque el token sea, en todo lo demas, valido.
  Scenario: solicitar token para un usuario de prueba
    Given url tokenUrl
    And header Host = 'localhost:8080'
    And form field client_id = 'karate-e2e'
    And form field grant_type = 'password'
    And form field username = usuario
    And form field password = password
    When method post
    Then status 200
    * def token = response.access_token
