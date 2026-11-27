# Implementación 41: propagación y trazabilidad

Este incremento extiende el logging seguro de Compras (39) y los límites HTTP de Gateway e Inventario (40). No agrega un repositorio de auditoría comercial ni un backend de trazas.

## Arquitectura y contrato

Los seis proyectos incluyen `spring-boot-starter-actuator`, `spring-boot-micrometer-tracing-opentelemetry` y `micrometer-tracing-bridge-otel`, sin versiones explícitas: las administra Spring Boot 4.1.2. Los tres emisores de llamadas técnicas incorporan `spring-boot-starter-restclient`. Las pruebas de propagación utilizan `spring-boot-micrometer-tracing-test` y `@AutoConfigureTracing`. Se conservan los niveles Java de cada POM y Spring Cloud existente.

La instrumentación HTTP de Spring/Micrometer crea spans de servidor y cliente. OpenTelemetry valida e inyecta W3C `traceparent`. No se construyen encabezados de trazas a mano en producción. No hay exportadores OTLP/Zipkin ni servicios externos. Se configura propagación W3C, muestreo 1.0 para este entorno académico y baggage deshabilitado. Los endpoints HTTP y JMX de Actuator están excluidos; las políticas JWT siguen denegando las rutas administrativas no autorizadas.

ECS a stdout conserva la lista explícita de campos seguros. Los registros HTTP agregan `trace.id` y `span.id`; los campos MDC `traceId` y `spanId` también pueden aparecer cuando el SDK tiene un scope activo. Se capturan los identificadores del span de servidor al entrar al filtro para conservarlos aunque el scope ya haya cerrado al terminar una solicitud reactiva o asíncrona. El estado no guarda JWT, excepciones, cuerpos, consultas ni credenciales. No se registran atributos completos de spans.

Gateway utiliza Reactor Context y propagación automática de contexto de Reactor. Los Servlet utilizan estado por solicitud y scopes administrados por Spring. La captura del actor se mantiene después de autenticar y antes de autorizar. En Identidad se captura también el cliente OAuth2 autenticado antes del filtro anónimo; no se registran sus credenciales.

La duración HTTP y la resolución de errores/cancelaciones conservan el comportamiento documentado en `TicketYa/api-gateway/OBSERVABILITY.md`. Un estado cero representa cancelación antes de confirmar una respuesta, no un código HTTP enviado al cliente.

## Correlation ID y límites de confianza

- Gateway genera un UUID v4 nuevo por solicitud, sustituye los valores externos y protege `X-Correlation-Id` en la respuesta frente a sustituciones del upstream.
- Inventario conserva el contrato de 40: admite únicamente un UUID v4 canónico como metadato; una solicitud anónima válida puede conservarlo. Esto no acredita que provenga del Gateway.
- Eventos, Compras, Entradas e Identidad generan inicialmente un UUID propio. Después de autenticar un JWT o cliente OAuth2 válido, conservan el UUID recibido si es único y canónico.
- Una solicitud pública anónima al catálogo, registro o login puede tener un correlation ID distinto en Gateway y en el receptor: todavía no existe autenticación del canal Gateway-servidor. La traza W3C permite seguir ese salto. No se agregan enlaces técnicos ni se concede confianza de red implícita para ocultar esta diferencia.
- Los clientes REST copian exclusivamente el correlation ID del estado de la solicitud Servlet actual. No copian actores, cookies, claves de idempotencia ni bearer tokens del usuario.

Los identificadores de observabilidad no autorizan, no identifican una operación financiera estable y no reemplazan claves de idempotencia. Un `traceparent` externo válido puede continuar una traza según W3C, siempre como metadato no confiable para autorización.

## Clientes técnicos y tareas programadas

