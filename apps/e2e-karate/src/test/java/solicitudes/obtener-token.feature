Feature: obtener un token de Keycloak por direct grant

  Scenario: solicitar token para un usuario de prueba
    Given url tokenUrl
    And form field client_id = 'karate-e2e'
    And form field grant_type = 'password'
    And form field username = usuario
    And form field password = password
    When method post
    Then status 200
    * def token = response.access_token
