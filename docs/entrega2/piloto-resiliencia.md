# Implementación 48 — Piloto selectivo de resiliencia

## Alcance y compatibilidad

Solo se decoran las lecturas `Compras → Eventos GET /api/v1/events/{id}` y
`Entradas → Compras GET /internal/orders/{orderId}`. Cada adaptador singleton
posee su propio `PilotReadGuard`, circuito y bulkhead semáforo. No hay registro
global compartido, endpoints de administración del circuito, fallback, retry ni
sondeos sintéticos. Las llamadas de prueba de HALF_OPEN son solicitudes reales.

Se agregan únicamente `resilience4j-circuitbreaker:2.4.0` y
`resilience4j-bulkhead:2.4.0`, con `resilience4j-core` transitivo. No se agrega
starter, AOP, integración Spring Cloud, scheduler ni infraestructura externa.
La integración es programática y síncrona: conserva el contexto Servlet, JWT,
MDC y tracing de los clientes instrumentados existentes.

La [documentación oficial](https://resilience4j.readme.io/docs/getting-started)
requiere Java 17 para Resilience4j 2. La [release 2.4.0](https://github.com/resilience4j/resilience4j/releases/tag/v2.4.0)
incluye el ajuste del target a Java 17. Se verificaron los JAR publicados mediante
`javap`: CircuitBreaker, Bulkhead e IntervalFunction tienen major version 61.
Esto verifica el nivel de bytecode; la ejecución local de las suites utiliza
JDK 21 y Spring Boot 4.1.2. No se afirma una ejecución adicional sobre JRE 17.

## Configuración efectiva por proceso

| Propiedad `ticketya.resilience.read.*` | Variable | Default |
| --- | --- | ---: |
| `window` | `TY_READ_WINDOW` | 20 |
| `minimum` | `TY_READ_MINIMUM` | 10 |
| `failure-percent` | `TY_READ_FAILURE_PERCENT` | 50 |
| `open-ms` | `TY_READ_OPEN_MS` | 10000 |
| `half-open-calls` | `TY_READ_HALF_OPEN_CALLS` | 2 |
| `concurrent` | `TY_READ_CONCURRENT` | 8 |

Ventana COUNT_BASED, mínimo no mayor que la ventana, parámetros validados al
construir el bean. Bulkhead con espera cero, sin cola. La transición OPEN a
HALF_OPEN ocurre al recibir una solicitud después de diez segundos; no se crea
un hilo de sondeo. HALF_OPEN permite como máximo dos llamadas simultáneas.
El estado es local a cada instancia y se reinicia con el proceso.

## Orden y clasificación

1. Comprobar margen del presupuesto monotónico de la 47, reservando 2000 ms.
2. Adquirir inmediatamente el semáforo; si está saturado, no comenzar la lectura.
3. Pedir permiso al circuito; si está OPEN, no ejecutar el proveedor HTTP/OAuth2.
4. Ejecutar una única lectura mediante el cliente OAuth2 y transporte existentes.
5. Validar el snapshot real antes de contabilizar éxito; liberar permisos.

La clasificación se realiza antes de traducir excepciones HTTP a las categorías
del adaptador. Así no se pierde la distinción entre un 409 y un 503.

| Resultado de la lectura | Estadística del circuito | Comportamiento |
| --- | --- | --- |
| Snapshot válido | Éxito real de lectura | Devolver datos reales; reglas comerciales posteriores sin cambios |
| 429, 500/502/503/504 y otros 5xx | Fallo técnico | Conservar traducción técnica y recuperación existentes |
| Timeout, conexión interrumpida, JSON/snapshot inválido o vacío | Fallo técnico | Nunca inferir precio, stock, propiedad, inexistencia ni éxito |
| 401/403 HTTP o `TechnicalAuthFailure` | Ignorado, ni éxito ni fallo | Mantener error de autenticación técnica; no conceder acceso |
| 404, 409, 422 y otros 4xx distintos de 429 | Ignorado, ni éxito ni fallo | Mantener clasificación de la 46; solo el 404 contractual demuestra ausencia |
| Rechazo comercial | Ignorado si atraviesa el guard | Propagar sin fallback |
| Presupuesto agotado antes del despacho | Sin muestra | Error técnico, sin adquirir permisos ni enviar solicitudes |
| Bulkhead lleno / circuito OPEN | Sin muestra de dependencia | Error técnico controlado, sin despacho |

Un evento CANCELADO o una orden PENDIENTE dentro de una respuesta válida no son
fallos HTTP: la consulta obtuvo un snapshot real. El rechazo comercial posterior
mantiene su contrato y no se convierte en un valor de fallback.

En Compras el rechazo local del piloto conserva `ComunicacionPasarelaException`
y su respuesta 503 existente; Entradas conserva `OrdersUnavailableException` y
`ORDERS_UNAVAILABLE`/503. Las etapas financieras, turnos, claves y administrador
original no se modifican. El guard no envuelve cobros, reintegros, confirmaciones,
emisiones ni cancelaciones. Una lectura utilizada dentro de esos circuitos sí
puede quedar temporalmente indisponible; la lógica previa conserva lo pendiente.

La obtención de tokens mantiene exclusivamente sus límites y caché de la 47.
No se agrega un circuito a Identidad: su fallo de autenticación se propaga y se
ignora en las métricas del piloto.

## Logging seguro

Eventos `DEPENDENCY_READ` y `CIRCUIT_TRANSITION`; campos `dependency`, `operation`,
`circuit`, `circuitState`, `durationMs`, `errorCategory` y correlationId cuando
existe contexto HTTP. El scope de tracing existente conserva traceId/spanId.
Las categorías incluyen `CIRCUIT_OPEN`, `BULKHEAD_FULL`, `BUDGET_EXHAUSTED`,
`TECHNICAL_AUTH`, `RATE_LIMITED`, `UPSTREAM_5XX`, `TIMEOUT`, `CONNECTION`,
`INVALID_RESPONSE` e `IGNORED`. Los textos y nombres proceden del código, no de
URLs, cuerpos, encabezados o mensajes remotos.

Los cuatro campos nuevos se agregan a las listas permitidas de ECS de los dos
servicios y del archivo central. Se mantiene stdout/docker logs y no se copian
actores o credenciales de una respuesta remota. Esto es diagnóstico técnico;
no reemplaza ni anticipa la auditoría comercial posterior al commit.

## Archivos

- Compras y Entradas: `pom.xml`, `application.properties`, `logging.properties`,
  nuevo `resilience/PilotReadGuard.java`, `resilience/PilotReadGuardTest.java` y
  `resilience/PilotReadHttpTest.java`.
- Compras: `client/RestEventoClient.java`.
- Entradas: `integration/RestComprasOrderClient.java`.
- Archivo central: `docker/observability/archive.py` y `archive_test.py`.
- Este documento.

Los archivos Java son relativos al paquete propio de cada proyecto. No se
modifican reglas comerciales, rutas, perfiles JWT, scripts SQL ni Compose.
Los cambios previos 46–47 permanecen en el mismo árbol sin staging ni commit.

## Validación y límites

Las pruebas deterministas usan reloj inyectado exclusivamente por un constructor
de paquete, latches y servidores HTTP locales. Comprueban configuración, apertura,
HALF_OPEN, recuperación, estadísticas ignoradas, saturación ocho/novena llamada,
independencia, presupuesto, despacho único, errores reales de adaptadores,
snapshot inválido y logging seguro. No requieren bases persistentes.

Los reportes locales quedan bajo `TicketYa/integration-tests/target/resilience48`,
ignorados por Git. La regresión comercial debe usar proyecto, claves, esquemas,
volúmenes y puertos nuevos. No se ejecuta el Compose persistente.

Los circuitos no garantizan disponibilidad ni consistencia por sí solos. Una
dependencia puede fallar después del permiso; una respuesta perdida sigue
requiriendo reconciliación. Los 401/403 no abren el circuito y necesitan su
diagnóstico de credenciales. Los límites iniciales requieren calibración y el
estado de circuitos no está coordinado entre réplicas. No se amplía el piloto
a escrituras ni se comienza la Implementación 49.

## Resultado final de validación

- Después de recuperar Docker: Compras 405 JUnit y Entradas 96, todas aprobadas
  (54 pruebas específicas del piloto); Node 61 y archivo central Python 8 aprobadas.
- Construidas las seis imágenes y el colector. Un argumento de puerto 48090 fue
  rechazado antes de crear recursos; se usó el puerto permitido 48086 sin cambiar guardas.
- Una regresión en `ticketya-e2-reg-d6d81f83c25269e2`: PASS, 93 comprobaciones de
  seguridad, 192 solicitudes comerciales (71 negativas), 285 eventos de auditoría.
- Dos cobros aprobados por 45000, uno rechazado; dos reintegros por 30000,
  tres tickets en dos lotes, sin duplicados. Se conservó el administrador original.
- Ambos circuitos terminaron CLOSED. Sus 45 registros ECS conservaron correlación
  y trazas y llegaron al archivo central; caída y recuperación del colector aprobadas.
- Los 100 contenedores y 28 volúmenes anteriores conservaron el estado posterior
  a la recuperación. Seis persistentes detenidos; entorno nuevo de ocho contenedores
  running, cero reinicios y dos volúmenes nuevos. No se accedió a MySQL persistente.
- Salvedad: tras reconstruir las etiquetas dev, siete IDs de imagen anteriores
  dejaron de resolver. No se ejecutaron comandos de eliminación ni reparación;
  no se afirma conservación de esos IDs. Evidencia en `target/resilience48`.
