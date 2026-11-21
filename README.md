# TicketYa — Entrega 2

Plataforma de eventos de Arquitectura Web (TUDAI, UNICEN, 2026). Seis aplicaciones Spring Boot independientes: Gateway, Identidad, Eventos, Inventario/Reservas, Compras/Pagos y Entradas. Cinco esquemas MySQL separados; MongoDB se usa solo para preferencias en Eventos. Pasarela de pagos simulada. React consume exclusivamente Gateway.

## Ejecutar desde un clon nuevo en aislamiento

Requisitos: Docker Desktop con contenedores Linux operativo, Git, Node 22.12+ y acceso inicial a Docker Hub, Maven Central y npm. No se requiere Java/Maven en el host: las imágenes compilan con Maven/JDK 21, conservando Java 17 en Eventos e Inventario. Cada proyecto tiene su propio POM; no hay reactor raíz.

Desde la raíz:

~~~powershell
node TicketYa/integration-tests/e2/environment.cjs --prepare --all
npm --prefix TicketYa/frontend ci
# Linux: instalar Chromium (ver guía); Windows: Edge instalado por defecto.
node TicketYa/integration-tests/e2/environment.cjs --all --frontend
node TicketYa/integration-tests/e2/run-security.cjs --centralized --notifications --audit --load --frontend --port 48084
~~~

La preparación descarga MySQL 8.4.8, MongoDB 8.0.16 y la imagen Node de las sondas, y construye las seis imágenes Java y el colector **solo si faltan**. No inicia contenedores. Si cambiaron fuentes de una imagen existente, reconstruirla explícitamente; `build-images.cjs` reconstruye los seis servicios. --centralized junto con --notifications/--reports activa nueve servicios Docker: seis aplicaciones Java, MySQL, MongoDB y colector. React se ejecuta en el host. No combinar --commercial-only con --audit o --centralized: el runner lo rechaza antes de crear recursos. El runner genera un único Compose aislado nuevo, MySQL y Mongo propios, claves RSA y credenciales aleatorias, primer administrador, organizadores, personal y siete clientes técnicos. Valida topología, permisos, readiness y rutas antes de probar. No lee .env ni utiliza recursos persistentes; conserva sus contenedores y volúmenes para auditoría. No usar Entrega1IT sobre estos datos.

Los puertos loopback 48084 (Gateway), 38228 (Forward) y 5174 (navegador) deben estar libres. El destino Forward tampoco puede pertenecer a un contenedor anterior, aunque esté detenido. Ante un puerto ocupado elegir otro --port 48080–48089; no borrar el entorno anterior. El runner elige otra identidad canónica si la subred aleatoria colisiona, antes de crear recursos; si no encuentra una libre, falla. El navegador predeterminado es Edge en Windows y Chromium de Playwright en Linux. Para otro navegador instalado ajustar TY_BROWSER_CHANNEL; Playwright permite instalar Chromium con npx playwright install chromium desde TicketYa/frontend y usar el canal chromium. No se modifica el motor Docker ni se limpian recursos. El runner elimina variables COMPOSE_* heredadas del proceso hijo y evita leer el .env de desarrollo. No arrancar servicios persistentes ni ejecutar dos campañas simultáneas.

[Guía de portabilidad: demo, pruebas, preparación y diagnóstico](docs/entrega2/portabilidad-entrega.md). Incluye el runner reproducible de las pruebas documentales y la limpieza por proyecto exacto. [Demo preparada mediante APIs](demo/README.md): ocho servicios aislados y frontend en 5190; no requiere activar el Compose persistente.

La salida final informa proyecto, run y resultados. El Compose generado se conserva en TicketYa/integration-tests/target/security/<run>/compose.json. Los secretos operativos de prueba están en ese directorio protegido e ignorado, nunca en este README ni en Git. No compartirlo como entregable. El manifiesto referencia secretos del proceso; no se promete reanudarlo con un compose up manual sin recuperar su entorno protegido. Para una instalación nueva reproducible ejecutar el runner completo. JSON es válido como YAML: Docker Compose consume ese único manifiesto mediante --file; no es necesario combinar varios archivos. La campaña crea datos de prueba mediante las APIs reales y nunca simula éxito en la UI.

