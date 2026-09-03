Feature: A1 - Registro valido de una solicitud

  Background:
    * url baseUrl
    * def resultadoCategorias = call read('obtener-categorias.feature')
    * def categoriaId = resultadoCategorias.categorias[0].id

  Scenario: un solicitante registra una solicitud valida y queda en REGISTRADA
    Given path 'solicitudes'
    And header Authorization = 'Bearer ' + tokenSolicitante
    And header Idempotency-Key = java.util.UUID.randomUUID() + ''
    And request
      """
      {
        asunto: 'Impresora sin tinta piso 3',
        descripcion: 'La impresora del area de coordinacion no imprime desde ayer.',
        categoriaId: '#(categoriaId)',
        prioridad: 'MEDIA'
      }
      """
    When method post
    Then status 201
    And match header Location == '#present'
    And match response.estado == 'REGISTRADA'
    And match response.codigo == '#regex SOL-\\d{4}-\\d{6}'
    And match response.solicitanteId == '#present'
    And match response.analistaId == '#null'
    # El registro es tambien la primera entrada del historial (BLUEPRINT 7.2:
    # se modela como NULL -> REGISTRADA, no se pierde la fila inicial).
    And match response.historial == '#[1]'
    And match response.historial[0].estadoOrigen == '#null'
    And match response.historial[0].estadoDestino == 'REGISTRADA'

  Scenario: reenviar la misma peticion con la misma Idempotency-Key no duplica
    * def llave = java.util.UUID.randomUUID() + ''
    * def cuerpo =
      """
      {
        asunto: 'Prueba de idempotencia',
        descripcion: 'Se envia dos veces con la misma llave.',
        categoriaId: '#(categoriaId)',
        prioridad: 'BAJA'
      }
      """

    Given path 'solicitudes'
    And header Authorization = 'Bearer ' + tokenSolicitante
    And header Idempotency-Key = llave
    And request cuerpo
    When method post
    Then status 201
    * def primeraRespuesta = response

    Given path 'solicitudes'
    And header Authorization = 'Bearer ' + tokenSolicitante
    And header Idempotency-Key = llave
    And request cuerpo
    When method post
    Then status 201
    And match response.id == primeraRespuesta.id
    And match response.codigo == primeraRespuesta.codigo
