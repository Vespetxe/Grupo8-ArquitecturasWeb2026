# Correlación y registro HTTP — piloto Gateway / Inventario (incremento 40)

Alcance: Gateway e Inventario. Los cambios de Compras del incremento 39 se conservan
sin alteración. No se modifica Identidad, otros servicios, Compose, POM ni versiones.

## Arquitectura

- Gateway instala `CorrelationWebFilter` antes de Spring Security. El estado se
  crea por solicitud y se propaga mediante Reactor Context; no depende de MDC ni
  de un ThreadLocal. El registro usa campos explícitos de ese estado.
- `GatewayActorCaptureFilter` se ejecuta dentro de la cadena de seguridad después
  de AUTHENTICATION. Sólo captura un `JwtAuthenticationToken` ya validado.
- Inventario instala un filtro Servlet externo mediante `FilterRegistrationBean`
  para REQUEST, ASYNC y ERROR. Su estado vive en un atributo privado de solicitud.
  `InventoryActorCaptureFilter` captura el actor después del filtro Bearer, antes
  de que Spring Security limpie el contexto al terminar la cadena.
- `HttpFailureObserver` observa timeouts/desconexiones resueltos por MVC y retorna
  null: no sustituye los handlers ni modifica sus códigos o cuerpos.
- La finalización es atómica y admite un único registro por solicitud, incluidos
  callbacks asíncronos y redispatch. No se mantiene un mapa estático de solicitudes.

## Encabezado y confianza

Gateway genera siempre un UUID v4 nuevo, incluso cuando el cliente proporciona
un UUID válido. Sustituye `X-Correlation-Id` en el request encaminado y en la
respuesta; un upstream no puede reemplazar el identificador de respuesta.

Inventario conserva un único valor canónico válido recibido. La política exige
36 caracteres ASCII, minúsculas, versión 4 y variante RFC correspondiente.
Valores ausentes, duplicados, largos, de otra versión o no canónicos se sustituyen
por un UUID nuevo, sin alterar el código HTTP de la operación.

La correlación recibida en Inventario es metadato, no una atestación criptográfica
de que el emisor sea Gateway. No autentica, no otorga roles ni acredita propiedad.
Alguien con acceso a la red interna puede repetir un ID válido; no se utiliza para
deduplicación, claves de idempotencia ni decisiones de negocio.

Sólo se verifica en este piloto el reenvío de Gateway y la recepción de Inventario.
Los clientes de Eventos, Compras, Entradas e Identidad no se modifican para propagar
correlación en sus futuras llamadas. No se declara trazabilidad comercial completa.

## Contrato JSON

Se utiliza ECS nativo de Spring Boot 4.1.2 hacia stdout, con lista de campos
permitidos en `logging.properties`, importada también por los tests de Inventario.

| Campo | Significado |
|---|---|
| `@timestamp` | Instante UTC del registro |
| `service.name` | Servicio emisor (objeto ECS anidado) |
| `log.level`, `log.logger` | Nivel y logger (objeto ECS anidado) |
| `message` | Texto fijo `HTTP request completed` |
| `eventType` | `HTTP_REQUEST` |
| `correlationId` | UUID de esta cadena HTTP |
| `httpMethod` | Método estándar; valores desconocidos se agrupan como OTHER |
| `httpRoute` | Plantilla conocida o `/unmapped`; nunca ruta arbitraria ni query |
| `httpStatus` | Código observado; 0 exclusivamente cuando se cancela sin respuesta |
| `durationMs` | Duración no negativa medida con `System.nanoTime` |
| `actorType` | ANONYMOUS, USER o SERVICE |
| `actorId` | Sólo si hay identidad JWT validada; sub numérico o nombre técnico restringido |
| `errorCategory` | Categoría enumerada, sin texto de excepción |
| `completion` | COMPLETED, ERROR o CANCELLED |

COMPLETED describe el final del procesamiento HTTP, también para respuestas 4xx
resueltas normalmente. No declara éxito comercial ni commit de una transacción.
Si se envió un 200 antes de un fallo/cancelación, se conserva ese código y se marca
ERROR/CANCELLED. Nunca se inventa una respuesta 499. Los rechazos previos al ingreso
en la aplicación (por ejemplo, un parser HTTP que rechaza la conexión) quedan fuera
de estos filtros.

En Gateway, el registro de un error no comprometido espera a que el manejador
externo determine el código real. Para ese caso la duración se toma al confirmar
las cabeceras de error; para la terminación normal/cancelación se toma al finalizar
la cadena reactiva. No se presenta como medición del tiempo de recepción del cuerpo
por el cliente. Una cancelación posterior al registro de un error ya confirmado no
produce un segundo registro; la observación conserva ese primer resultado terminal.

