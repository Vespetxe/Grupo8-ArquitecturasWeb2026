# Notificaciones

El punto 11 exige email o push al confirmar una compra, ante cancelación/cambio de evento y al publicar un artista favorito en la ciudad del cliente. Se implementa **push SSE real** y bandeja persistente REST; no requiere SMTP, proveedores externos ni otro microservicio.

## Ubicación y eventos

Compras guarda la bandeja en su MySQL (`notifications`). Cada fila contiene destinatario (`identityOwnerId`), tipo, referencia de negocio, fecha UTC y fecha de lectura. No almacena JWT, direcciones de correo, importes ni claves de idempotencia financieras.

- `RESERVA_CONFIRMADA`: solo tras confirmar la reserva remota.
- `COMPRA_CONFIRMADA` y `ENTRADAS_EMITIDAS`: compra finalizada y emisión validada con sus identificadores persistidos.
- `COMPRA_CANCELADA`: cancelación local finalizada y reserva liberada.
- `REINTEGRO_COMPLETADO`: devolución finalizada; también compensación confirmada de una compra fallida.
- `EVENTO_CANCELADO` / `EVENTO_CAMBIADO`: compradores con entradas previamente emitidas. Los cambios cubren las transiciones de estado existentes; no se agrega una API de edición de fecha/lugar.
- `ARTISTA_FAVORITO_PUBLICADO`: preferencias MongoDB que coincidan con participantes y ciudad normalizada del evento publicado.

## API pública

Todas las rutas pasan por Gateway y requieren `CLIENTE`; el propietario se obtiene exclusivamente del JWT validado.

- `GET /api/v1/me/notifications?page=0&size=20`: lista, orden por fecha e ID descendentes; tamaño máximo 100.
- `GET /api/v1/me/notifications/unread-count`: `{"unread":3}`.
- `PUT /api/v1/me/notifications/{id}/read`: devuelve la notificación propia leída. Repetir conserva la primera fecha de lectura; una ajena o inexistente responde 404.
- `GET /api/v1/me/notifications/stream`: `text/event-stream`, con `Authorization: Bearer <JWT>`. Emite `ready` y, ante nuevas notificaciones confirmadas, `notification` con ID y contenido. Para navegador se utiliza fetch con lectura del stream; nunca JWT en la URL.

Las respuestas personales usan `Cache-Control: no-store`. Las conexiones SSE duran como máximo 30 segundos, acotadas además por la expiración del JWT; límite de cuatro por cliente y 128 globales. El consumidor reconecta y consulta la bandeja para recuperar pendientes. Recibir un push no marca el aviso como leído.

## Integración, idempotencia y recuperación

Los avisos financieros se insertan en la **misma transacción local** que confirma la etapa comercial. Una clave determinista por hecho y destinatario produce el ID único; el upsert nunca reinicia la lectura. No se crean éxitos para pagos rechazados/inciertos ni para emisiones sin confirmar. Los registros históricos sin propietario JWT no se asignan por coincidencia de IDs antiguos.

El push se despacha después del commit, mediante un executor acotado; sus errores o saturación no afectan operaciones comerciales. Si falla la persistencia local, se revierte la etapa junto con el aviso: la recuperación comercial conserva pago, reserva y emisión originales y consulta sus resultados remotos. No se repiten cobros ni se inventan confirmaciones. Las pruebas inyectan este fallo.

Eventos captura una intención durable (`event_notification_outbox`) en la transacción de cambio de catálogo, protegida por el versionado optimista existente. Una tarea entrega hasta veinte intenciones por pasada, cada cinco segundos (`ticketya.notifications.dispatch-ms`; la entrega se puede desactivar con `ticketya.notifications.dispatch-enabled=false`, conservando las intenciones). Las preferencias se consultan en Mongo solo para avisos de favoritos. Los destinatarios de cambios/cancelaciones se resuelven en Compras a partir de sus compras; no se accede a bases ajenas.

La entrega usa el enlace OAuth2 existente Eventos→Compras: `POST /internal/notifications/events`, scope exclusivo `notifications.publish`. No admite JWT humanos ni queda expuesto por Gateway; ese scope no habilita cancelaciones, pagos ni reintegros. Los favoritos se envían en lotes de hasta 500 destinatarios. Ante falla de Mongo, Compras, autenticación o respuesta ambigua, la intención permanece pendiente. Los únicos reenvíos nuevos son de **notificaciones**, con la misma identidad de entrega y deduplicación en destino; no se agregan retries financieros. Cada pasada tiene su propia traza raíz.

## Pruebas y límites

JUnit cubre persistencia, lectura, propiedad, roles, scope mínimo, concurrencia, rollback, recuperación y ausencia de avisos falsos. Node verifica aislamiento y trazas. Campaña HTTP aislada: `node TicketYa/integration-tests/e2/run-security.cjs --notifications --audit --port 48080`; incluye Mongo, push SSE a través de Gateway, caída/recuperación de Compras, favoritos, cambios/cancelación y consistencia financiera. Evidencias bajo `TicketYa/integration-tests/target/notifications/`, ignoradas por Git.

Resultado de la campaña `20299eb3d3d660b0`: PASS, 93 comprobaciones de seguridad y 292 solicitudes comerciales; SSE, recuperación, propiedad y deduplicación verificadas, con 352 eventos de auditoría. JUnit: 444 Compras, 28 Gateway, 129 Eventos entre suite y comprobaciones focalizadas, tres de scopes de Identidad; 20 Node. Se corrigió además la captura de cambios para no emitir otro aviso al repetir el mismo estado, verificada por JUnit focalizada sin repetir la campaña HTTP. Se corrigieron fixtures nuevos (transporte sin retry implícito y cierre de executor compatible con Java 17) y se reejecutaron las pruebas afectadas.

La bandeja es durable; SSE es de mejor esfuerzo y puede reenviar un mismo ID, que el cliente debe deduplicar. Los clientes desconectados recuperan por REST; no hay push del sistema operativo ni correo. Las preferencias vigentes se evalúan al entregar: no se conserva su historial. No se incorpora purga automática ni entrega a clientes históricos sin identidad. La memoria y cola de push están acotadas.

El entorno persistente no se inicia ni migra. Su futura activación requiere autorización para crear las dos tablas nuevas y reprovisionar el cliente técnico con `notifications.publish`. No se modifican los otros esquemas ni se requiere infraestructura adicional.