## Usar el frontend

Con el Gateway aislado operativo, en otra terminal:

~~~powershell
$env:TICKETYA_GATEWAY_URL = 'http://127.0.0.1:48084'
Set-Location TicketYa/frontend
npm run dev
~~~

Abrir http://127.0.0.1:5173. Se puede registrar un CLIENTE desde la UI. Los roles privilegiados requieren el provisionamiento operativo existente, no se conceden por registro público. JWT en memoria: recargar exige login. El proxy Vite /gateway conserva bearer y SSE; no usa credenciales OAuth2 técnicas en el navegador. Para revisar el build: npm run build y npm run preview en ese directorio. No arrancar dos procesos en el mismo puerto.

[Frontend: pantallas, pruebas y límites](docs/entrega2/frontend-react.md).

## Componentes y responsabilidades

| Servicio | Puerto interno | Propiedad de datos |
|---|---|---|
| Gateway | 8080 | Rutas, JWT, correlación y presupuesto HTTP; sin BD |
| Identidad | 8085 | Usuarios, roles, hashes y clientes OAuth2; MySQL propio |
| Eventos | 8081 | Eventos, participantes, lugares, sectores y precios; MySQL. Preferencias en MongoDB |
| Inventario | 8082 | Aforo, asientos operativos, reservas, expiración y FIFO; MySQL |
| Compras | 8084 | Compra, pago simulado, reintegros, compensación y notificaciones/outbox; MySQL |
| Entradas | 8083 | Tickets, emisión idempotente, asignación de personal y acceso QR único; MySQL |
| MySQL / MongoDB | 3306 / 27017 | Sin publicación en host en la topología aislada |

Solo Gateway publica HTTP, limitado a loopback. Las lecturas y escrituras técnicas usan REST/OAuth2 entre servicios; nunca se consulta una base ajena. Los reportes distribuidos no constituyen un snapshot atómico. La recuperación usa los identificadores y autoridad originales; un timeout no implica rechazo financiero.

## Despliegue raíz y datos históricos

[Docker Compose raíz](docker-compose.yml) y [.env.example](.env.example) son la plantilla de despliegue. Identidad es optativa mediante --profile identidad y requiere su esquema/usuario, claves externas persistentes y provisionamiento previo; las siete credenciales técnicas deben coincidir con los hashes registrados. Mongo requiere dos contraseñas y un volumen propio. El generador generate-docker-env.ps1 prepara solo las cinco contraseñas MySQL originales: no genera automáticamente las adicionales de Identidad, OAuth2 o Mongo.

**No ejecutar un up genérico sobre ticketya-e2-infra.** Sus recursos históricos no deben activarse, recrearse ni migrarse como parte de las pruebas. La creación/adaptación del esquema de Identidad en ese entorno y la activación de nuevas imágenes requieren autorización separada. No cambiar contraseñas de .env esperando que roten usuarios existentes. Los inicializadores solo corren en volúmenes vacíos. No usar down -v, prune ni eliminar recursos como preparación de pruebas.

La ruta reproducible probada desde cero es el Compose nuevo del runner anterior. La plantilla raíz se valida estáticamente sin activar datos persistentes. El colector Fluent Bit existe en la topología optativa --centralized. El runner admite combinarla con los flags de Mongo y genera un único Compose de nueve servicios. La prueba mínima previa de coexistencia se amplió con la campaña de correcciones de nueve servicios descrita abajo. La campaña de correcciones verificó nueve servicios simultáneos con notificaciones/recomendaciones, negocio, carga y navegador; ver [auditoría documental](docs/entrega2/auditoria-documental.md). Los casos FIFO/QR backend/reportes/caché/fallos mantienen su evidencia previa de la campaña integral de ocho. Para ampliarlos en una corrida nueva agregar --queue --qr --reports --cache --faults, sin asumir que esa combinación completa de nueve fue reejecutada. El Compose raíz conserva su plantilla de ocho servicios y no activa el colector por defecto. La entrega centralizada sigue siendo de mejor esfuerzo.