No se registran Authorization, cookies, JWT, roles declarados por encabezados,
cuerpos, queries, mensajes de excepción ni stack traces. El error genérico de
Inventario conserva su HTTP 500 y registra un texto fijo; su registro HTTP asociado
contiene la correlación y el actor. Los errores resueltos conservan sus contratos.

## Dependencias y límites

No se agregan dependencias. Reactor Context, Spring Security, Servlet, SLF4J y ECS
ya están disponibles en los proyectos. Actuator/Micrometer Tracing se evaluaron,
pero no son necesarios para este primer registro de solicitudes. No se generan
traceId, spanId, operationId ni auditoría comercial y no se instala Fluent Bit.

La lista de rutas se mantiene explícitamente para evitar registrar texto libre de
una URL. Nuevas rutas requieren ampliar la tabla y sus pruebas. Las clases comunes
de normalización/correlación se mantienen iguales, salvo el paquete, entre los dos
proyectos Maven independientes. La instrumentación de otros servicios queda para
un incremento autorizado posterior.

## Validación ejecutada

- Gateway: 15 JUnit aprobados; Inventario: 51 JUnit aprobados, sin errores ni omitidos.
- 30 pruebas Node de aislamiento y sincronización de políticas aprobadas.
- JWT reales firmados en los tests HTTP; no se desactiva seguridad ni se agregan
  rutas de producción para facilitar pruebas.
- Gateway comprueba que el upstream HTTP de prueba recibe su UUID y que una
  respuesta upstream no puede falsificarlo. Inventario prueba su recepción real
  en el servidor Servlet. No se ejecutó un despliegue integrado de ambas imágenes.
- 24 solicitudes concurrentes por servicio, mezclando usuarios y anónimos, con
  comprobación de identidad y correlación independiente.
- Respuestas 200, 400, 401, 403, 404, 409, 500 y 503; timeout real de Gateway con 504;
  cancelación antes/después de commit y ciclo asíncrono Servlet.
- Inspección final de 160 registros JSON de Gateway (64 HTTP) y 203 de Inventario
  (67 HTTP): esquema válido y sin secretos señuelo ni JWT en los registros.

Las suites se ejecutan mediante el runner Maven existente, en contenedores
efímeros con H2 para Inventario, sin publicar puertos en el host. Se conservan
`target/security-tests.log` y `target/surefire-reports/` ignorados por Git.

La validación corrigió la clasificación de timeouts para respetar el estado 504
determinado por el framework, y agregó un caso de fallo asíncrono después de enviar
cabeceras. No se cambió una respuesta para hacer pasar las pruebas.

## Cierre y alcance de archivos

Se verificaron por SHA-256 los 12 archivos pendientes de Compras del incremento 39: sin alteraciones. Los 49 contenedores preexistentes y los metadatos del volumen MySQL persistente permanecieron idénticos a la línea base. Los seis contenedores persistentes estaban detenidos y no se iniciaron; no se renovaron hashes de sus datos ni se ejecutaron migraciones. No se crearon commits ni pushes.

El piloto comprende estas 27 rutas (los archivos pendientes de Compras son adicionales y se conservan):

- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/security/GatewaySecurity.java`.
- `TicketYa/api-gateway/src/main/resources/application.properties`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/exceptions/GlobalExceptionHandler.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/security/DistributedSecurity.java`.
- `TicketYa/inventarioReserva_service/src/main/resources/application.properties`.
- `TicketYa/inventarioReserva_service/src/test/resources/application.properties`.
- `TicketYa/api-gateway/OBSERVABILITY.md`.
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/observability/CorrelationIds.java`.
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/observability/CorrelationWebFilter.java`.
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/observability/GatewayActorCaptureFilter.java`.
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/observability/HttpLogState.java`.
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/observability/HttpRoutes.java`.
- `TicketYa/api-gateway/src/main/resources/logging.properties`.
- `TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/observability/HttpLoggingTest.java`.
- `TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/observability/ReactiveLifecycleTest.java`.
- `TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/observability/RequestLogCapture.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/CorrelationIds.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/HttpFailureObserver.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/HttpLogConfiguration.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/HttpLogState.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/HttpLoggingFilter.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/HttpRoutes.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/InventoryActorCaptureFilter.java`.
- `TicketYa/inventarioReserva_service/src/main/resources/logging.properties`.
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/observability/HttpLoggingTest.java`.
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/observability/RequestLogCapture.java`.
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/observability/ServletLifecycleTest.java`.
