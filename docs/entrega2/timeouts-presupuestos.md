> Documento del incremento 47: resultados y lista de archivos son históricos. El alcance final de circuitos y OAuth2 está en [Bloque 4](bloque4-resiliencia.md); los presupuestos aquí definidos siguen vigentes.

# Implementación 47 — Timeouts y presupuestos

## Política efectiva

| Capa | Antes | Ahora, por defecto |
| --- | --- | --- |
| Gateway conexión | 2000 ms | 1000 ms |
| Gateway respuesta Netty | 15 s | 15 s, configurable |
| Gateway cadena completa | Sin límite agregado explícito | 15000 ms |
| Gateway pool | Elastic, pendiente sin cota explícita de aplicación | 50 conexiones, 100 adquisiciones pendientes, espera 500 ms |
| RestClient técnico conexión | 2000 ms | 1000 ms, también handshake TLS |
| RestClient GET | 10000 ms | 2000 ms |
| RestClient comando | 10000 ms | 4000 ms |
| OAuth2 token | 10000 ms | 3000 ms |
| Espera del monitor OAuth2 | Sin timeout explícito | Lock de caché con espera de hasta 500 ms |
| Pool HTTP por emisor | Cliente JDK sin límites propios declarados | 40 conexiones totales, 10 por ruta, adquisición hasta 500 ms |
| Presupuesto Servlet | Sin agregado | 12000 ms, cooperativo |
| Hikari adquisición | Defaults, sin valor explícito del proyecto | 2000 ms |
| Hikari tamaño | Default en negocio; Identidad fijaba 5 | Negocio 10; Identidad 5, configurables |
| Hikari validación | Default | Hasta 1000 ms, nunca mayor que adquisición |
| MySQL conexión | Default del driver | 1000 ms |
| InnoDB row-lock wait | Heredado del servidor | 3 segundos por sesión del pool |

Los valores anteriores heredados no se presentan como mediciones del servidor.
El `SimpleClientHttpRequestFactory` local del constructor de `EventWorkflow` no
se utilizaba: las llamadas reales ya pasaban por `ServiceTokens`. Se eliminó ese
objeto sin efecto para no confundir configuración declarada con aplicada.

Los clientes emisores —Eventos, Compras y Entradas— usan ahora Apache HttpClient 5,
con la versión administrada por Spring Boot 4.1.2. Es una dependencia de transporte,
no Resilience4j ni un bulkhead. Permite limitar el pool, la adquisición y desactivar
retries automáticos por cliente, sin propiedades JVM globales. Conserva el builder
instrumentado por Boot, los siete registros OAuth2 y sus scopes.

## Propiedades y variables

En los tres emisores:

| Propiedad | Variable | Default |
| --- | --- | --- |
| `ticketya.http.connect-ms` | `TY_HTTP_CONNECT_MS` | 1000 |
| `ticketya.http.read-ms` | `TY_HTTP_READ_MS` | 2000 |
| `ticketya.http.command-ms` | `TY_HTTP_COMMAND_MS` | 4000 |
| `ticketya.http.token-ms` | `TY_HTTP_TOKEN_MS` | 3000 |
| `ticketya.http.acquire-ms` | `TY_HTTP_ACQUIRE_MS` | 500 |
| `ticketya.http.pool-size` | `TY_HTTP_POOL_SIZE` | 40 |
| `ticketya.http.per-route` | `TY_HTTP_PER_ROUTE` | 10 |

Los cinco servicios Servlet admiten `ticketya.time.budget-ms` /
`TY_TIME_BUDGET_MS` (12000), y `ticketya.jdbc.acquire-ms`, `pool-size`,
`connect-ms`, `lock-seconds`, con variables `TY_JDBC_ACQUIRE_MS`,
`TY_JDBC_POOL_SIZE`, `TY_JDBC_CONNECT_MS` y `TY_JDBC_LOCK_SECONDS`.

