# Frontend React — Bloque 12

La consigna valora una interfaz que consuma la API por Gateway y demuestre búsqueda, selección de sector/asientos, compra y consulta de entradas. Se agregó `TicketYa/frontend`: React 19.3.1, Vite 8.3.8 y JavaScript, con CSS responsive propio. No se modificó el backend.

## Ejecutar

Requiere Node 22.12+ (se probó con Node 26.13). Desde `TicketYa/frontend`:

```powershell
npm ci
$env:TICKETYA_GATEWAY_URL = 'http://127.0.0.1:48080'
npm run dev
```

Abrir `http://127.0.0.1:5173`. El Gateway debe estar operativo y con JWT configurado. No iniciar el entorno persistente para probar esta interfaz. Puede copiarse `.env.example` a `.env.local` para configurar el destino; no contiene credenciales.

`npm run build` produce `dist/`. `npm run preview` permite comprobar ese build y también configura el proxy. En un despliegue estático real se debe configurar un reverse proxy `/gateway` hacia el Gateway, HTTPS y las políticas de seguridad del servidor. Vite no es un servidor de producción.

`TICKETYA_GATEWAY_URL` se lee únicamente en la configuración del servidor Vite. El navegador usa `/gateway`; el proxy conserva Authorization y SSE. No se requieren cambios CORS ni credenciales OAuth2 en el navegador. No se publica ningún servicio Docker adicional.

## Pantallas y contratos

- **Explorar:** `/api/v1/events/search` con ciudad, tipo y orden; detalle de lugar público. El detalle completo `/api/v1/events/{id}` exige JWT en el backend actual. Un visitante ve la información pública de búsqueda, lugar y precio mínimo; se solicita login para tarifas por sector y reserva. No se abre una ruta protegida para resolverlo.
- **Cuenta:** registro de CLIENTE e inicio de sesión reales con `/auth/register`, `/auth/login`, `/auth/me`. Roles obtenidos del servidor. Los perfiles privilegiados se provisionan por el mecanismo administrativo existente.
- **Evento:** consulta disponibilidad actual, permite seleccionar asientos numerados o cantidad general, crea `/reservas` y `/compras`. Incluye ingreso y consulta manual de fila FIFO cuando el evento la requiere.
- **Compras:** listado propio, estado de la compra y pago con `/pagos`. El total y los estados provienen del backend. No se solicitan datos de tarjeta: se usa la pasarela simulada existente. Las cifras se muestran en unidades monetarias del sistema, sin inventar una moneda.
- **Entradas:** `/tickets?clientId=...`, estados e identificadores QR textuales. No genera imágenes ni expone un token como URL pública.
- **Preferencias/recomendaciones:** GET/PUT `/api/v1/me/preferences` y GET `/api/v1/me/recommendations`. Permite ciudad, tipos y participantes favoritos; indica degradación si falta el historial.
- **Notificaciones:** bandeja de las últimas 100, contador de no leídas, marcado propio y SSE. La reconexión es cada tres segundos; la bandeja REST sigue disponible cuando el stream falla.
- **Organizador:** búsqueda pública filtrada por su ID y apertura de un evento por ID; GET `/api/v1/events/{id}/report`. El backend valida propiedad. No existe API para listar todos los borradores/cancelados propios; no se simula ese listado.
- **Personal de acceso:** POST `/accesos/eventos/{event}/validaciones`, con token textual. Requiere asignación administrativa previa al evento, como exige el backend.

## Seguridad y resultados inciertos

JWT exclusivamente en memoria y encabezado Authorization; se pierde al recargar. Se cierra la sesión al vencer o recibir 401. No hay OAuth2 técnico ni contraseñas persistidas en la aplicación. Las rutas privadas se ocultan y bloquean por rol; el servidor sigue siendo la autoridad de permisos y propiedad.

No hay retries automáticos de escrituras. Los botones bloquean dobles envíos en curso. La clave de idempotencia de pago se guarda en `sessionStorage` separada por cliente y compra, para conservarla durante una consulta/reintento explícito de esa compra. No se confirma éxito por un código HTTP: se consulta el estado persistido de la compra. Una compra PROCESANDO ofrece “Recuperar pago original”: consulta la compra y su único intento remoto y envía un único POST con la misma clave y medio original (null solo para históricos sin dato). No usa una nueva clave del navegador ni envía un importe. Si falta el intento inequívoco, no despacha el POST. El resultado pendiente sigue mostrándose pendiente. Ante una respuesta perdida de reserva/creación, consultar los recursos existentes antes de repetir; la interfaz no reconcilia automáticamente esos pasos.

