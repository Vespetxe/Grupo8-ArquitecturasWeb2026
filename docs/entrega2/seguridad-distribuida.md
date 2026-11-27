> Estado vigente de la etapa 36: ver [autorización por propiedad](autorizacion-propiedad.md). Las notas de cierre de rutas y regresión pendiente de la etapa 35 se conservan como registro histórico.

# Seguridad distribuida — Implementación 35

Estado de alcance: validadores y transporte técnico implementados en los cinco
proyectos anteriores. No se incorpora todavía autorización por propiedad de
recursos. No se migró ni redesplegó el entorno persistente; sus contenedores siguen
usando las imágenes anteriores. La validación de este incremento se realiza en
un proyecto nuevo y con cinco bases aisladas.

## Fronteras de confianza

Gateway usa Spring Security reactivo y Resource Server; los cuatro servicios usan
la cadena servlet correspondiente. Cada instancia valida RS256 con un PEM público
local (RSA mínimo 3072), issuer exacto, audiencia propia `ticketya-<servicio>`, kid,
exp/nbf/iat, jti y token_use. No hay descarga de claves ni consulta a Identidad por
solicitud. Se admite tolerancia de reloj de 30 segundos, no ausencia de fechas.

Humanos: sub positivo representable como Long y roles conocidos, convertidos
explícitamente a ROLE_CLIENTE/ORGANIZADOR/ADMINISTRADOR/PERSONAL_ACCESO. Técnicos:
audiencia única, sub `service:<caller>-to-<destino>`, sin roles humanos y scopes
convertidos a SCOPE_*. El Gateway rechaza tokens técnicos; éstos se usan solamente
dentro de la red. La autorización local exige tanto caller exacto como scope.

`JwtTrust` y `RoutePolicy` mantienen una base común sincronizada y extensiones locales acotadas por servicio,
comprobadas por un test Node. Esto permite Maven/Docker independientes, sin
artefactos compartidos previamente instalados. Cambiar una política requiere
actualizar sus copias y pasar la guarda; una librería compartida publicada puede
evaluarse más adelante sin cambiar ahora los contextos de construcción.

No se confía en X-User-Role, X-Admin-Id, X-User-Id ni X-Delegated-Authorization.
Gateway los elimina antes de reenviar. Los backends también los descartan aunque
se acceda directamente. Para compatibilidad con el workflow de Eventos, únicamente
un principal humano ADMINISTRADOR validado produce los encabezados internos
derivados `ADMIN` e ID del sub; nunca se reutiliza el valor recibido del cliente.
Compras ya no exige X-Admin-Id como autoridad. La coordinación de cancelación
Eventos→Compras usa exclusivamente su identidad técnica, sin credencial humana.

## Matriz efectiva de acceso

Toda ruta/método no indicado queda cerrado (401 sin autenticación; 403 con JWT
válido sin permiso). Los endpoints de prueba permanecen cerrados. La política vigente restringe OpenAPI/Swagger a ADMINISTRADOR humano; Eventos usa /api-docs, incluido /api-docs/swagger-config. No se agregan esas rutas como API pública al Gateway ni se abrió permitAll general para tests.