Gateway admite `TY_HTTP_CONNECT_MS`, `TY_GATEWAY_RESPONSE_TIMEOUT` (duración),
`TY_GATEWAY_BUDGET_MS`, `TY_GATEWAY_POOL_SIZE`, `TY_GATEWAY_PENDING_MAX` y
`TY_HTTP_ACQUIRE_MS`. Los límites propios de presupuesto, pools, JDBC y
`TimedTransport` se validan al iniciar: cero y valores fuera de sus rangos
admitidos se rechazan. Las propiedades nativas de SCG conservan su binding;
deben configurarse junto con el límite agregado, que siempre es finito.
No se modificaron `.env` ni Compose; la regresión utiliza los defaults del código.

## Presupuesto y confianza

`RequestBudget` guarda un deadline monotónico en un atributo de cada solicitud.
No utiliza un ThreadLocal propio, estado global mutable ni el reloj comercial.
Las consultas del contexto Servlet usan `RequestContextHolder`, administrado por
Spring. En WebFlux, el filtro usa el exchange y Reactor, conservando el estado de
correlación existente.

Gateway sustituye el encabezado externo `X-TicketYa-Remaining-Ms`; no permite que
el cliente aumente el presupuesto. Los Servlet aceptan como máximo un decimal
positivo y solo para **reducir** su límite local. No es identidad, autorización,
prueba de propiedad ni clave de idempotencia. Nunca cambia claims JWT o roles.

Cada llamada saliente reserva conexión + adquisición + 500 ms para retorno y
persistencia local. El timeout de respuesta se reduce al remanente útil. Después
de obtener un token se vuelve a calcular el presupuesto del comando. También se
transmite un remanente reducido al siguiente servicio; no se reinician doce
segundos en cada salto. Si no hay margen, no se despacha la llamada.

No hay garantía de completar cualquier circuito en doce segundos. Los valores
son conservadores iniciales y deben revisarse con capacidad y percentiles reales.
No se interrumpe la JVM ni se fuerza rollback cuando termina una espera HTTP.

## Etapas comerciales

- Publicación, suspensión y habilitación conservan los controles y recuperación
  de la 46. Si se agota el margen antes del siguiente RPC, las etapas previas
  pueden estar confirmadas y se conserva el error técnico recuperable.
- Pago comprueba margen antes de tomar una nueva intención y antes de cada etapa.
  Un cobro confirmado se persiste aunque su respuesta consuma el presupuesto;
  queda en CONFIRMACION o EMISION, sin reintegrar por el simple vencimiento.
- Devolución comprueba margen antes de tomar el turno y después de registrar el
  reintegro. Si el dinero ya se devolvió, conserva ANULACION y el resultado real.
- Cancelación deja de comenzar nuevas compras/devoluciones cuando no hay margen y
  devuelve sus IDs pendientes. Usa el **202 ya existente** de Compras y el **503
  ya existente** de Eventos; no se agrega contrato asíncrono ni estado persistido.
  Repetir conserva la autorización administrativa original.
- Reserva y compra pueden completar una transacción local después de que el
  cliente deje de esperar. No se interpreta el timeout como rollback. La creación
  de reserva sigue sin clave de idempotencia: consultar antes de repetir POST.
- Recuperación utiliza las identidades originales. No hay retries de aplicación,
  scheduler financiero, cambios de TTL, nuevos estados ni migraciones.

Una respuesta HTTP 201 de pago sigue significando creación de intención, no
compra finalizada. Los estados persistidos determinan el resultado comercial.

## Transporte y persistencia

Apache tiene retries y redirects automáticos desactivados; tampoco mantiene
cookies. Gateway desactiva `retry once` TCP de Reactor Netty, sin filtros Retry.
El pool y la cola pendiente de Gateway se configuran en `HttpClientFactory`
conservando el resto de customizaciones de SCG, TLS e instrumentación.

El límite agregado de Gateway comprende la cadena y la escritura del cuerpo.
Antes de confirmar cabeceras puede responder 504. Si ya se emitieron, no inventa
otro código: se conserva lo enviado y se registra timeout/error. No garantiza
que el negocio remoto haya sido abortado.

La respuesta HTTP bloqueante limita esperas de lectura y comprueba el deadline
durante la lectura del cuerpo. Estos mecanismos no equivalen a una cancelación
transaccional remota. Un emisor lento o un cliente desconectado puede dejar un
resultado desconocido que requiere consulta.

