> Documento del incremento 46: la ausencia de circuitos/retries indicada describe ese cambio puntual. Ver el alcance final en [Bloque 4](bloque4-resiliencia.md). Se conserva la política de clasificación y los resultados históricos.

# Implementación 46 — Clasificación segura de fallos

## Alcance

Se conservan estados persistidos, contratos de solicitudes, roles, scopes, claves,
transacciones y tiempos existentes. No se agregan retries, circuit breakers,
bulkheads, schedulers ni migraciones. Las únicas decisiones nuevas son clasificar
fallos ambiguos como pendientes y validar la respuesta antes de utilizarla.

## Política por contrato

| Respuesta remota | Tratamiento |
| --- | --- |
| 401/403 | `TechnicalAuthFailure`; no repetición ni compensación automática |
| 404 de Inventario/Eventos/Entradas a Compras | No demuestra rechazo definitivo de una escritura; pendiente |
| 404 al consultar órdenes desde Entradas | Solo el DTO existente `status:404` y mensaje de compra inexistente o recurso oculto genera `OrderNotFoundException`; ambos deniegan acceso. Cuerpo desconocido o contradictorio: indisponible |
| 409 genérico o `ORDER_NOT_CONFIRMED` remoto | No distingue carrera, progreso o conflicto permanente; pendiente, sin compensación automática |
| 422 de emisión con `ApiError` completo y `code:INVALID_ORDER` | Validación definitiva del contrato existente; conserva compensación recuperable actual |
| Otro 422 o cuerpo incompleto | Rechazo no confirmado; pendiente |
| 429 | `RATE_LIMITED`; nunca rechazo comercial |
| 500/502/503/504 | `UNAVAILABLE`; no demuestra ausencia de efecto remoto |
| Timeout, EOF, error de conexión o decodificación | `RESULT_UNKNOWN`; consultar estado antes de otra escritura |
| Snapshot incompleto, contradictorio o con estado desconocido | `INVALID_RESPONSE`; no autoriza reintegro ni liberación |

`RemoteCallSupport.Operation` limita el reconocimiento de `INVALID_ORDER` a la emisión;
ese mismo código en otro endpoint no habilita compensación. Se descartan cuerpos,
URLs y causas externas al construir `RemoteFailure`. El texto remoto nunca se
utiliza como mensaje de logging. Los cuerpos de error examinados se limitan a
4096 bytes y no se conservan en la excepción nueva.

Inventario no tiene códigos de error estables en su DTO. Eventos reconoce
únicamente los mensajes controlados existentes de inventario incompatible y
bloqueo de ciclo de vida, con el status y `exitoso:false` correspondientes. Cualquier
otra combinación permanece pendiente. Esto conserva los rechazos válidos actuales
y falla de forma conservadora si cambia el texto del contrato; incorporar códigos
estables públicos requerirá un incremento aprobado aparte.

## Respuestas exitosas y reconciliación

- `[]` es una ausencia confirmada de tickets; un cuerpo vacío, `null`, JSON roto
  o una fila inválida no lo es. Se valida toda la selección antes de registrar
  equivalencias locales de UUID.
- La reserva debe coincidir con el ID consultado y contener identidades, cantidad,
  vencimiento y estado conocidos. Después del cobro, un snapshot contradictorio
  con la compra también queda pendiente. Una reserva confirmadamente liberada o
  vencida continúa utilizando las reglas comerciales existentes.
- La inicialización de Inventario exige el `MensajeResponse` con `exitoso:true`.
  Los endpoints cuyo contrato real es `void` no requieren un cuerpo ficticio.
- La coordinación de cancelación exige `eventoId` coincidente y las listas
  `devoluciones` y `comprasPendientes`. 202, pendientes o cuerpo inválido conservan
  la cancelación pendiente y su autorización administrativa original.
- Un cobro aprobado puede permanecer en CONFIRMACION o EMISION. Un reintegro
  completado puede permanecer en ANULACION: no se devuelve otra vez el dinero.
  La recuperación utiliza la misma compra, pago, devolución, clave e importe.

No se modifica el advice HTTP público de Compras: los fallos capturados durante
procesamiento conservan sus respuestas y estados existentes; los errores de
comunicación de reintegros conservan 503. Los fallos previos a la intención local
siguen el handler genérico existente. No se agregan campos ni rutas públicas.
Cambian únicamente las interpretaciones de respuestas remotas ajenas a su contrato,
que ya no se presentan como rechazos definitivos o éxitos confirmados.

## Observabilidad y límites

