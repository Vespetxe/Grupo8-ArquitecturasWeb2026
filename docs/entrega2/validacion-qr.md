# Validación de entradas por QR

Implementa el punto 8 del PDF oficial (página 2): entrada existente, evento correcto y un único ingreso. Se reutiliza `qr_payload`, UUID v4 aleatorio de 122 bits aleatorios, independiente del `ticketId` y con índice único. Es un valor opaco, sin JWT, datos personales ni claves. No se generan imágenes ni se incorpora escáner/UI, servicios o dependencias.

## API y autorización

`POST /accesos/eventos/{eventId}/validaciones`, cuerpo `{"qr":"80b128d6-3bf2-4dc9-8cea-4b2db84fc101"}` (ejemplo ficticio). El token se envía únicamente en el cuerpo, nunca en la URL. Solo JWT humano con rol PERSONAL_ACCESO y asignación activa al mismo evento. CLIENTE, ORGANIZADOR, ADMINISTRADOR sin rol de acceso y clientes técnicos no pueden escanear.

No había asignaciones de eventos implementadas. Se agrega su mínimo almacenamiento local en Entradas, con administración idempotente:

- `PUT /admin/accesos/eventos/{eventId}/personal/{staffId}` asigna.
- `DELETE /admin/accesos/eventos/{eventId}/personal/{staffId}` revoca, conservando la fila.

Ambas operaciones requieren ADMINISTRADOR. El administrador utiliza IDs de eventos del catálogo e identidades provisionadas en Identidad. No se consulta remotamente Identidad ni Catálogo al asignar: una asignación no crea un evento comercial, no concede roles y no basta sin un JWT válido de PERSONAL_ACCESO. Permite preparar el personal antes de la primera emisión. El evento del ticket se comprueba siempre al escanear; no hay permiso global implícito por ser personal o administrador. La identidad del operador proviene exclusivamente del JWT, nunca de headers o del cuerpo.

## Resultados

Primera admisión: HTTP 200, por ejemplo `{"accepted":true,"code":"ACCESS_ACCEPTED","ticketId":"...","eventId":"1","validatedAt":"2026-11-15T00:00:00Z"}`.

Repetición: HTTP 409, `{"accepted":false,"code":"ALREADY_USED","ticketId":null,"eventId":"1","validatedAt":null}`. Otros motivos: 400 INVALID_QR (formato canónico UUID v4 en minúscula); 404 TICKET_NOT_FOUND; 409 EVENT_MISMATCH, TICKET_NOT_ENABLED o EVENT_BLOCKED. Sin JWT: 401; rol/asignación insuficientes: 403, con los errores de seguridad existentes. No se devuelve el QR ni el comprador en la respuesta de control.

## Atomicidad y compatibilidad

En una transacción se bloquea primero `emission_event_gate` del evento —el mismo bloqueo utilizado por cancelación y por asignación/revocación— y después la entrada mediante `SELECT FOR UPDATE` por QR. Se exige asignación activa, gate no cancelado, evento coincidente, entrada EMITIDA, lote no anulado y ausencia de ingreso previo. Se guardan `validated_at` (UTC, microsegundos) y `validated_by` en la propia entrada. Ante concurrencia solo una solicitud puede persistir el primer uso; la siguiente ve el registro y se rechaza.

El estado de acceso USADA se representa por `validated_at != null`, separado del estado comercial. `TicketStatus` y el contrato existente de emisión/consulta no cambian. Las anulaciones y reintegros conservan el registro del primer uso y nunca rehabilitan un QR. La versión optimista existente protege frente a escrituras comerciales concurrentes: un conflicto revierte, no sobrescribe silenciosamente la admisión. El evento se serializa con su cancelación; una revocación terminada antes del escaneo impide ingresar.

La aceptación se responde tras el commit. Auditoría TICKET_VALIDATED posterior al commit, con ID de entrada, actor y trazas existentes; nunca se registra el QR ni el cuerpo. La respuesta lleva Cache-Control: no-store. Ante error de comunicación no se inventa aceptación: repetir un QR ya utilizado devuelve rechazo, no un segundo éxito.

Persistencia nueva: tabla `event_access` y dos columnas anulables en `tickets`. Hibernate update se prueba únicamente sobre MySQL aislado; los contenedores y esquemas persistentes permanecen intactos.

## Pruebas y límites

JUnit: Entradas 118 (12 nuevas de admisión), Gateway 26; cinco pruebas Node de aislamiento. Incluyen primer/segundo uso, token desconocido/inválido, anulada/reintegrada, evento equivocado, roles y asignación/revocación, dos validaciones simultáneas, persistencia, cancelación concurrente y conservación del registro al reintegrar.

Reproducción HTTP con imágenes actuales: `node TicketYa/integration-tests/e2/run-security.cjs --qr --audit --port 48081`. Usa un proyecto nuevo con esquemas, claves y volumen propios y JWT reales. Reutiliza la devolución parcial y la entrada restante de la regresión comercial, sin crear compras adicionales para QR; valida en concurrencia y luego cancela, comprobando que el registro no se borra. Los QR usados se mantienen en memoria para verificar que no aparecen en logs y no se guardan en el informe.

No hay validación offline ni ventana horaria del evento: la consigna no define horarios de ingreso. Tampoco se verifican personas físicamente ni se agregan reglas de devolución por haber ingresado. El control se basa en estado de entrada, lote, gate y asignación. El bloqueo por evento es una simplificación adecuada para esta entrega, sin promesa de rendimiento de producción. Los recursos de prueba se conservan para auditoría; su limpieza o el despliegue persistente requieren autorización.

Resultado HTTP de esta ejecución: 93 comprobaciones de seguridad y 213 solicitudes comerciales; QR, auditoría, trazabilidad y ausencia de QR/JWT/credenciales en logs: PASS. Dos escaneos simultáneos: uno aceptado y otro ALREADY_USED; un único registro persistido, conservado tras cancelación. Se mantuvieron dos cobros aprobados por 45000, un rechazo, dos reintegros por 30000 y tres tickets, sin duplicados. Evidencia local ignorada: `integration-tests/target/security/d76cc07e172cd31d/report.json` y `target/qr/`.