Hikari se configura antes de inicializar los pools. Identidad aplica la misma
política explícitamente **antes de construir** su HikariDataSource: no depende
de propiedades Hikari que su bean manual ignoraba. H2 no recibe SQL MySQL.
`SET SESSION innodb_lock_wait_timeout` solo afecta las conexiones nuevas de la
instancia donde se ejecuta la aplicación; no altera variables globales ni tablas.

No se agrega `socketTimeout` JDBC ni un timeout transaccional general de escritura:
podrían cortar la confirmación y producir commits desconocidos. Se conservan
transacciones locales cortas, restricciones únicas, turnos y reconciliación.
El límite de locks es por espera de fila, no un límite de duración total de una
transacción ni de locks de metadatos. La E/S JDBC en curso no queda interrumpida
por el deadline HTTP; esta es una limitación deliberada, no una garantía de latencia.

## Observabilidad

Se conservan ECS, W3C, correlationId, actores JWT y auditoría posterior al commit.
`TimedTransport` registra `DEPENDENCY_TIMEOUT` con `TIMEOUT` o
`BUDGET_EXHAUSTED`, texto fijo y contexto disponible. No registra cuerpos,
encabezados, URLs completas, secretos ni causas externas. Gateway utiliza la
clasificación de timeout del estado HTTP existente incluso después de enviar
cabeceras. El colector ya admite estos campos; no se cambia Fluent Bit.

## Pruebas reproducibles

```powershell
node TicketYa/integration-tests/e2/test-security.cjs
node --test TicketYa/integration-tests/e2/*.test.cjs
node TicketYa/integration-tests/e2/build-images.cjs
node TicketYa/integration-tests/e2/run-security.cjs --audit --port 48089
```

Todos los tests y la regresión deben usar sus proyectos, esquemas, claves y
volúmenes aislados. No se inicia el Compose persistente. Las pruebas incluyen
timeout real de lectura, handshake TLS que no progresa, adquisición real de
pool HTTP y Hikari, token lento, Gateway HTTP con deadline, escritura con
respuesta perdida y recuperación financiera con las mismas identidades.
Una conexión TCP rechazada se prueba en loopback; no se usa una IP externa
"blackhole" como prueba supuestamente determinista de timeout SYN.

Los reportes locales se conservan en `TicketYa/integration-tests/target/timing47`,
ignorados por Git.

## Archivos de este incremento

Las rutas Java de esta tabla son relativas al paquete propio de cada servicio.
Los cambios de clasificación de la Implementación 46 continúan pendientes en el
mismo árbol; no se revirtieron ni se reemplazaron.

| Proyecto | Archivos de producción/configuración | Pruebas |
| --- | --- | --- |
| Gateway | `timing/GatewayBudgetFilter.java`, `timing/GatewayTransport.java`, `application.properties` | `timing/GatewayBudgetTest.java`, `timing/GatewayBudgetHttpTest.java` |
| Eventos | `pom.xml`, `security/ServiceTokens.java`, `service/EventWorkflow.java`, `application.properties`, los cuatro archivos `timing` indicados debajo | `PublicacionInterrumpidaTest.java`, `PublicacionRecuperacionTest.java`, `timing/JdbcTimeoutTest.java`, `timing/TimedTransportTest.java` |
| Compras | `pom.xml`, `security/ServiceTokens.java`, `service/PagoService.java`, `service/ReintegroService.java`, `application.properties`, los cuatro archivos `timing` | `observability/TechnicalTracingTest.java`, `service/FlujoCompraIntegracionTest.java`, `service/ReintegroIntegracionTest.java`, `timing/JdbcTimeoutTest.java`, `timing/TimedTransportTest.java` |
| Entradas | `pom.xml`, `security/ServiceTokens.java`, `config/OAuth2RestClientConfiguration.java`, `integration/FeignComprasOrderAdapter.java`, `application.properties`, los cuatro archivos `timing` | `timing/JdbcTimeoutTest.java`, `timing/TimedTransportTest.java` |
| Inventario | `application.properties`, los tres archivos `timing` | `timing/JdbcTimeoutTest.java` |
| Identidad | `config/DataSourceConfiguration.java`, `application.properties`, los tres archivos `timing` | `timing/JdbcTimeoutTest.java` |
| Regresión/documentación | `TicketYa/integration-tests/e2/security-isolation.test.cjs`, este documento | Normalización de paquetes en la guarda de equivalencia de los tres emisores |

