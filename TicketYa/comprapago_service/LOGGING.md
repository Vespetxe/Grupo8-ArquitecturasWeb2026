# Logging operativo seguro de Compras — incremento 39

Se utiliza ECS nativo de Spring Boot 4.1.2 con Logback y SLF4J, hacia stdout.
`application.properties` importa `logging.properties`. Los tests importan el mismo
archivo, pero conservan su datasource H2 aislado y sus perfiles de seguridad.
No se incorporan dependencias ni encoders externos.

## Contrato inicial

Los nombres del contrato funcional corresponden a estos campos ECS:

| Concepto | Campo JSON |
|---|---|
| timestamp UTC | `@timestamp` |
| level | `log.level` |
| service | `service.name` (`compras-service`) |
| logger | `log.logger` |
| message | `message` |
| errorCode | `errorCode`, cuando hay un diagnóstico operativo de la aplicación |
| errorCategory | `errorCategory`, cuando hay un diagnóstico operativo de la aplicación |

`log.level`, `log.logger`, `service.name` y `ecs.version` designan rutas de
objetos JSON anidados, tal como los produce ECS nativo de Boot 4.1.2. La lista
de inclusión conserva también sus nodos padre; no son claves planas con puntos.

También se permiten `ecs.version`, `exceptionType`, `rootErrorCategory`,
`errorOrigin` y los identificadores numéricos `compraId`, `devolucionId`, `eventoId`
y la fase enumerada `etapa` cuando estén disponibles. No se infieren propietarios
ni se registran transiciones exitosas: estos registros siguen siendo avisos de
recuperación y errores internos, no una auditoría comercial.

`SafeLog` acepta únicamente excepciones, IDs y fases tipadas. Los códigos,
mensajes y categorías proceden de conjuntos definidos en el código. Nunca toma
el mensaje, el cuerpo HTTP ni la representación `toString()` de una excepción.
Los tipos desconocidos se agrupan como `Exception`/`RuntimeException`.
El diagnóstico conserva el código, categoría, categoría de causa (recorrido
acotado y con detección de ciclos) y primera ubicación del código de aplicación,
sin mensajes de causas ni stack traces completos. Los errores internos siguen
en nivel ERROR; la recuperación financiera permanece en WARN.

## Política de datos

La lista de campos JSON es explícita. Se excluyen campos MDC/SLF4J arbitrarios y
los mensajes/stack traces nativos de Throwable. No se registran JWT, Authorization,
cookies, contraseñas, client secrets, claves RSA, tarjetas, cuerpos HTTP, entidades,
DTO o claves de idempotencia. Los IDs persistidos permiten diagnosticar la
operación sin registrar la clave de idempotencia.

Se desactiva `show-sql`, el formato SQL y los canales Hibernate SQL/bind/extract.
Se silencia `SqlExceptionHelper`, que puede imprimir consultas y texto del servidor;
también se silencia el canal efectivo `org.hibernate.orm.jdbc.error` de Hibernate,
detectado emitiendo SQL en conflictos provocados por las pruebas.
Los fallos que alcanzan el manejador de aplicación conservan un diagnóstico
controlado, sin variar su código HTTP. Un conflicto de persistencia conserva su
respuesta 409 existente; el error interno genérico conserva su respuesta 500.
El conflicto genera `PERSISTENCE_CONFLICT` en WARN, sin consultas ni valores SQL.

| Código | Nivel | Diagnóstico |
|---|---|---|
| `REFUND_RECOVERY_PENDING` | WARN | Devolución pendiente de recuperación |
| `EVENT_REFUND_PENDING` | WARN | Cancelación con devolución pendiente |
| `EVENT_PURCHASE_PENDING` | WARN | Cancelación con compra pendiente |
| `PURCHASE_RELEASE_PENDING` | WARN | Compra cancelada con liberación pendiente |
| `PAYMENT_RECOVERY_PENDING` | WARN | Pago/compra pendiente en la fase indicada |
| `PERSISTENCE_CONFLICT` | WARN | Conflicto de persistencia, conservando HTTP 409 |
| `REQUEST_UNEXPECTED_ERROR` | ERROR | Error interno, conservando HTTP 500 |