Los tres `ServiceTokens` reciben el builder administrado por Boot y clonan su configuración para cada cliente de negocio y para `/oauth2/token`. Se conservan los siete enlaces, credenciales independientes, scopes, caché en memoria, renovación anticipada, clasificación `TechnicalAuthFailure` y ausencia de replay automático de POST. Un 401/403 invalida el token cacheado y falla la operación actual: la próxima invocación puede obtener otro token, sin repetir automáticamente la escritura fallida.

Los timeouts iniciales fueron sustituidos por [presupuestos del Bloque 4](timeouts-presupuestos.md): conexión/TLS 1 s, GET 2 s, escritura 4 s, token 3 s y adquisición 500 ms por defecto. Entradas conserva su transporte instrumentado de consulta de órdenes. Los clientes agregan correlación desde `RequestContextHolder`, cuyo ciclo de vida controla Spring. No existe un ThreadLocal propio ni un mapa global de correlaciones.

`ReservationExpirationTask` recibe el temporizador que antes anotaba a `ReservaService`. Invoca el mismo método transaccional, con el mismo intervalo, sin cambiar TTL ni lógica de liberación. Cada ejecución inicia un span raíz con `setNoParent()`, lo cierra en `finally` y restaura el scope anterior. Registra únicamente tipo de tarea, resultado controlado y traza. No crea un correlation ID HTTP ficticio.

No se agregan IDs de usuarios, compras o reservas a etiquetas de métricas. Los campos de actor aparecen solamente en logs. Las métricas estándar conservan sus convenciones y plantillas; Gateway no habilita etiquetas de rutas concretas.

## Validación reproducible y aislamiento

Desde la raíz, las suites pueden ejecutarse con:

```powershell
node TicketYa/integration-tests/e2/test-security.cjs
node --test TicketYa/integration-tests/e2/isolation.test.cjs TicketYa/integration-tests/e2/security-isolation.test.cjs TicketYa/integration-tests/e2/verify-tracing.test.cjs
```

JUnit se ejecuta en Maven/JDK 21 efímero, sin importar el `.env` del host ni publicar puertos. La persistencia de esas suites utiliza H2 de prueba. `create-drop` existente queda limitado a H2; no se invoca sobre MySQL persistente.

La integración completa requiere construir primero las seis imágenes actuales:

```powershell
node TicketYa/integration-tests/e2/build-images.cjs
node TicketYa/integration-tests/e2/run-security.cjs --observability --port 48084
```

El runner elimina variables `COMPOSE_*` heredadas, usa `--env-file NUL` en Windows y rechaza puertos/esquemas/topologías persistentes. Genera proyecto, red, volumen, credenciales y claves exclusivos por corrida; provisiona solamente su MySQL aislado. Antes de cada acceso acredita etiquetas, imagen, mounts, estado, red, puertos y JDBC efectivos. No elimina volúmenes ni modifica recursos previos. Los reportes, claves y archivos de provisionamiento permanecen en `integration-tests/target/security`, ignorado por Git y protegido con ACL en Windows. No versionar ni compartir ese directorio.

El recorrido utiliza registro/login y JWT reales, los siete enlaces OAuth2, publicación, reserva, compra, pago, emisión, devolución parcial, cancelación, rechazo y recuperación de pago incierto. Agrega 24 consultas concurrentes con tres identidades. Las comprobaciones SQL posteriores son de lectura sobre los esquemas de esa corrida.

`verify-tracing.cjs` inspecciona logs en memoria y compara el correlation ID de cada respuesta con el registro del Gateway y sus receptores. Exige un trace ID común, spans distintos en cada salto y trazas diferentes entre operaciones públicas independientes. Exige evidencia de OAuth2 y scheduler independiente, y ausencia de credenciales conocidas o JWT en todos los logs inspeccionados. No persiste logs completos en el reporte de trazabilidad.

Las suites prueban además 401/403 con JWT inválidos y actores reales, errores, timeouts, concurrencia, cierre de scopes, renovación de tokens y ausencia de replay comercial. La simulación financiera se verifica por estados persistidos y conteos, no solamente por códigos HTTP.

## Límites y operación futura