| Servicio | Método y ruta | Permiso |
| --- | --- | --- |
| Gateway/Identidad | POST /auth/register, POST /auth/login | Público; registro sólo CLIENTE en Identidad |
| Gateway/Identidad | GET /auth/me | JWT humano válido; Identidad verifica cuenta activa |
| Gateway/Eventos | GET /api/v1/events/search | Público; EventSpecificationBuilder restringe a PUBLICADO/AGOTADO |
| Gateway/Eventos | GET /api/v1/venues, /venues/{id}, /venues/{id}/sectors; GET /api/v1/participants, /participants/{id}; GET /api/v1/sectors/{id}/seats | Público |
| Gateway/Inventario | GET /disponibilidad/eventos/{id}/sectores/{id} | Público |
| Gateway/Eventos | POST/PUT/DELETE /api/v1/participants[/{id}] | ADMINISTRADOR |
| Gateway/Eventos | PATCH /api/v1/events/{id}/cancel, /suspend, /status | ADMINISTRADOR |
| Eventos | GET /api/v1/events/{id} | compras + events.read; acceso humano completo pendiente del tratamiento de borradores/propiedad |
| Inventario | POST /eventos/{id}/inventario | eventos + inventory.prepare |
| Inventario | POST /internal/eventos/{id}/publicar, /habilitar, /suspender; POST /eventos/{id}/cancelar-inventario | eventos + inventory.lifecycle |
| Inventario | GET /reservas/{id} | compras + reservations.read |
| Inventario | POST /reservas/{id}/confirmar, /liberar | compras + reservations.confirm / reservations.release |
| Inventario | POST /internal/reservas/{id}/compensar | compras + reservations.compensate |
| Inventario | GET/POST /internal/reservas/{id}/devoluciones; POST /ocupaciones/liberar | compras + reservations.return |
| Entradas | GET /tickets | compras + tickets.read |
| Entradas | POST /internal/tickets/issue | compras + tickets.issue |
| Entradas | POST /internal/tickets/orders/{id}/annul | compras + tickets.annul |
| Entradas | PATCH /internal/tickets/{id}/status, /internal/tickets/status | compras + tickets.status |
| Entradas | POST /internal/events/{id}/cancel-emission | eventos + tickets.event-block |
| Compras | GET /internal/orders/{id} | entradas + orders.read |
| Compras | POST /admin/eventos/{id}/cancelacion | eventos + purchases.event-cancel; nunca publicado por Gateway |
| Gateway/Compras | GET /admin/devoluciones; POST /admin/devoluciones/{id}/aprobar, /rechazar, /procesar; GET /admin/devoluciones/{id}/procesamiento; GET /admin/reintegros/{id} | ADMINISTRADOR |
| Cuatro backends | GET /internal/security/readiness | Sólo los callers de su grafo, con su scope de preparación/lectura/coordinación; no lee datos ni invoca dependencias |

Los patrones de venues/participants de la tabla están bajo `/api/v1`. Las rutas
internas no dependen de ocultamiento: incluso accesibles desde la red requieren
validación y autorización local. Gateway excluye `/internal/**`, `/oauth2/**`,
provisionamiento y coordinación `/admin/eventos/**`.

Permanecen cerrados a humanos, también ADMINISTRADOR: crear lugar/sector/evento,
asignar precios/publicar, detalle de evento, crear/consultar reservas, compras,
pagos, solicitudes de devolución y consulta de entradas. ORGANIZADOR y
PERSONAL_ACCESO no reciben permisos de escritura adicionales en esta etapa.
Esto evita aceptar IDs declarados antes de verificar propiedad. No se interpreta
un 403 de estas rutas como regresión comercial aprobada ni como prueba de negocio.

## Tokens técnicos y fallos

Siete registros distintos en Identidad, uno por enlace ya utilizado:

- eventos-to-inventario: inventory.prepare, inventory.lifecycle.
- eventos-to-entradas: tickets.event-block.
- eventos-to-compras: purchases.event-cancel.
- compras-to-eventos: events.read.
- compras-to-inventario: reservations.read/confirm/release/compensate/return.
- compras-to-entradas: tickets.read/issue/annul/status.
- entradas-to-compras: orders.read.

Cada emisor usa `AuthorizedClientServiceOAuth2AuthorizedClientManager` oficial y
client_credentials/client_secret_basic. Credenciales distintas por destino,
mínimo 32 caracteres, sin valores por defecto utilizables. Token endpoint y
recursos tienen connect timeout 2 s/read timeout 10 s. El provider cachea tokens
hasta su renovación (30 s antes del vencimiento). No hay dependencia de request
humano ni ciclos de autenticación: sólo se solicita token a Identidad al necesitarlo.
Los servicios pueden validar JWT existentes con Identidad temporalmente caída.
Los scopes se solicitan explícitamente desde el registro del cliente. En la
versión utilizada, omitir scope produce un token sin permisos; no se confía en
que el servidor conceda automáticamente todos los scopes registrados.

Un 401/403 remoto elimina la autorización cacheada y produce TechnicalAuthFailure;
la operación comercial NO se repite automáticamente. La siguiente recuperación
explícita puede obtener un token nuevo conservando la misma operación/idempotencia.
Los errores del token endpoint también son técnicos, con mensajes redactados.
No se imprimen tokens, secretos ni respuestas OAuth2.