Convertir texto arbitrario a JSON no lo vuelve seguro. La política depende de
no pasar datos externos como `message`: la lista de campos no censura una cadena
que alguien agregue incorrectamente al mensaje principal de un logger. No se
habilita DEBUG/TRACE de transporte o seguridad para inspeccionar credenciales.

## Evolución pendiente

`correlationId`, `traceId`, `spanId`, actor y `operationId` no se generan ni se
incluyen aún. Su incorporación requiere ampliar explícitamente el contrato y
sus pruebas, con las reglas de validación y propagación del siguiente incremento.
No hay Actuator, Micrometer Tracing, OpenTelemetry, Fluent Bit ni cambios Compose.

La política se prueba con el encoder ECS real de Boot, salida a consola,
excepciones con secretos señuelo, contextos MDC/SLF4J no aprobados y la recuperación
HTTP de un reintegro. La suite financiera existente continúa siendo la referencia
para estados, compensaciones e idempotencia.

Los mensajes de librerías y avisos nativos de la JVM requieren su propia revisión
si se cambian niveles/configuraciones. El contrato no se presenta como un filtro
universal capaz de reconocer cualquier secreto dentro de texto arbitrario.

## Validación ejecutada

La ejecución final de `node TicketYa/integration-tests/e2/test-security.cjs comprapago_service`
terminó con BUILD SUCCESS: 304 casos JUnit, sin fallos, errores ni omitidos. Utilizó
Maven/JDK 21 en un contenedor efímero y H2 en memoria; no se ejecutó la regresión
comercial Docker completa ni se construyeron imágenes de aplicación.

Se verificaron cinco casos nuevos de seguridad del logging y dos casos HTTP/configuración
adicionales, además de los 297 casos preexistentes. La prueba de reintegro conserva
HTTP 503 y ERROR_PENDIENTE al fallar Entradas, recupera a COMPLETADA con HTTP 200,
y comprueba una única operación de reintegro al repetir la solicitud.

La inspección de 265 registros JSON reales de la ejecución final confirmó campos
obligatorios, ausencia de los secretos señuelo/JWT y ausencia de sentencias SQL.
Durante la validación se corrigieron la selección de nodos padre ECS y el canal
adicional de errores JDBC de Hibernate. Evidencias: `target/security-tests.log` y
`target/surefire-reports/`, ignorados por Git.

Los 49 contenedores preexistentes conservaron ID, estado, fechas de arranque y
finalización, reinicios y estado de salud observado. Los seis contenedores
persistentes ya estaban detenidos al comenzar y no fueron iniciados. El volumen
MySQL conserva sus metadatos. No fue posible renovar hashes lógicos de sus datos
con MySQL detenido: no se declara una verificación de integridad por hash ni se
intentó iniciarlo para conseguirla.

## Archivos de este incremento

Todos pertenecen a `TicketYa/comprapago_service`:

- `src/main/resources/application.properties`.
- `src/main/resources/logging.properties` (nuevo).
- `src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/SafeLog.java` (nuevo).
- `src/main/java/ar/edu/unicen/grupo8/comprasservice/exception/GlobalExceptionHandler.java`.
- `src/main/java/ar/edu/unicen/grupo8/comprasservice/service/CompraService.java`.
- `src/main/java/ar/edu/unicen/grupo8/comprasservice/service/PagoService.java`.
- `src/main/java/ar/edu/unicen/grupo8/comprasservice/service/ReintegroService.java`.
- `src/test/resources/application.properties`.
- `src/test/java/ar/edu/unicen/grupo8/comprasservice/observability/EcsLogFixture.java` (nuevo).
- `src/test/java/ar/edu/unicen/grupo8/comprasservice/observability/LoggingSafetyTest.java` (nuevo).
- `src/test/java/ar/edu/unicen/grupo8/comprasservice/service/DevolucionHttpIntegracionTest.java`.
- `LOGGING.md` (nuevo).