No se aplica este incremento a los contenedores persistentes. Las imágenes `:dev` se reconstruyen para el entorno aislado; los contenedores existentes mantienen su imagen y configuración originales.

Sin exportador no hay árbol de spans consultable ni almacenamiento de trazas: se reconstruyen operaciones desde los logs HTTP. La correlación entre tareas futuras, reanudaciones o reintentos debe usar IDs comerciales existentes por un diseño posterior, no reutilizar una traza HTTP indefinidamente. Antes de agregar un backend deberán revisarse la retención, muestreo y atributos de URL de alta cardinalidad de las convenciones estándar. El SDK no debe exportar información personal innecesaria.

Fuentes primarias: [Spring Boot 4.1: tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html), [pruebas de tracing](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/micrometer/tracing/test/autoconfigure/AutoConfigureTracing.html) y [observabilidad de Gateway](https://docs.spring.io/spring-cloud-gateway/reference/appendix.html).

## Evidencia de la ejecución del 14/11/2026

Las seis imágenes se construyeron correctamente. Las suites JUnit finalizaron con BUILD SUCCESS: Gateway 16, Eventos 40, Inventario 54, Entradas 36, Compras 306 e Identidad 16: **468 pruebas**, sin fallos, errores ni omisiones. Aprobaron también 30 guardas de aislamiento y 10 pruebas del verificador de trazas. La prueba de timeout de Compras realizó una espera HTTP real de diez segundos y comprobó una sola invocación.

Proyecto aislado: `ticketya-e2-reg-40d410132b8e9cef`; Gateway `127.0.0.1:48084`. Resultado PASS, siete componentes running, MySQL healthy, sin reinicios al acreditarlos. HTTP: 92 comprobaciones de seguridad y 192 solicitudes comerciales, incluidas 71 negativas. Las 24 consultas concurrentes utilizaron tres identidades y 24 correlaciones diferentes.

El verificador inspeccionó 586 registros HTTP y tres registros del scheduler; comprobó 171 solicitudes distribuidas. En los logs de Identidad se observaron 18 emisiones OAuth2 exitosas y los siete clientes técnicos originales. Los registros inspeccionados no contenían JWT ni las credenciales conocidas de la corrida.

Ejemplo real de pago y emisión: correlation ID `bd311fa7-8977-4849-8b63-6dede6545299`, trace ID `48415dfb4b7d23fd078c1b2907809d06`, componentes `gateway`, `identidad`, `inventario`, `compras`, `entradas`. Cada solicitud del recorrido tiene su propio span de servidor; el reporte conserva la evidencia por operación sin cuerpos ni tokens.

La validación financiera confirmó tres intentos de cobro: dos aprobados por 45000 en total y uno rechazado; dos reintegros distintos por 30000 en total (parcial y cancelación), tres tickets y dos lotes de emisión. Las repeticiones idempotentes y la recuperación de pago incierto conservaron esos conteos. No se observaron operaciones financieras duplicadas.

Se compararon los 49 contenedores originales (ID, estado, inicio, fin y reinicios) con la referencia anterior: sin cambios. El volumen `ticketya-e2-infra_mysql_data` conserva nombre, fecha y metadatos. Sus seis contenedores permanecen detenidos; no se consultó ni arrancó ese MySQL para comparar filas. El proyecto aislado permanece funcionando para auditoría; no se eliminaron recursos.

Los once archivos de 39 distintos de `logging.properties` mantienen su SHA-256 inicial. Ese archivo se amplió con trazas y configuración de observabilidad, conservando los campos seguros y supresión SQL anteriores. No se modificaron nuevamente los servicios financieros ni sus pruebas aprobadas de 39.

Estado Git acumulado 39–41: rama `entrega-2`, HEAD `c822345`, 39 archivos versionados modificados y 68 sin seguimiento; staging vacío, sin commits ni pushes. `git diff --stat` (solo versionados): 39 archivos, 166 inserciones y 33 eliminaciones. `git diff --check` sin errores. `.env`, claves y reportes bajo target siguen fuera de Git.

### Archivos alcanzados por 41

- `TicketYa/EventoCatalogo_Service/pom.xml`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/DistributedSecurity.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/ServiceTokens.java`.
- `TicketYa/EventoCatalogo_Service/src/main/resources/application.properties`.
- `TicketYa/EventoCatalogo_Service/src/test/resources/application.properties`.
- `TicketYa/api-gateway/pom.xml`.
- `TicketYa/comprapago_service/pom.xml`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/DistributedSecurity.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/ServiceTokens.java`.
- `TicketYa/entradas_service/pom.xml`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/config/OAuth2RestClientConfiguration.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/integration/FeignComprasOrderAdapter.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/DistributedSecurity.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/ServiceTokens.java`.
- `TicketYa/entradas_service/src/main/resources/application.properties`.
- `TicketYa/entradas_service/src/test/resources/application.properties`.
- `TicketYa/identidad_service/pom.xml`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/security/SecurityConfiguration.java`.
- `TicketYa/identidad_service/src/main/resources/application.properties`.
- `TicketYa/identidad_service/src/test/java/ar/edu/ticketya/identidad/IdentityHttpTest.java`.
- `TicketYa/integration-tests/e2/commercial-smoke.cjs`.
- `TicketYa/integration-tests/e2/run-security.cjs`.
- `TicketYa/integration-tests/e2/security-isolation.test.cjs`.
- `TicketYa/integration-tests/e2/test-security.cjs`.
- `TicketYa/inventarioReserva_service/pom.xml`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/services/ReservaService.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/CorrelationIds.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/CorrelationInterceptor.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/HttpFailureObserver.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/HttpLogConfiguration.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/HttpLogState.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/HttpLoggingFilter.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/HttpRoutes.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/ServletActorCaptureFilter.java`.
- `TicketYa/EventoCatalogo_Service/src/main/resources/logging.properties`.
- `TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/observability/TechnicalTracingTest.java`.
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/observability/CorrelationWebFilter.java`.
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/observability/HttpLogState.java`.
- `TicketYa/api-gateway/src/main/resources/logging.properties`.
- `TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/observability/HttpLoggingTest.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/CorrelationIds.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/CorrelationInterceptor.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/HttpFailureObserver.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/HttpLogConfiguration.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/HttpLogState.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/HttpLoggingFilter.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/HttpRoutes.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/ServletActorCaptureFilter.java`.
- `TicketYa/comprapago_service/src/main/resources/logging.properties`.
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/observability/TechnicalTracingTest.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/CorrelationIds.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/CorrelationInterceptor.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/HttpFailureObserver.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/HttpLogConfiguration.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/HttpLogState.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/HttpLoggingFilter.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/HttpRoutes.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/ServletActorCaptureFilter.java`.
- `TicketYa/entradas_service/src/main/resources/logging.properties`.
- `TicketYa/entradas_service/src/test/java/ar/edu/tudai/ticketya/servicio_entradas/observability/TechnicalTracingTest.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/observability/CorrelationIds.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/observability/HttpFailureObserver.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/observability/HttpLogConfiguration.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/observability/HttpLogState.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/observability/HttpLoggingFilter.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/observability/HttpRoutes.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/observability/ServletActorCaptureFilter.java`.
- `TicketYa/identidad_service/src/main/resources/logging.properties`.
- `TicketYa/integration-tests/e2/verify-tracing.cjs`.
- `TicketYa/integration-tests/e2/verify-tracing.test.cjs`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/HttpLogConfiguration.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/HttpLogState.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/HttpLoggingFilter.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/InventoryActorCaptureFilter.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/ReservationExpirationTask.java`.
- `TicketYa/inventarioReserva_service/src/main/resources/logging.properties`.
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/observability/HttpLoggingTest.java`.
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/observability/ScheduledTracingTest.java`.
- `docs/entrega2/trazabilidad.md`.