`RemoteCallSupport.mutate` distingue explícitamente 401/403 de otros 4xx: nunca crea
OperacionExternaRechazadaException por autenticación técnica. PagoService mantiene
sus estados/etapas y no inicia compensación por TechnicalAuthFailure. Una prueba
verifica pago aprobado pendiente de confirmación, recuperación con la misma clave,
un solo cargo y ningún reintegro. Publicación también clasifica 401/403 como 503,
no incompatibilidad comercial. El cliente de órdenes mantiene esos errores como
indisponibilidad, sin convertirlos en orden inexistente/no confirmada.

## Configuración y despliegue

Variables comunes: TY_SECURITY_ISSUER, TY_SECURITY_KEY_ID,
TY_SECURITY_PUBLIC_KEY (archivo PEM local). Clientes: TY_SECURITY_TOKEN_URL y
TY_SECURITY_<DESTINO>_SECRET. Compose obtiene los siete secretos de
TY_SECURITY_<EMISOR>_<DESTINO>_SECRET; `.env.example` contiene sólo nombres vacíos.
Gateway usa IDENTIDAD_BASE_URL para las tres rutas /auth.

Los cinco resource servers montan **sólo public.pem**, de lectura. Nunca reciben
la clave privada de Identidad. No se alteraron MySQL, inicializadores, red o volumen
del Compose persistente. No se añadieron dependencias circulares de arranque.

No ejecutar un up global del entorno persistente con estas imágenes sin la
preparación y autorización pendientes: migración de Identidad, claves externas,
séptuple provisionamiento técnico y tratamiento de IDs históricos/propiedad.
El `.env` existente y sus cinco credenciales no se modificaron. No se agregaron
cuentas de Identidad a las cuatro bases de las validaciones anteriores.

## Ejecución reproducible y alcance de regresión

```powershell
node TicketYa/integration-tests/e2/test-security.cjs
node --test TicketYa/integration-tests/e2/isolation.test.cjs TicketYa/integration-tests/e2/security-isolation.test.cjs
node TicketYa/integration-tests/e2/build-images.cjs
node TicketYa/integration-tests/e2/run-regression.cjs --security
```

El comando sin --security falla antes de crear recursos: no inicia los escenarios
anónimos de Entrega 1. Su código se conserva para adaptación en la etapa de
propiedad. --security admite únicamente --port 48080..48089 (default 48082), no
URL, proyecto, esquema, credenciales ni overrides externos.

El runner crea un proyecto aleatorio, red y volumen nuevos, cuatro esquemas
ty_e2_reg_<id>_* y ty_e2_identity_<id>, claves RSA propias, cinco contraseñas de base,
root y siete secretos técnicos independientes. Usa --env-file NUL y elimina
COMPOSE_* del proceso hijo. Provisiona administrador y clientes mediante el CLI
real; registra un CLIENTE e inicia sesión a través del Gateway. No desactiva
Spring Security ni reemplaza JwtDecoder en las pruebas de autorización.

Atestación previa a operaciones: Compose canónico, imagen efectiva, proyecto,
red/volumen propios, usuario 10001, configuración JDBC, ausencia de overrides,
montaje exacto, sólo Gateway publicado en loopback y siete contenedores exactos.
Helper HTTP dentro de la red (sin puertos extra), log-driver none y salidas
capturadas; reporte sin secretos bajo target/security/<id>/report.json, ignorado.
Sólo el emisor y el helper de tokens inválidos reciben la clave privada de esa
prueba; jamás un resource server ni un contenedor previo.

Pruebas: sin JWT, firma/formato inválidos, expiración, issuer/audiencia/nbf erróneos,
tipos cruzados, scope insuficiente, caller incorrecto, headers falsificados,
las siete relaciones autorizadas y auth/register/login/me a través del Gateway.
Los tokens positivos proceden de Identidad; los negativos firmados usan sólo la
clave efímera de la corrida. El probe interno verifica transporte, no una compra.

JUnit conserva servicios/repositorios y pruebas de negocio. Compras usa H2 en
memoria en su classpath de test, con guarda exacta de URL; no se conecta al MySQL
histórico 33079. Las suites HTTP de reserva (6), compras (13) y devolución (7)
están explícitamente omitidas hasta autorización de propiedad: 26 casos pendientes,
no aprobados. Las pruebas HTTP internas de inventario y emisión se adaptaron para
usar JWT firmado; no se omiten por requerir credenciales técnicas.

