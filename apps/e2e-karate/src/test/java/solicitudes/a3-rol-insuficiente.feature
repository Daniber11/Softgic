Feature: A3 - Un usuario sin rol intenta una accion restringida

  Background:
    * url baseUrl
    * def resultadoCategorias = call read('obtener-categorias.feature')
    * def categoriaId = resultadoCategorias.categorias[0].id

    # Solicitud de referencia, registrada por un SOLICITANTE real.
    Given path 'solicitudes'
    And header Authorization = 'Bearer ' + tokenSolicitante
    And header Idempotency-Key = java.util.UUID.randomUUID() + ''
    And request
      """
      {
        asunto: 'Servidor de nomina sin respuesta',
        descripcion: 'El servicio de nomina no responde desde las 8am.',
        categoriaId: '#(categoriaId)',
        prioridad: 'ALTA'
      }
      """
    When method post
    Then status 201
    * def solicitudId = response.id

  Scenario: sinrol1 intenta cerrar y recibe 403 sin ningun efecto persistido
    Given path 'solicitudes', solicitudId, 'transiciones'
    And header Authorization = 'Bearer ' + tokenSinRol
    And request { accion: 'CERRAR' }
    When method post
    Then status 403

    # La solicitud sigue exactamente donde estaba: A3 exige "sin efectos
    # persistidos", no solo el codigo de estado correcto.
    Given path 'solicitudes', solicitudId
    And header Authorization = 'Bearer ' + tokenSupervisor
    When method get
    Then status 200
    And match response.estado == 'REGISTRADA'
    And match response.historial == '#[1]'

  Scenario: sinrol1 intenta tomar y recibe 403 sin ningun efecto persistido
    Given path 'solicitudes', solicitudId, 'asignaciones'
    And header Authorization = 'Bearer ' + tokenSinRol
    When method post
    Then status 403

    Given path 'solicitudes', solicitudId
    And header Authorization = 'Bearer ' + tokenSupervisor
    When method get
    Then status 200
    And match response.estado == 'REGISTRADA'
    And match response.analistaId == '#null'
