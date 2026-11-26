# Cobertura HTTP de la reentrega OpenFeign

Corrida `ticketya-e2-reg-b2a0b652b0337244`. Las 87 operaciones tienen un caso HTTP ejecutado. La última columna distingue flujos positivos de negativas; un 401/403 esperado no acredita una mutación comercial exitosa. El reporte original conserva FAIL por el timeout del comando del runner; las continuaciones y el flujo autónomo se registran por separado.

Negocio original: 455 solicitudes mediante el adaptador Newman. Flujo autónomo: 50 solicitudes y 78 aserciones aprobadas. Probes: 87 operaciones, siete emisiones OAuth2, cuatro readiness autenticados y dos OpenAPI administrativos. Dos expectativas equivocadas se corrigieron con comprobaciones focalizadas; no se ocultó el reporte inicial. SSE se verificó con el helper de streaming.

| Servicio | Método | Endpoint | Acceso | HTTP esperado/obtenido | Evidencia funcional |
|---|---|---|---|---|---|
| eventos | GET | `/api/v1/participants/{id}` | ANONIMO | 404/404 | Flujo funcional Newman |
| eventos | PUT | `/api/v1/participants/{id}` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | DELETE | `/api/v1/participants/{id}` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | GET | `/api/v1/venues` | ANONIMO | 200/200 | Respuesta positiva del proveedor + probe Newman |
| eventos | POST | `/api/v1/venues` | ORGANIZADOR|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | GET | `/api/v1/venues/{venueId}/sectors` | ANONIMO | 200/200 | Respuesta positiva del proveedor + probe Newman |
| eventos | POST | `/api/v1/venues/{venueId}/sectors` | ORGANIZADOR|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | GET | `/api/v1/participants` | ANONIMO | 200/200 | Flujo funcional Newman |
| eventos | POST | `/api/v1/participants` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | POST | `/api/v1/events` | ORGANIZADOR|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | POST | `/api/v1/events/{eventId}/prices` | ORGANIZADOR|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | PATCH | `/api/v1/events/{eventId}/suspend` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | PATCH | `/api/v1/events/{eventId}/status` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | PATCH | `/api/v1/events/{eventId}/publish` | ORGANIZADOR|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | PATCH | `/api/v1/events/{eventId}/cancel` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| eventos | GET | `/api/v1/venues/{venueId}` | ANONIMO | 200/200 | Respuesta positiva del proveedor + probe Newman |
| eventos | GET | `/api/v1/sectors/{sectorId}/seats` | ANONIMO | 200/200 | Respuesta positiva del proveedor + probe Newman |
| eventos | GET | `/api/v1/events/{eventId}` | CLIENTE|ORGANIZADOR|ADMINISTRADOR|PERSONAL_ACCESO | 401/401 | Flujo funcional Newman |
| eventos | GET | `/api/v1/events/search` | ANONIMO | 200/200 | Flujo funcional Newman |
| eventos | GET | `/api/v1/events/{eventId}/report` | ORGANIZADOR | 401/401 | Flujo funcional Newman |
| eventos | GET | `/api/v1/me/preferences` | CLIENTE | 401/401 | Flujo funcional Newman |
| eventos | PUT | `/api/v1/me/preferences` | CLIENTE | 401/401 | Flujo funcional Newman |
| eventos | GET | `/api/v1/me/recommendations` | CLIENTE | 401/401 | Flujo funcional Newman |
| eventos | GET | `/internal/security/readiness` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | GET | `/reservas` | CLIENTE | 401/401 | Flujo funcional Newman |
| inventario | POST | `/reservas` | CLIENTE | 401/401 | Flujo funcional Newman |
| inventario | POST | `/reservas/{id}/liberar` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | POST | `/reservas/{id}/confirmar` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | POST | `/ocupaciones/liberar` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | GET | `/internal/reservas/{id}/devoluciones` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | POST | `/internal/reservas/{id}/devoluciones` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | POST | `/internal/reservas/{id}/compensar` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | POST | `/internal/eventos/{id}/suspender` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | POST | `/internal/eventos/{id}/publicar` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | POST | `/internal/eventos/{id}/habilitar` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | POST | `/eventos/{idEvento}/inventario` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | POST | `/eventos/{idEvento}/cancelar-inventario` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | GET | `/reservas/{id}` | CLIENTE | 401/401 | Flujo funcional Newman |
| inventario | GET | `/disponibilidad/eventos/{idEvento}/sectores/{idSector}` | ANONIMO | 200/200 | Flujo funcional Newman |
| inventario | POST | `/colas/eventos/{evento}` | CLIENTE | 401/401 | Flujo funcional Newman |
| inventario | PUT | `/colas/eventos/{evento}` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| inventario | GET | `/colas/eventos/{evento}/mi-turno` | CLIENTE | 401/401 | Flujo funcional Newman |
| inventario | DELETE | `/colas/eventos/{evento}/mi-turno` | CLIENTE | 401/401 | Flujo funcional Newman |
| inventario | GET | `/internal/reports/events/{eventId}/inventory` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| inventario | GET | `/internal/security/readiness` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| compras | GET | `/pagos` | CLIENTE|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | POST | `/pagos` | CLIENTE | 401/401 | Flujo funcional Newman |
| compras | POST | `/devoluciones` | CLIENTE | 401/401 | Flujo funcional Newman |
| compras | GET | `/compras` | CLIENTE|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | POST | `/compras` | CLIENTE | 401/401 | Flujo funcional Newman |
| compras | POST | `/compras/{id}/cancelar` | CLIENTE | 401/401 | Flujo funcional Newman |
| compras | POST | `/admin/eventos/{id}/cancelacion` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| compras | POST | `/admin/devoluciones/{id}/rechazar` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | POST | `/admin/devoluciones/{id}/procesar` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | POST | `/admin/devoluciones/{id}/aprobar` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | GET | `/pagos/{id}` | CLIENTE|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | GET | `/internal/orders/{id}` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| compras | GET | `/devoluciones/{id}` | CLIENTE|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | GET | `/compras/{id}` | CLIENTE|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | GET | `/compras/{id}/estado` | CLIENTE|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | GET | `/compras/{id}/entradas` | CLIENTE|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | GET | `/compras/{id}/detalle` | CLIENTE|ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | GET | `/admin/reintegros/{id}` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | GET | `/admin/devoluciones` | ADMINISTRADOR | 401/401 | Respuesta positiva del proveedor + probe Newman |
| compras | GET | `/admin/devoluciones/{id}/procesamiento` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| compras | GET | `/api/v1/me/notifications` | CLIENTE | 401/401 | Flujo funcional Newman |
| compras | GET | `/api/v1/me/notifications/stream` | CLIENTE | 401/401 | SSE autenticado mediante helper de streaming |
| compras | GET | `/api/v1/me/notifications/unread-count` | CLIENTE | 401/401 | Flujo funcional Newman |
| compras | PUT | `/api/v1/me/notifications/{id}/read` | CLIENTE | 401/401 | Flujo funcional Newman |
| compras | GET | `/internal/recommendations/customers/{owner}/history` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| compras | GET | `/internal/reports/events/{eventId}/sales` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| compras | POST | `/internal/notifications/events` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| compras | GET | `/internal/security/readiness` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| entradas | GET | `/tickets` | CLIENTE | 401/401 | Flujo funcional Newman |
| entradas | POST | `/internal/tickets/issue` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| entradas | PATCH | `/internal/tickets/{ticketId}/status` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| entradas | PATCH | `/internal/tickets/status` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| entradas | POST | `/internal/tickets/orders/{id}/annul` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| entradas | POST | `/internal/events/{id}/cancel-emission` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| entradas | POST | `/accesos/eventos/{event}/validaciones` | PERSONAL_ACCESO | 401/401 | Flujo funcional Newman |
| entradas | PUT | `/admin/accesos/eventos/{event}/personal/{staff}` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| entradas | DELETE | `/admin/accesos/eventos/{event}/personal/{staff}` | ADMINISTRADOR | 401/401 | Flujo funcional Newman |
| entradas | GET | `/internal/reports/events/{eventId}/tickets` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| entradas | GET | `/internal/security/readiness` | OAUTH2_TECNICO | 403/403 | Respuesta positiva del proveedor + probe Newman |
| identidad | POST | `/auth/register` | ANONIMO | 400/400 | Flujo funcional Newman |
| identidad | POST | `/auth/login` | ANONIMO | 400/400 | Flujo funcional Newman |
| identidad | GET | `/auth/me` | CLIENTE|ORGANIZADOR|ADMINISTRADOR|PERSONAL_ACCESO | 401/401 | Flujo funcional Newman |

Las continuaciones complementarias aportaron 52 solicitudes aprobadas; se excluyeron los casos fallidos de fixtures, cuyos reportes originales se conservan.

Los resultados detallados y referencias están en `openfeign-cobertura.json`. Los archivos privados de target no se versionan. Las rutas que solo tienen negativa HTTP no se declaran probadas con éxito comercial. Las 24 operaciones Feign tienen además pruebas wire focalizadas; los siete enlaces se ejercitaron realmente en Docker.