Los tres archivos comunes por servicio Servlet son `timing/RequestBudget.java`,
`timing/BudgetConfiguration.java` y `timing/JdbcTimeoutConfiguration.java`.
Eventos, Compras y Entradas agregan `timing/TimedTransport.java`.
Son **55 archivos alcanzados por la 47**, incluidos siete ya modificados por la
46; el listado completo del árbol acumulado queda en `target/timing47/files-final.txt`.

## Resultados ejecutados en la Implementación 47

Las seis suites JUnit se ejecutaron nuevamente sobre el código final, usando
Maven/JDK 21 en contenedores temporales y bases H2 de prueba:

| Proyecto | Pruebas aprobadas |
| --- | ---: |
| Gateway | 24 |
| Eventos | 80 |
| Inventario | 67 |
| Entradas | 69 |
| Compras | 378 |
| Identidad | 29 |
| **Total** | **647** |

No hubo fallos, errores ni omisiones en esa pasada final. Las 61 pruebas Node
también aprobaron. La primera pasada detectó una aserción antigua que exigía diez
segundos en `TechnicalTracingTest`; se actualizó al timeout de lectura de dos
segundos, manteniendo la comprobación de un solo envío y limpieza de contexto.

Las seis imágenes se construyeron correctamente. La regresión nueva
`ticketya-e2-reg-2e4d7fc762a6e0a1`, puerto 48089, terminó en PASS:

- 93 comprobaciones HTTP de seguridad y 192 solicitudes comerciales.
- Publicación, reserva, compra, cobro, emisión, devolución parcial, cancelación,
  rechazo y recuperación del pago incierto; 24 solicitudes concurrentes con
  correlaciones distintas y tres actores.
- 171 solicitudes distribuidas correlacionadas y 285 eventos de auditoría.
- Tres intentos de cobro: dos aprobados por un total de 45000 y uno rechazado.
- Dos reintegros por un total de 30000; tres tickets en dos lotes de emisión.
- Sin duplicados financieros ni de tickets; administrador original conservado.
- Verificación de ausencia de secretos en los registros y siete clientes
  técnicos provisionados, sin desactivar JWT.

La prueba de respuesta perdida verifica que el destino recibió una sola
escritura y el transporte no la repitió. Las pruebas financieras adicionales
agotan el presupuesto después del cobro o del reintegro, conservan la etapa
pendiente y completan la recuperación con el mismo pago/devolución.

Una consulta de solo lectura a `performance_schema.variables_by_thread` del
**nuevo MySQL aislado** confirmó `innodb_lock_wait_timeout=3` en las conexiones
de los cinco usuarios de aplicación. La adquisición Hikari acotada se comprobó
con un pool H2 real agotado; no se presenta como una medición de adquisición
bajo carga MySQL ni como una prueba de espera de locks de metadatos.

La comparación final conservó exactamente los estados, IDs, inicios, finales y
contadores de reinicios de los 93 contenedores anteriores. Los 27 volúmenes
previos permanecen con sus mismos metadatos. Los seis contenedores persistentes
siguen detenidos y `ticketya-e2-infra_mysql_data` se conserva. No se accedió a sus
bases ni se comprobó su contenido byte por byte. La nueva regresión queda con
siete contenedores running, cero reinicios, MySQL healthy y un volumen propio.
Solo Gateway publica `127.0.0.1:48089`.

No se ejecutó una prueba de timeout TCP SYN contra una red blackhole: se probaron
el handshake TLS bloqueado, la conexión local rechazada y los timeouts reales de
lectura/adquisición. Tampoco se realizaron pruebas de carga prolongada. Los
límites iniciales requieren calibración posterior. No se agregaron commits,
pushes, migraciones, circuit breakers ni retries de aplicación.

La compilación conserva una advertencia previa de deprecación de
`RestClient.Builder.messageConverters(Consumer)` en los tres emisores OAuth2.
No impide construir ni probar; su sustitución por la API futura queda pendiente,
sin cambiar versiones ni conversiones de tokens en este incremento.
