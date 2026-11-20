> Resultados de la etapa 36: históricos. La API vigente incorpora también FIFO, QR/asignaciones, preferencias, notificaciones y reportes, descritos en [matriz E2](entrega-final.md) y [OpenAPI](openapi-publico.json).

# Autorización por roles y propiedad — Implementación 36

La etapa 35 validó identidad y enlaces técnicos; esta etapa habilita los circuitos humanos con controles de propiedad. No despliega cambios en el entorno persistente.

## Identidad y registros históricos

Venue, Event, Reserva y Compra incorporan identity_owner_id nullable e inmutable para actualizaciones JPA. Se establece con sub del JWT humano validado al crear el recurso. Los IDs declarados organizerId, idCliente y clienteId deben coincidir con sub antes de ejecutar el controlador. No existe backfill ni asociación por coincidencia numérica. Un registro histórico con propietario NULL queda fuera del acceso del cliente/organizador, aunque sus IDs coincidan. Su eventual vinculación requiere una migración revisada por separado.

Sectores heredan propiedad del lugar; pagos y devoluciones, de la compra. Entradas heredan la identidad inmutable de la compra mediante la proyección técnica de la orden. ADMINISTRADOR puede consultar globalmente y administrar recursos autorizados; no puede reservar, comprar, pagar ni solicitar devoluciones en nombre de clientes. Para crear un evento también declara su propia identidad: no existe delegación implícita de organizador.

## Fronteras de autorización

RoutePolicy aplica rol, tipo de token, caller y scope en Gateway y servicios. ResourceOwnership aplica guardas MVC antes del controlador y de leer el cuerpo hacia negocio. Las verificaciones de DTO usan RequestBodyAdvice; las de rutas y queries usan HandlerInterceptor. No hay perfiles que omitan seguridad. El actor proviene exclusivamente de SecurityContext con JWT validado. TrustedActorHeaders descarta encabezados externos y sólo deriva el actor administrativo de un token humano administrador.

Los recursos ajenos o sin propietario devuelven 404. Rol incorrecto o suplantación explícita de identidad devuelve 403; falta/token inválido, 401. Un fallo técnico al consultar propiedad no se interpreta como permiso: falla cerrado.