No hay limpieza global ni down. Contenedores/volúmenes nuevos se conservan para
auditoría; sólo herramientas temporales propias usan --rm. Si falla, se detiene
la secuencia sin reparar ni reutilizar un volumen. No se prueba el flujo financiero
completo por HTTP hasta completar propiedad; sí se ejecutan sus pruebas de servicio.

Pendientes de la etapa siguiente: dueño de lugar/evento, identidad de cliente
derivada de sub, autorización de reserva/compra/pago/devolución/entradas, lectura de
borradores, separación ownership de orden frente a elegibilidad de emisión,
adaptar IDs de escenarios a usuarios reales y reactivar la regresión comercial.
Mantener como decisiones explícitas revocación, rotación de varias claves,
rate limiting, TLS y actualización completa de OpenAPI/Postman de negocio.

Referencias: [Spring Security Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html),
[OAuth2AuthorizedClientManager](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/core.html).

## Resultados ejecutados

| Proyecto | Casos JUnit | Ejecutados sin fallos | Omitidos por propiedad |
| --- | ---: | ---: | ---: |
| Gateway | 6 | 6 | 0 |
| Eventos | 38 | 38 | 0 |
| Inventario | 41 | 35 | 6 |
| Entradas | 34 | 34 | 0 |
| Compras | 295 | 275 | 20 |
| Total | 414 | 388 | 26 |

Todos los Maven package finalizaron con BUILD SUCCESS. Se ejecutaron además 30
pruebas Node de aislamiento/sincronización y las seis construcciones Docker.
La corrida `ticketya-e2-reg-c83f8cc33afde676` aprobó 82 comprobaciones HTTP con
tokens reales y cinco esquemas propios. Se añadieron y ejecutaron diez GET
negativos de firma manipulada/alg none sobre sus cinco validadores, todos 401;
el reporte local los identifica por separado como signatureChecks. El runner
normal incorpora también esas diez comprobaciones para futuras corridas.

La primera corrida `ticketya-e2-reg-d6e455b9dce5db29` se detuvo ante el 403 de un
token solicitado sin scope. Se diagnosticó sin modificar negocio: sin scope 403,
con events.read 404 de recurso inexistente. Se corrigieron clientes y helper, se
reconstruyó y se repitió en otro proyecto. Sus siete contenedores se detuvieron
con Compose stop --timeout 60; MySQL terminó con código 0. Se conservaron volumen,
red y contenedores. La corrida aprobada permanece running, MySQL healthy.

Incidencias de pruebas corregidas: ubicación de dependencias del Gateway,
tipo reactivo AuthorizationResult de Security 7, extracción de ruta compatible
con Servlet/MockMvc, prioridad del token endpoint simulado de Entradas y perfil
test de Compras que heredaba la URL histórica. Esa conexión fallida era al
loopback del contenedor Maven, no a una base histórica. Después se ejecutaron
todos los tests con H2 y sus guardas. No se alteraron versiones del stack.

Limitaciones de evidencia: las siete relaciones técnicas se probaron contra
validadores reales mediante el probe interno y JWT emitidos por Identidad;
no equivalen a un circuito financiero completo por HTTP. El manager OAuth2 se
probó por HTTP con servidor controlado, renovación de reloj y ausencia de replay
de POST; su implementación común está comprobada entre los tres emisores.

Al finalizar se verificó que los seis persistentes seguían running con cero
reinicios y MySQL healthy. Los hashes de datos de sus cuatro esquemas coinciden
con los de las implementaciones anteriores. Sin migraciones persistentes,
sin sustitución de contenedores históricos, sin commits ni pushes.
Los avisos de deprecación de messageConverters y LF/CRLF no impidieron la
compilación; revisar esa API antes de futuras actualizaciones de Spring.

## Inventario de archivos de esta implementación

37 archivos versionados modificados y 54 archivos nuevos; sin staging. Rutas relativas a la raíz del repositorio:

