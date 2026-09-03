Feature: obtener el catalogo de categorias activas

  Scenario: listar categorias con cualquier usuario autenticado
    Given url baseUrl
    And path 'categorias'
    And header Authorization = 'Bearer ' + tokenSolicitante
    When method get
    Then status 200
    * def categorias = response