`SafeLog` incorpora las categorías controladas de `RemoteFailure` en campos ya
permitidos por ECS. Se conservan correlationId, W3C, auditoría posterior al commit,
JWT y los siete enlaces OAuth2. No se cambia `ServiceTokens`, su caché, sus scopes
ni sus timeouts. No se registra contenido HTTP para diagnosticar un error.

No se pretende resolver en este incremento la disponibilidad: un rechazo que no
puede probarse puede permanecer pendiente y requerir revisión. Tampoco se agrega
reconciliación automática. La etapa 47 y sus cambios de tiempos están fuera de alcance.

## Validación reproducible

```powershell
node TicketYa/integration-tests/e2/test-security.cjs comprapago_service
node TicketYa/integration-tests/e2/test-security.cjs EventoCatalogo_Service
node TicketYa/integration-tests/e2/test-security.cjs entradas_service
node --test TicketYa/integration-tests/e2/*.test.cjs
```

Los tests Maven usan los perfiles H2 existentes en contenedores temporales. Los
tests del adaptador HTTP usan servidores locales de prueba, JWT de prueba donde
corresponde, y no acceden a bases persistentes. Los escenarios de respuesta perdida
comprueban el efecto remoto y su lectura posterior; la recuperación financiera
comprueba persistencia y conteos con los simuladores transaccionales existentes.

Para regresión real se reconstruyen las imágenes de los tres servicios afectados
y se ejecuta el runner existente `run-security.cjs --audit` en un proyecto nuevo,
con puerto libre entre 48080 y 48089. Debe esperarse a que terminen los contenedores
temporales Maven antes de tomar la línea base de la regresión. No usar el Compose
persistente ni sus credenciales. Reportes y líneas base quedan bajo `target`,
ignorados por Git; se conservan los volúmenes para auditoría.

## Resultados de la implementación

Ejecutado sobre HEAD `11e29f2`, sin commits nuevos:

- Compras: **366 JUnit**; Eventos: **70**; Entradas: **59**. Total afectado:
  **495**, sin fallos, errores ni omitidas. No se reejecutaron las suites de los
  tres proyectos cuyo código no cambió.
- **61 pruebas Node** aprobadas, incluidas guardas de aislamiento y consistencia
  de JWT, políticas y clientes técnicos.
- Casos parametrizados 401/403/404/409/422/429/500/502/503/504: confirmación y
  emisión quedan pendientes sin anular, liberar ni reintegrar; recuperar conserva
  pago y cobro originales. Los tests de reintegro mantienen ANULACION y el
  reintegro ya completado; recuperar no repite dinero ni libera anticipadamente.
- Un servidor HTTP de prueba ejecutó una escritura y cerró la conexión sin
  respuesta. Se comprobó un único efecto, error desconocido y lectura posterior
  de ese efecto; no se agregó un retry del POST.
- Se verificaron timeout, EOF y conexión fallida mediante excepciones controladas,
  y respuestas vacías, malformadas y contradictorias mediante HTTP local. Estas
  inyecciones pertenecen a JUnit/H2; no se inyectaron esos fallos en Docker persistente.
- La primera pasada de Entradas detectó un stub 404 que enviaba el cuerpo de una
  orden PENDIENTE. Se corrigió para representar el DTO real de error y se añadió
  un test separado: el 404 contradictorio devuelve 503 y no emite tickets. La
  suite final pasó; no se eliminó la comprobación de ausencia válida con 404.
- Las tres imágenes modificadas se construyeron correctamente. Regresión nueva
  `ticketya-e2-reg-79d7e83c170c0f5c`, Gateway `127.0.0.1:48088`: **PASS**,
  **93 comprobaciones HTTP de seguridad**, **192 solicitudes comerciales**,
  **71 negativas**, **24 concurrentes** con tres actores, **285 auditorías** y
  **171 solicitudes distribuidas**. Publicación, reserva, pago, emisión,
  devolución parcial, cancelación, rechazo y recuperación incierta completados.
- SELECT sobre los esquemas de esa corrida confirmó tres intentos de cobro,
  dos aprobados por **45000** en total, uno rechazado, dos reintegros por **30000**,
  tres tickets y dos lotes; idempotencia y administrador original conservados.
- Los **86 contenedores** anteriores conservaron ID, estado, inicio, fin y
  reinicios; los **26 volúmenes** anteriores conservaron sus metadatos. Los seis
  persistentes permanecieron detenidos y no se accedió a sus bases. La corrida
  nueva conserva siete componentes running, MySQL healthy y su volumen propio.

Los reportes completos y la línea de base están en
`TicketYa/integration-tests/target/failures46`; el reporte comercial detallado está
en `TicketYa/integration-tests/target/security/79d7e83c170c0f5c/report.json`.
Son evidencia local ignorada, no material para versionar con credenciales.
