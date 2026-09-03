Feature: Recorrido completo REGISTRADA -> EN_ATENCION -> RESUELTA

  Background:
    * url baseUrl
    * def resultadoCategorias = call read('obtener-categorias.feature')
    * def categoriaId = resultadoCategorias.categorias[0].id

  Scenario: un analista toma y resuelve una solicitud recien registrada
    # 1. REGISTRADA — el solicitante registra
    Given path 'solicitudes'
    And header Authorization = 'Bearer ' + tokenSolicitante
    And header Idempotency-Key = java.util.UUID.randomUUID() + ''
    And request
      """
      {
        asunto: 'Correo corporativo sin acceso',
        descripcion: 'No se puede acceder al correo desde esta manana.',
        categoriaId: '#(categoriaId)',
        prioridad: 'ALTA'
      }
      """
    When method post
    Then status 201
    And match response.estado == 'REGISTRADA'
    * def solicitudId = response.id

    # 2. EN_ATENCION — un analista la toma (escenario A2: se modela como crear
    # una asignacion, no como una transicion mas)
    Given path 'solicitudes', solicitudId, 'asignaciones'
    And header Authorization = 'Bearer ' + tokenAnalista
    When method post
    Then status 201
    And match response.estado == 'EN_ATENCION'
    And match response.analistaId == '#present'

    # 3. RESUELTA — el mismo analista la resuelve
    Given path 'solicitudes', solicitudId, 'transiciones'
    And header Authorization = 'Bearer ' + tokenAnalista
    And request { accion: 'RESOLVER' }
    When method post
    Then status 200
    And match response.estado == 'RESUELTA'

    # El historial completo quedo trazado: registro, toma y resolucion.
    Given path 'solicitudes', solicitudId
    And header Authorization = 'Bearer ' + tokenSupervisor
    When method get
    Then status 200
    And match response.historial == '#[3]'
    And match response.historial[0].estadoDestino == 'REGISTRADA'
    And match response.historial[1].estadoDestino == 'EN_ATENCION'
    And match response.historial[2].estadoDestino == 'RESUELTA'

  Scenario: escenario A4 - una transicion que ya no aplica al estado actual se rechaza
    Given path 'solicitudes'
    And header Authorization = 'Bearer ' + tokenSolicitante
    And header Idempotency-Key = java.util.UUID.randomUUID() + ''
    And request
      """
      {
        asunto: 'Impresora sin tinta piso 3',
        descripcion: 'No imprime desde ayer.',
        categoriaId: '#(categoriaId)',
        prioridad: 'BAJA'
      }
      """
    When method post
    Then status 201
    * def solicitudId = response.id

    Given path 'solicitudes', solicitudId, 'asignaciones'
    And header Authorization = 'Bearer ' + tokenAnalista
    When method post
    Then status 201

    Given path 'solicitudes', solicitudId, 'transiciones'
    And header Authorization = 'Bearer ' + tokenAnalista
    And request { accion: 'RESOLVER' }
    When method post
    Then status 200
    And match response.estado == 'RESUELTA'

    # RESOLVER solo aplica desde EN_ATENCION (dominio Accion); repetirla
    # sobre una solicitud ya RESUELTA no tiene ninguna fila en la tabla de
    # transiciones que la ampare, y debe rechazarse con 422 (A4), no con un
    # 500 ni con un cambio de estado silencioso.
    Given path 'solicitudes', solicitudId, 'transiciones'
    And header Authorization = 'Bearer ' + tokenAnalista
    And request { accion: 'RESOLVER' }
    When method post
    Then status 422
    And match response.codigo == 'TRANSICION_INVALIDA'
