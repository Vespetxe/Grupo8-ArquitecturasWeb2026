# Reportes para organizadores

El punto 12 exige entradas vendidas y devueltas por sector, porcentaje de ocupación y recaudación de los eventos del organizador. No exige rangos de fechas ni exportación. Se implementa un reporte acumulado por evento, sin nuevas tablas, dependencias ni infraestructura.

## API y seguridad

`GET /api/v1/events/{eventId}/report`, mediante Gateway, requiere `ORGANIZADOR`. Eventos comprueba `identityOwnerId` contra el `sub` del JWT antes de consultar dependencias. Evento ajeno o inexistente: 404; rol incorrecto: 403; falta de autenticación: 401; ID cero: 400. No se incorpora un permiso administrativo nuevo ni se acepta un organizador declarado por parámetro.

La respuesta incluye evento, estado, fecha UTC, métricas totales y por sector (ID, nombre y tipo), fechas de cada fuente y señales de conciliación. Usa `Cache-Control: no-store`. No expone clientes, compras individuales, tarjetas, QR, tokens ni claves financieras.

Lecturas internas, exclusivamente para el caller técnico Eventos:

| Servicio | Endpoint GET | Scope mínimo |
|---|---|---|
| Compras | `/internal/reports/events/{eventId}/sales` | `reports.sales.read` |
| Entradas | `/internal/reports/events/{eventId}/tickets` | `reports.tickets.read` |
| Inventario | `/internal/reports/events/{eventId}/inventory` | `reports.inventory.read` |

Reutilizan tres de los siete enlaces OAuth2 existentes; no reenvían JWT humanos ni están disponibles por Gateway. Los nuevos scopes solo permiten sus lecturas, sin habilitar pagos, reintegros, emisión o cancelaciones. No se consultan bases ajenas.

## Criterios contables y fórmulas

- **Vendidas**: cantidades de detalles históricos de compras `COMPLETADA`, `DEVUELTA_PARCIAL` o `DEVUELTA_TOTAL`, con etapa `FINALIZADA` y un pago confirmado. Se excluyen pendientes, procesando, fallidas y canceladas. Una devolución total conserva la venta histórica.
- **Devueltas**: entradas distintas de detalles de devolución asociados a reintegros `COMPLETADO`. Solicitud, aprobación o resultado incierto no significan devolución confirmada. Una devolución parcial descuenta solo sus entradas.
- **Vendidas netas**: vendidas menos devueltas.
- **Recaudación bruta**: importe histórico de pagos de esas adquisiciones, conciliado con los subtotales de compra; no se usan precios actuales del catálogo.
- **Reintegrado**: suma de reintegros confirmados, conciliada con sus detalles por sector y el acumulado del pago.
- **Neto**: bruta menos reintegrado. Si hay procesamiento o resultados financieros inciertos, `financialComplete=false` y `netCollected=null`; se conservan los importes confirmados por separado, sin inventar un resultado final.
- **Ocupación comercial (%)**: `100 × vendidas netas / capacidad operativa`, redondeada a dos decimales. No incluye reservas temporales ni equivale al ingreso físico al evento. Capacidad cero o inventario aún no inicializado: porcentaje null; nunca se sustituye capacidad desconocida por cero.

Los importes se expresan en `UNIDAD_MONETARIA_DEL_SISTEMA`: el dominio no persiste un código ISO de moneda, por lo que no se inventa uno ni se hacen conversiones.

Un pago conocido no asignable a una adquisición efectiva deja el resultado financiero incompleto. Datos incompatibles (p. ej., varios cobros confirmados para una compra o un reintegro sin detalle conciliable) producen 503 en lugar de cifras supuestas. La compensación de una compra fallida no se cuenta como venta y devolución de una compra exitosa.

## Origen y consistencia

Eventos aporta catálogo, sectores y propiedad. Compras aporta ventas y reintegros; cada operación se contabiliza por sus registros persistidos, sin multiplicación de joins ni suma de reintentos. Entradas aporta tickets emitidos, activos y anulados; el estado `REINTEGRADA` se agrupa con anulados. Los ingresos QR usan `validatedAt`, incluso cuando después se anula una entrada. Estos contadores no sustituyen la contabilidad comercial de Compras.

Inventario aporta capacidad, disponibilidad y estado actuales. Un inventario cancelado conserva capacidad, pero su disponibilidad de venta es cero; una ocupación comercial cero no implica que esté habilitado para vender. `NO_INICIALIZADO` deja capacidad y disponibilidad null.

No hay una transacción distribuida de lectura: cada fuente tiene su fecha. `sourcesAligned` advierte diferencias entre tickets activos y ventas netas o cantidades superiores a la capacidad, posibles durante una recuperación o transición concurrente. No se ocultan divergencias ni se cachea disponibilidad.

Si una dependencia falla, devuelve una respuesta vacía/inválida o rechaza la autenticación técnica, el reporte responde 503. Una lista vacía válida de una fuente sí representa ausencia comprobada de registros. Se reutilizan timeouts, presupuestos y circuitos independientes para las tres familias GET, sin nuevos retries ni escrituras.

## Pruebas y ejecución

JUnit focalizada cubre acumulados por evento, eventos sin ventas, parciales/totales, exclusión de estados, doble contabilización, saldos inciertos, conciliación, JWT/propiedad, scopes y dependencia caída. Node comprueba aislamiento y sincronización de políticas.

Campaña aislada: `node TicketYa/integration-tests/e2/run-security.cjs --reports --commercial-only --port 48080`. Usa identidades y tokens OAuth2 reales; comprueba reportes durante publicación, reserva, compra, pago, devolución parcial, cancelación, rechazo y recuperación del pago incierto. Compara instantáneas financieras antes/después de las consultas e inyecta la caída de Compras. No repite las suites generales de seguridad u observabilidad ya aprobadas. Evidencias en `TicketYa/integration-tests/target/reports/`, ignoradas por Git.

Resultados de esta ejecución: 44 JUnit focalizadas (Compras 8, Eventos 21, Entradas 2, Inventario 2, Gateway 7 e Identidad 4), nueve Node y campaña `515fea1796e17328` PASS con 274 solicitudes comerciales autenticadas. Se verificaron 14 instantáneas de reportes, incluida incertidumbre con neto null, y recuperación tras caída de Compras. Sin cobros, reintegros ni emisiones duplicados; las consultas conservaron las instantáneas financieras. Se reejecutaron únicamente las pruebas afectadas por las validaciones de conciliación y campos remotos ausentes.

Limitación de escala: la agregación financiera recorre los registros del evento en memoria; volúmenes de producción requerirían optimizar las consultas, sin cambiar los criterios contables.

El entorno persistente no se inicia ni modifica. Para activarlo en el futuro se requieren imágenes actualizadas y reprovisionar los scopes técnicos con autorización; no hay migraciones ni cambios de esquemas en este bloque.