- `.env.example`
- `README.md`
- `TicketYa/EventoCatalogo_Service/pom.xml`
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/DistributedSecurity.java`
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/JwtTrust.java`
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/RoutePolicy.java`
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/SecurityReadiness.java`
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/ServiceTokens.java`
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/TechnicalAuthErrors.java`
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/TechnicalAuthFailure.java`
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/security/TrustedActorHeaders.java`
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/service/EventWorkflow.java`
- `TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/DistributedSecurityHttpTest.java`
- `TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/PublicacionInterrumpidaTest.java`
- `TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/PublicacionRecuperacionTest.java`
- `TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/SecurityTestKeys.java`
- `TicketYa/EventoCatalogo_Service/src/test/resources/META-INF/spring.factories`
- `TicketYa/api-gateway/pom.xml`
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/GatewayApplication.java`
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/security/GatewaySecurity.java`
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/security/JwtTrust.java`
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/security/RoutePolicy.java`
- `TicketYa/api-gateway/src/main/resources/application.properties`
- `TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/DistributedSecurityHttpTest.java`
- `TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/GatewayRoutingTest.java`
- `TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/GatewayUnavailableTest.java`
- `TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/SecurityTestKeys.java`
- `TicketYa/api-gateway/src/test/resources/META-INF/spring.factories`
- `TicketYa/comprapago_service/pom.xml`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/client/FeignEntradaAdapter.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/client/FeignEventoAdapter.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/client/FeignReservaAdapter.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/client/RemoteCallSupport.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/controller/DevolucionAdminController.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/DistributedSecurity.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/JwtTrust.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/RoutePolicy.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/SecurityReadiness.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/ServiceTokens.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/TechnicalAuthErrors.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/TechnicalAuthFailure.java`
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/security/TrustedActorHeaders.java`
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/DistributedSecurityHttpTest.java`
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/EntornoPruebasSeguro.java`
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/SecurityTestKeys.java`
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/client/RemoteCallSupportSecurityTest.java`
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/controller/ApiIntegracionTest.java`
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/controller/SeleccionEntradaYActorTest.java`
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/security/ServiceTokensTest.java`
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/service/DevolucionHttpIntegracionTest.java`
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/service/PagoServiceTest.java`
- `TicketYa/comprapago_service/src/test/resources/META-INF/spring.factories`
- `TicketYa/comprapago_service/src/test/resources/application-test.properties`
- `TicketYa/comprapago_service/src/test/resources/application.properties`
- `TicketYa/entradas_service/pom.xml`
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/config/OAuth2RestClientConfiguration.java`
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/DistributedSecurity.java`
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/JwtTrust.java`
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/RoutePolicy.java`
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/SecurityReadiness.java`
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/ServiceTokens.java`
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/TechnicalAuthErrors.java`
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/TechnicalAuthFailure.java`
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/security/TrustedActorHeaders.java`
- `TicketYa/entradas_service/src/test/java/ar/edu/tudai/ticketya/servicio_entradas/DistributedSecurityHttpTest.java`
- `TicketYa/entradas_service/src/test/java/ar/edu/tudai/ticketya/servicio_entradas/SecurityTestKeys.java`
- `TicketYa/entradas_service/src/test/java/ar/edu/tudai/ticketya/servicio_entradas/TicketIssuanceIntegrationTest.java`
- `TicketYa/entradas_service/src/test/resources/META-INF/spring.factories`
- `TicketYa/integration-tests/e2/build-images.cjs`
- `TicketYa/integration-tests/e2/run-regression.cjs`
- `TicketYa/integration-tests/e2/run-security.cjs`
- `TicketYa/integration-tests/e2/security-isolation.cjs`
- `TicketYa/integration-tests/e2/security-isolation.test.cjs`
- `TicketYa/integration-tests/e2/security-smoke.cjs`
- `TicketYa/integration-tests/e2/test-security.cjs`
- `TicketYa/inventarioReserva_service/pom.xml`
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/DistributedSecurity.java`
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/JwtTrust.java`
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/RoutePolicy.java`
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/SecurityReadiness.java`
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/TechnicalAuthErrors.java`
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/TechnicalAuthFailure.java`
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/TrustedActorHeaders.java`
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/DistributedSecurityHttpTest.java`
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/SecurityTestKeys.java`
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/controllers/InventarioControllerTest.java`
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/controllers/ReservaControllerTest.java`
- `TicketYa/inventarioReserva_service/src/test/resources/META-INF/spring.factories`
- `docker-compose.yml`
- `docs/entrega2/docker-regresion.md`
- `docs/entrega2/seguridad-distribuida.md`