| Servicio | Método y ruta | Autorización adicional |
|---|---|---|
| Eventos | POST /api/v1/venues | ORGANIZADOR/ADMINISTRADOR; propietario desde JWT |
| Eventos | POST /api/v1/venues/{id}/sectors | Propietario del lugar o ADMINISTRADOR |
| Eventos | POST /api/v1/events | ORGANIZADOR/ADMINISTRADOR; organizerId propio y lugar propio o acceso administrativo |
| Eventos | POST /api/v1/events/{id}/prices; PATCH .../publish | Propietario del evento o ADMINISTRADOR |
| Eventos | GET /api/v1/events/{id} | Humano autenticado o Compras técnico; borrador sólo dueño/ADMINISTRADOR o Compras técnico |
| Eventos | PATCH .../cancel, .../suspend, .../status | ADMINISTRADOR validado |
| Eventos | POST/PUT/DELETE /api/v1/participants[/{id}] | ADMINISTRADOR |
| Catálogo | GET venues, sectors/seats, participants, events/search | Mantiene consultas públicas y filtro de estados públicos de búsqueda |
| Inventario | POST /reservas | CLIENTE; idCliente = sub |
| Inventario | GET /reservas/{id}; GET /reservas?idCliente | CLIENTE propio o ADMINISTRADOR; listas de cliente filtran propietario inmutable |
| Inventario | GET /reservas/{id} técnico | Sólo Compras + reservations.read |
| Compras | POST /compras | CLIENTE; clienteId propio y reserva con identidad propietaria verificada |
| Compras | GET /compras/{id}[/estado,/detalle,/entradas]; GET /compras?clienteId | Dueño o ADMINISTRADOR; control antes de registrar correspondencias de tickets |
| Compras | POST /compras/{id}/cancelar | Sólo CLIENTE propietario |
| Compras | POST /pagos; GET /pagos/{id}; GET /pagos?compraId | POST sólo CLIENTE propietario; GET dueño/ADMINISTRADOR |
| Compras | POST /devoluciones; GET /devoluciones/{id} | POST CLIENTE propio y compra propia; GET dueño/ADMINISTRADOR |
| Compras | /admin/devoluciones y /admin/reintegros | ADMINISTRADOR; identidad de actor derivada del JWT |
| Entradas | GET /tickets?orderId | CLIENTE propietario de la orden o ADMINISTRADOR; valida incluso con lista vacía |
| Entradas | GET /tickets?clientId | CLIENTE propio o ADMINISTRADOR; filtra órdenes históricas/ajenas |
| Entradas | Emisión, anulación y cambios internos | Continúan limitados a caller técnico y scopes mínimos de etapa 35 |
| Todos | GET /v3/api-docs, /swagger-ui/* | ADMINISTRADOR en el servicio; no se agregan rutas de documentación al Gateway |

PERSONAL_ACCESO no tiene permisos de consulta comercial. La etapa 36 aún no incluía QR; la versión vigente permite validar únicamente con rol y asignación al evento, según [validación QR](validacion-qr.md). No se cambia ningún enlace técnico ni se reenvía un token humano como credencial de servicio.

## Consultas de órdenes y emisión

OrderClient.getOrderForOwnership obtiene la proyección estructural autenticada, incluido identityOwnerId, sin exigir que la compra esté completada. Esto permite autorizar una consulta vacía durante un pago incierto/rechazado. getOrder sigue exigiendo CONFIRMADA: la emisión no se debilita. La consulta por cliente verifica cada orden distinta y filtra históricos; esto añade una consulta técnica por orden distinta (optimización pendiente si crece el volumen).

## Cancelación, idempotencia y recuperación

Antes del primer efecto remoto de cancelación, Eventos persiste administrador original, jti e instante bajo bloqueo pesimista de la fila. El registro no se sobrescribe en reintentos, aunque otro administrador continúe. No persiste el JWT completo. Sólo un administrador autenticado puede iniciar/reanudar por HTTP. Eventos usa su identidad técnica al coordinar Entradas, Inventario y Compras; Compras conserva la comprobación del estado autoritativo del evento. No se modifica la máquina de estados ni las claves idempotentes de pagos/reintegros.

Las guardas de propiedad preceden la búsqueda/repetición idempotente: conocer una clave ajena no permite consultar ni reejecutar la operación. No se agregan reintentos automáticos de POST.

## Validación reproducible y despliegue

1. Ejecutar node TicketYa/integration-tests/e2/test-security.cjs: Maven/JUnit en contenedores efímeros y bases H2 en memoria restringidas por las guardas existentes. No hereda el entorno JDBC del host.
2. Ejecutar node --test TicketYa/integration-tests/e2/isolation.test.cjs TicketYa/integration-tests/e2/security-isolation.test.cjs.
3. Construir las seis imágenes con node TicketYa/integration-tests/e2/build-images.cjs. Retaggear imágenes locales no sustituye contenedores existentes.
4. Ejecutar node TicketYa/integration-tests/e2/run-regression.cjs --security --port 48083, seleccionando un puerto libre de 48080..48089. Crea un proyecto/volumen/esquemas nuevos, Identidad real, dos organizadores y personal de acceso provisionados, tres clientes registrados, administrador controlado y siete clientes técnicos de scopes mínimos.

El runner acredita imágenes, entornos, redes, claves públicas y volumen antes de mutaciones. Toda operación comercial pasa por el Gateway del proyecto nuevo en loopback; SQL comercial es sólo SELECT. Conserva los recursos y reportes ignorados para auditoría; no ejecuta down, rm, stop ni limpieza automática. Valida publicación repetida, reserva, pago/emisión idempotentes, devolución parcial, cancelación repetida, rechazo y recuperación de cobro incierto, con negativas de propiedad, roles, headers e IDs falsificados.

El despliegue persistente requiere aprobación específica: las columnas nuevas y las claves/Identidad no se aplicaron allí. Mantener los contenedores existentes no constituye una validación JWT de ese entorno.

## Resultados ejecutados

Verificado al retomar el 14/11/2026. La ejecución Maven lanzada antes de la interrupción finalizó correctamente; se revisaron logs y reportes, y se comprobó que no hubo cambios posteriores en los fuentes Java. No se declara una segunda ejecución Maven en la reanudación.

| Proyecto | JUnit aprobados | Fallos / errores / omitidos |
|---|---:|---|
| Gateway | 6 | 0 / 0 / 0 |
| Eventos | 39 | 0 / 0 / 0 |
| Inventario | 43 | 0 / 0 / 0 |
| Entradas | 35 | 0 / 0 / 0 |
| Compras | 297 | 0 / 0 / 0 |
| Total | 420 | 0 / 0 / 0 |

También pasaron las 30 pruebas Node de aislamiento y sincronización de políticas. Se reactivaron los 26 casos HTTP omitidos en la etapa 35; no se desactivó JWT ni se agregó permitAll.

Se construyeron las seis imágenes y se ejecutó la regresión en ticketya-e2-reg-15d96ae502d88539, con Gateway en 127.0.0.1:48083, siete contenedores y cinco esquemas nuevos. Resultado PASS: 92 comprobaciones HTTP de seguridad y 168 solicitudes comerciales, incluidas 71 negativas. Identidades reales: administrador 1, organizadores 2/3, personal de acceso 4 y clientes comerciales 6/7/8; el cliente 5 corresponde al smoke inicial de seguridad. Son IDs exclusivos de este entorno.

| Escenario | IDs evento / reserva / compra / pago | Resultado |
|---|---|---|
| Numerado, devolución parcial y cancelación | 1 / 1 / 1 / 1 | Pago inicial 30000; devolución parcial 15000 y resto reintegrado al cancelar; dos tickets anulados; evento bloqueado y disponibilidad 0 |
| Pago rechazado | 2 / 2 / 2 / 2 | Importe 150000 rechazado; compra FALLIDA, reserva LIBERADA, disponibilidad 2 y ningún ticket/lote |
| Pago incierto recuperado | 3 / 3 / 3 / 3 | Un único cobro 15000; mismo ID y referencia al recuperar; compra COMPLETADA, reserva CONFIRMADA, un ticket y disponibilidad 1 |

Los registros de la pasarela simulada confirman tres intentos de cobro, dos aprobados por un total de 45000 y uno rechazado. Hay exactamente dos reintegros por 30000, tres tickets totales y dos lotes de emisión. Se verificó que repetir pagos, reintegro y cancelación no duplica esas operaciones, y que el actor/jti/instante original de cancelación no cambia al repetirla.

Las negativas incluyen acceso cruzado a reservas, compras, pagos, devoluciones y tickets; orden sin tickets; organizador ajeno a evento/lugar; DTO y query con ID falsificado; encabezados administrativos falsificados; personal de acceso consultando tickets; administrador intentando pagar; tokens humanos en rutas internas y técnicos en rutas humanas; firma, emisor, audiencia, fechas y scopes inválidos. Los registros históricos NULL permanecen sin adjudicar en las pruebas JUnit.

Los seis servicios persistentes siguen running, sin reinicios, y MySQL healthy. Los siete contenedores E2 también quedan running sin reinicios. El runner comparó ID, estado, inicio, finalización y reinicios de todos los contenedores preexistentes, incluidos históricos: sin cambios. No se eliminaron recursos ni se ejecutó migración de Identidad sobre MySQL persistente.

Se compararon nuevamente los hashes de dumps de datos, ordenados por clave primaria y de sólo lectura, contra la línea base anterior a esta implementación: coincidencia exacta para los cuatro esquemas persistentes. El volumen ticketya-e2-infra_mysql_data permanece conservado.

Evidencias locales ignoradas por Git:

- TicketYa/integration-tests/target/security/15d96ae502d88539/report.json.
- TicketYa/integration-tests/target/e36-integrity.json.
- TicketYa/integration-tests/target/e36-git-status.json.
- TicketYa/integration-tests/target/e36-build.log y e36-regression.log.
- target/security-tests.log y target/surefire-reports de cada proyecto Maven.

## Correcciones y límites de esta etapa

Se corrigieron las expectativas HTTP previas a JWT, se autenticaron las pruebas antes anónimas y se ajustaron fixtures para marcar propietarios desde un principal firmado. Se agregó respuesta JSON fija para 401/403 sin detalles de excepciones. Se conservó la validación CONFIRMADA para emitir, separándola de la consulta de titularidad. En la reanudación no se encontró otro defecto de autorización: se completaron los oráculos financieros y de persistencia del actor de cancelación del runner, y se ejecutó la regresión que faltaba.

No quedan pruebas obligatorias de esta etapa pendientes. El despliegue en bases persistentes y la vinculación administrativa de recursos históricos requieren aprobación independiente. Las imágenes locales :dev fueron actualizadas, pero los contenedores persistentes conservan sus imágenes anteriores: no ejecutar su recreación antes de revisar configuración, Identidad, claves y migraciones.

Queda como límite de rendimiento una consulta técnica por orden distinta al listar tickets de un cliente. Hay avisos de deprecación de validación sobre List, del agente Mockito/JDK y de SpringDoc habilitado; no hubo líneas ERROR en los seis servicios del proyecto E2. El WARN del pago incierto fue el resultado esperado del escenario y se recuperó correctamente. No se cambiaron versiones ni se modificaron esos avisos para hacer pasar pruebas.

Git: entrega-2, HEAD 6585976, staging vacío. El acumulado de etapas 35 y 36 contiene 54 archivos versionados modificados y 69 nuevos (123 rutas). Se verificó que ninguna credencial del .env ni bloques de claves privadas estén en esos archivos; .env y reportes siguen ignorados. Sin commits ni pushes. Se conserva todo el trabajo de la etapa 35.


## Inventario Git acumulado de las etapas 35–36

Las rutas siguientes son el estado completo sin staging; no implican que todos esos cambios hayan sido creados en la etapa 36.

```text
 M .env.example
 M README.md
 M TicketYa/EventoCatalogo_Service/pom.xml
 M TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/domain/Event.java
 M TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/domain/Venue.java
 M TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/repository/EventRepository.java
 M TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/service/EventServiceImpl.java
 M TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/service/EventWorkflow.java
 M TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/service/VenueService.java
 M TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/PublicacionInterrumpidaTest.java
 M TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/PublicacionRecuperacionTest.java
 M TicketYa/EventoCatalogo_Service/src/test/resources/META-INF/spring.factories
 M TicketYa/api-gateway/pom.xml
 M TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/GatewayApplication.java
 M TicketYa/api-gateway/src/main/resources/application.properties
 M TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/GatewayRoutingTest.java
 M TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/GatewayUnavailableTest.java
 M TicketYa/comprapago_service/pom.xml
 M TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/client/ReservaClientSimulado.java
 M TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/client/RestEntradaClient.java
 M TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/client/RestEventoClient.java
 M TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/client/RestReservaClient.java
 M TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/client/RestSupport.java
 M TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/controller/DevolucionAdminController.java
 M TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/controller/OrdenInternaController.java
 M TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/dto/ReservaDTO.java
 M TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/entity/Compra.java
 M TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/service/CompraService.java
 M TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/EntornoPruebasSeguro.java
 M TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/controller/ApiIntegracionTest.java
 M TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/controller/SeleccionEntradaYActorTest.java
 M TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/service/DevolucionHttpIntegracionTest.java
 M TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/service/PagoServiceTest.java
 M TicketYa/comprapago_service/src/test/resources/META-INF/spring.factories
 M TicketYa/comprapago_service/src/test/resources/application.properties
 M TicketYa/entradas_service/pom.xml
 M TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/config/RestClientConfiguration.java
 M TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/integration/OrderClient.java
 M TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/integration/OrderSnapshot.java
 M TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/integration/RestComprasOrderClient.java
 M TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/service/TicketQueryService.java
 M TicketYa/entradas_service/src/test/java/ar/edu/tudai/ticketya/servicio_entradas/TicketIssuanceIntegrationTest.java
 M TicketYa/entradas_service/src/test/resources/META-INF/spring.factories
 M TicketYa/integration-tests/e2/build-images.cjs
 M TicketYa/integration-tests/e2/run-regression.cjs
 M TicketYa/inventarioReserva_service/pom.xml
 M TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/dtos/ReservaResponse.java
 M TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/entities/Reserva.java
 M TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/services/ReservaService.java
 M TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/controllers/InventarioControllerTest.java
 M TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/controllers/ReservaControllerTest.java
 M TicketYa/inventarioReserva_service/src/test/resources/META-INF/spring.factories
 M docker-compose.yml
 M docs/entrega2/docker-regresion.md
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/CurrentActor.java
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/DistributedSecurity.java
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/JwtTrust.java
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/ResourceOwnership.java
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/RoutePolicy.java
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/SecurityErrors.java
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/SecurityReadiness.java
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/ServiceTokens.java
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/TechnicalAuthErrors.java
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/TechnicalAuthFailure.java
?? TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/TrustedActorHeaders.java
?? TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/DistributedSecurityHttpTest.java
?? TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/HistoricalOwnershipTest.java
?? TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/SecurityTestKeys.java
?? TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/security/GatewaySecurity.java
?? TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/security/JwtTrust.java
?? TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/security/RoutePolicy.java
?? TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/DistributedSecurityHttpTest.java
?? TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/SecurityTestKeys.java
?? TicketYa/api-gateway/src/test/resources/META-INF/spring.factories
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/CurrentActor.java
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/DistributedSecurity.java
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/JwtTrust.java
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/ResourceOwnership.java
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/RoutePolicy.java
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/SecurityErrors.java
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/SecurityReadiness.java
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/ServiceTokens.java
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/TechnicalAuthErrors.java
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/TechnicalAuthFailure.java
?? TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/TrustedActorHeaders.java
?? TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/DistributedSecurityHttpTest.java
?? TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/SecurityTestKeys.java
?? TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/client/RestSupportSecurityTest.java
?? TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/security/ServiceTokensTest.java
?? TicketYa/comprapago_service/src/test/resources/application-test.properties
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/CurrentActor.java
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/DistributedSecurity.java
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/JwtTrust.java
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/ResourceOwnership.java
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/RoutePolicy.java
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/SecurityErrors.java
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/SecurityReadiness.java
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/ServiceTokens.java
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/TechnicalAuthErrors.java
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/TechnicalAuthFailure.java
?? TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/TrustedActorHeaders.java
?? TicketYa/entradas_service/src/test/java/ar/edu/tudai/ticketya/servicio_entradas/DistributedSecurityHttpTest.java
?? TicketYa/entradas_service/src/test/java/ar/edu/tudai/ticketya/servicio_entradas/SecurityTestKeys.java
?? TicketYa/integration-tests/e2/commercial-smoke.cjs
?? TicketYa/integration-tests/e2/run-security.cjs
?? TicketYa/integration-tests/e2/security-isolation.cjs
?? TicketYa/integration-tests/e2/security-isolation.test.cjs
?? TicketYa/integration-tests/e2/security-smoke.cjs
?? TicketYa/integration-tests/e2/test-security.cjs
?? TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/CurrentActor.java
?? TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/DistributedSecurity.java
?? TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/JwtTrust.java
?? TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/ResourceOwnership.java
?? TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/RoutePolicy.java
?? TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/SecurityErrors.java
?? TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/SecurityReadiness.java
?? TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/TechnicalAuthErrors.java
?? TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/TechnicalAuthFailure.java
?? TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/TrustedActorHeaders.java
?? TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/DistributedSecurityHttpTest.java
?? TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/SecurityTestKeys.java
?? docs/entrega2/autorizacion-propiedad.md
?? docs/entrega2/seguridad-distribuida.md
```
