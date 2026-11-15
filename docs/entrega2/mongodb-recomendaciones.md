# MongoDB, preferencias y recomendaciones

MongoDB se incorpora únicamente a Eventos: un documento por cliente, con `_id` igual al `sub` validado del JWT. Guarda `favoriteTypes`, `favoriteParticipantIds`, `preferredCity` y `updatedAt`; no almacena tokens, contraseñas ni información financiera. El catálogo y sus relaciones siguen en MySQL, sin migración de datos.

## API

Requieren `CLIENTE`; el propietario se obtiene del JWT, nunca de parámetros del cliente:

- `GET /api/v1/me/preferences`: preferencias actuales o listas vacías si todavía no existen.
- `PUT /api/v1/me/preferences`: reemplaza los campos editables. Ejemplo: `{"favoriteTypes":["recital"],"favoriteParticipantIds":[1],"preferredCity":"Tandil"}`.
- `GET /api/v1/me/recommendations`: hasta diez eventos, con `historyAvailable`, `mode`, `warning` y `events`.

Tipos y ciudad se normalizan a minúsculas; listas sin duplicados, límites de tamaño y participantes existentes. Un upsert atómico sobre el índice único `_id` evita documentos duplicados. Ante actualizaciones concurrentes prevalece la última escritura.

## Ranking e historial

Se consideran exclusivamente eventos futuros `PUBLICADO`. El orden es lexicográfico: cantidad de participantes favoritos, coincidencia de tipo favorito, ciudad y coincidencias de tipos/participantes del historial. Desempate por fecha e ID. Sin señales se devuelven próximos eventos generales.

Eventos consulta `GET /internal/recommendations/customers/{identityOwnerId}/history` de Compras mediante el cliente técnico existente y el nuevo scope mínimo `purchases.history.read`. No reenvía JWT humanos ni consulta la base financiera. El endpoint exige caller Eventos y ese scope. Solo devuelve eventos de compras `COMPLETADA` o `DEVUELTA_PARCIAL`, sin cancelación solicitada y con pago aprobado/parcialmente reintegrado con saldo efectivo positivo. Se excluyen las adquisiciones totalmente devueltas.

Los eventos adquiridos se excluyen cuando hay historial disponible. Ante indisponibilidad de Compras se usan las preferencias y se devuelve `historyAvailable:false`, `warning:"HISTORY_UNAVAILABLE"`; no se presenta la falla como historial vacío. No se reintentan escrituras ni se modifica el circuito financiero.

## Infraestructura y disponibilidad

Compose usa `mongo:8.0.16`, volumen propio `mongo_data`, red privada, sin puerto publicado, healthcheck autenticado, límite de memoria de 768 MiB y caché WiredTiger de 0,25 GiB. Credenciales externas: `TY_E2_MONGO_ROOT_PASSWORD` y `TY_E2_EVENTOS_MONGO_PASSWORD`; para evitar problemas de interpretación usar 64 caracteres hexadecimales independientes. El inicializador crea el usuario de aplicación con `readWrite` exclusivamente en su base durante el primer arranque de un volumen nuevo. Cambiar variables no rota credenciales de un volumen ya inicializado.

Solo Eventos recibe `MONGODB_URI`. Spring Boot 4 utiliza `spring.mongodb.uri` ([documentación oficial](https://docs.spring.io/spring-boot/reference/data/nosql.html)). Las operaciones Mongo tienen tiempos de espera acotados y retries desactivados. Mongo no condiciona el arranque ni las operaciones del catálogo, publicación, compras o pagos. Si está inaccesible, únicamente preferencias/recomendaciones responden 503. No se arranca ni migra el entorno persistente en este bloque.

El cliente técnico Eventos→Compras necesita reprovisionamiento autorizado con el scope de lectura antes de usar la funcionalidad en un entorno existente. El runner aislado lo provisiona automáticamente; el entorno persistente permanece intacto.

## Verificación

Pruebas focalizadas JUnit cubren JWT/propiedad, validación, historial filtrado, permisos mínimos, ranking y degradación. Pruebas Node verifican la topología canónica aislada. Para la prueba HTTP con Mongo y MySQL reales: `node TicketYa/integration-tests/e2/run-security.cjs --recommendations --audit --port 48080`. El runner genera credenciales efímeras, bases y volúmenes propios; conserva sus recursos para inspección. Usa una subred privada explícita `10.203.x.0/24` y rechaza superposiciones con redes Docker existentes, evitando depender del pool automático agotado. Comprueba persistencia tras reinicio, PUT concurrentes, fallas de Mongo/Compras y consistencia financiera. Los resultados y logs se guardan bajo `integration-tests/target/`, fuera de Git.

Resultado de esta ejecución: 122 JUnit de Eventos, 436 de Compras, 27 de Gateway y dos pruebas focalizadas de Identidad; nueve pruebas Node; 93 comprobaciones HTTP de seguridad y 240 solicitudes comerciales. Campaña `1ee0043458523354`: PASS, persistencia Mongo tras reinicio, dos documentos únicos, 323 eventos de auditoría, sin cobros, reintegros ni emisiones duplicados. Se completó el circuito comercial con Mongo detenido.

Limitaciones: ranking académico determinista, sin aprendizaje automático; consulta los candidatos del catálogo en memoria. `PUBLICADO` determina elegibilidad, sin prometer stock actual: la reserva continúa siendo la autoridad de disponibilidad. Durante caída de Compras no es posible excluir con certeza eventos adquiridos. No hay transacción distribuida entre MongoDB y MySQL; las preferencias no afectan invariantes comerciales.