## API, seguridad y documentación

- [Colección Postman de referencia](docs/entrega2/postman-coleccion.json): completar variables y ejecutar por solicitud/rol, nunca toda la colección sobre datos persistentes. No exportar valores de JWT/contraseñas.
- [Contrato OpenAPI público E2](docs/entrega2/openapi-publico.json): JWT, roles, rutas de los bloques nuevos y contratos base actualizados. La cancelación financiera técnica y /internal no se exponen por Gateway. /oauth2/token es técnico, no un endpoint del navegador.
- [Matriz final, arquitectura y limitaciones](docs/entrega2/entrega-final.md).
- [Auditoría y matriz documental E2](docs/entrega2/auditoria-documental.md), [historias y cambios E2](docs/entrega2/historias-e2.md).
- [Regresión integral y carga](docs/entrega2/regresion-final.md).
- [Identidad y claves](docs/entrega2/identidad.md), [JWT/propiedad](docs/entrega2/autorizacion-propiedad.md), [Docker y aislamiento](docs/entrega2/docker-regresion.md).
- [FIFO](docs/entrega2/cola-virtual.md), [QR](docs/entrega2/validacion-qr.md), [Mongo/recomendaciones](docs/entrega2/mongodb-recomendaciones.md), [notificaciones](docs/entrega2/notificaciones.md), [reportes](docs/entrega2/reportes-organizadores.md), [caché](docs/entrega2/cache-catalogo.md).
- [Trazabilidad](docs/entrega2/trazabilidad.md), [auditoría](docs/entrega2/auditoria-comercial.md), [centralización](docs/entrega2/centralizacion-logs.md), [resiliencia](docs/entrega2/bloque4-resiliencia.md).

El encabezado X-Admin-Id o un ID declarado no otorgan autoridad: JWT validado, rol y propiedad son obligatorios. No hay credenciales de demostración versionadas. El detalle completo de evento exige sesión; el visitante usa búsqueda y datos públicos de lugar.

## Pruebas y antecedentes

~~~powershell
node --test TicketYa/integration-tests/e2/*.test.cjs
npm --prefix TicketYa/frontend test
npm --prefix TicketYa/frontend run build
~~~

Las pruebas JUnit se ejecutan por proyecto con los perfiles/guardas existentes, exclusivamente en sus bases aisladas; ver cada guía. Nunca apuntar create-drop, limpieza de repositorios ni runners antiguos al volumen histórico. Dockerfiles omiten tests durante el empaquetado: compilar una imagen no acredita JUnit.

La [Entrega 1](docs/entrega1/README.md) y sus colecciones se conservan como antecedentes; sus encabezados de identidad, puertos y ejemplos sin JWT no representan la API E2 actual. La consigna oficial permite simplificar FIFO y recomendaciones; no se afirma haber demostrado decenas de miles de compradores con la campaña acotada de entrega.

El modo con colector requiere su imagen existente; en un clon nuevo construirla con `docker build --progress=plain --tag ticketya-e2-logs:dev docker/observability` siguiendo la guía de centralización.

Para verificar solo coexistencia sin repetir operaciones comerciales: `node TicketYa/integration-tests/e2/verify-coexistence.cjs`. El modo conjunto usa puertos loopback 38224–38233 y subredes explícitas comprobadas; no modifica exclusiones de Windows. Para generar la campaña con colector y Mongo, agregar `--centralized` a los flags de la campaña integral. En ese modo el colector permanece activo durante los reinicios de dependencias; la caída del colector conserva su prueba histórica separada.