SSE usa streaming `fetch` con bearer, no `EventSource` ni JWT en query string. Se cierra y aborta al navegar o salir. No se ejecuta HTML recibido del backend ni se registran credenciales. Los errores usan mensajes controlados y, cuando existe, correlation ID para diagnóstico.

## Pruebas

```powershell
npm test
npm run build
# Solo sobre un proyecto aislado identificado y operativo, en el puerto 48080:
$env:TY_FRONTEND_TEST_PROJECT = 'ticketya-e2-reg-<16-hex-del-run>'
npm run test:e2e
```

Playwright es una dependencia exclusiva de desarrollo; usa Edge headless en Windows (`TY_BROWSER_CHANNEL` permite otro canal instalado). Las pruebas rechazan proyectos persistentes, validan la topología aislada existente, sus esquemas JDBC y el puerto del Gateway. Provisionan nuevas cuentas ORGANIZADOR/PERSONAL_ACCESO por CLI y un CLIENTE por registro; crean eventos independientes con capacidad dos y precio 15000. Ningún secreto se versiona. Las propiedades temporales quedan en `frontend/test-results`, ignorado y con ACL restrictiva en Windows. El runner conserva los contenedores nuevos de provisionamiento al terminar; no elimina recursos anteriores.

Las unitarias cubren sesión, 401/403, errores técnicos, resultados inciertos sin retries, claves de pago y recepción SSE fragmentada, incluyendo CRLF entre fragmentos. El navegador comprueba catálogo móvil, autenticación real, reserva/compra/pago/emisión, selección de dos asientos numerados, preferencias MongoDB, recomendaciones, bandeja/SSE, reportes propios y rechazo del personal sin asignación. Los resultados y capturas quedan en `test-results`, fuera de Git; se conserva evidencia financiera y visual también en `integration-tests/target/frontend`.

Resultado: instalación y build aprobados; 13 pruebas unitarias aprobadas; cinco escenarios de navegador en la campaña principal y uno adicional de asientos numerados aprobado de forma focalizada. El reporte se volvió a verificar después de traducir sus métricas. Compra aislada 4 del evento 6: importe 15000, un pago APROBADO y una entrada EMITIDA, sin duplicación. La prueba numerada reservó exactamente dos asientos y verificó disponibilidad cero. No se repitieron suites del backend. Los seis contenedores persistentes permanecieron detenidos. En la campaña original del Bloque 12 solo se verificó el rechazo del personal sin asignación. El Bloque 11 completó la prueba positiva con asignación administrativa, login real en React, aceptación única y rechazos de repetición e inválido; ver regresion-final.md.

## Límites

No incluye administración, creación de eventos por UI, devoluciones, escáner de cámara ni generación gráfica del QR. El control de acceso permite el token textual. La validación positiva de ingreso requiere que un administrador asigne previamente al personal; el frontend no concede ese permiso. Ese caso quedó resuelto en la regresión integral del Bloque 11 con un administrador creado exclusivamente en un proyecto nuevo aislado; no se restablecieron credenciales ni se alteraron permisos persistentes. No reemplaza la regresión financiera del backend ni agrega funcionalidades del Bloque 11.

## Corrección final I2 (19/11/2026)

Diecinueve unitarias y build aprobados. Se agregó payment-recovery.spec.js al modo de entrega: login CLIENTE real, compra PROCESANDO y recuperación explícita sin clave previa en el navegador, exactamente un POST y estado COMPLETADA. Se verificó con GET/SELECT un pago/cobro/lote/ticket por 15000. El chequeo SQL inicial falló por una columna equivocada del runner; se corrigió el JOIN y se repitió solo la lectura, sin otro pago. Ver [auditoría documental](auditoria-documental.md). Para reproducir el escenario junto al negocio/notificaciones y logs: comando completo de la campaña de correcciones en ese documento; genera un entorno nuevo y conserva evidencia privada ignorada.
