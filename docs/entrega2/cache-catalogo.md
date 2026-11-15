# Caché del catálogo — Bloque 10

La consigna exige justificar datos, ubicación, TTL, invalidación y exclusiones; no impone Redis ni otra tecnología. Se utiliza Spring Cache programáticamente con Caffeine local, con versiones administradas por Spring Boot 4.1.2. [Soporte oficial](https://docs.spring.io/spring-boot/reference/io/caching.html).

## Consultas y límites

En Eventos se cachean únicamente GET /api/v1/participants y GET /api/v1/participants/{id}: ID, nombre y categoría artística, que son datos públicos iguales para todos los usuarios. Cada respuesta recibe DTO nuevos a partir de snapshots inmutables. No se cachean errores ni inexistencias.

| Configuración | Valor por defecto | Límite permitido |
| --- | --- | --- |
| TY_CATALOGUE_CACHE_TTL_SECONDS | 30 segundos desde la carga | 1–300 |
| TY_CATALOGUE_CACHE_MAX_PARTICIPANTS | 512 detalles | 1–10000 |
| TY_CATALOGUE_CACHE_LIST_MAX_ITEMS | 200 participantes retenidos en el listado | 1–1000 |

El listado conserva todos sus resultados en la API. Si supera el límite de peso, no se retiene en memoria. Una lista vacía pesa una unidad. Los límites se aplican por instancia y las configuraciones inválidas impiden arrancar. Caffeine registra hits, misses, cargas y expulsiones; no se agrega un endpoint de métricas ni se expone Actuator.

## Consistencia

Crear, modificar o eliminar participantes invalida ambas cachés después del commit. También lo hacen la creación, publicación, asignación de precios, cancelación y los cambios efectivos de estado de eventos. Un rollback conserva el snapshot comprometido. Las claves incluyen una generación local: una carga anterior que termina tarde no puede reponer datos visibles en la generación actual. Las lecturas concurrentes de una clave comparten su carga.

Una lectura dentro de una transacción de escritura evita la caché y no publica datos sin confirmar. Las cargas normales usan una transacción de lectura nueva para no conservar un snapshot viejo de una transacción exterior. Reiniciar la instancia vacía la caché; MySQL sigue siendo la fuente de verdad.

No se cachean eventos ni búsquedas que incluyan precios o estado; tampoco disponibilidad, reservas, cola FIFO, QR, preferencias privadas, compras, pagos, reintegros o reportes. Las autorizaciones y validaciones comerciales siguen consultando sus fuentes actuales. La caché nunca habilita una operación comercial.

La invalidación es local. Con varias instancias, una modificación recibida por otra instancia puede tardar hasta el TTL en verse en estos datos públicos auxiliares. No se ofrece consistencia distribuida inmediata. Los listados grandes siguen consultando MySQL; no se alteró la paginación ni el contrato existente.

## Verificación reproducible

- CatalogueCacheTest: miss/hit, DTO independientes, TTL con reloj de prueba, invalidación CRUD, rollback, concurrencia (24 consultas/8 hilos), límites y gestor nuevo con la misma base H2.
- CacheInvalidationRaceTest: carga antigua terminada después de invalidar.
- PublicCatalogueHttpTest: público/CLIENTE, rechazo de escritura, borrador ajeno oculto y 404 conservado.
- EventServiceImplTest: publicación, cancelación y cambios de estado notifican la invalidación.
- Guardas Node de seguridad y topología Mongo existente.
- Campaña mínima con JWT real: node TicketYa/integration-tests/e2/run-security.cjs --cache --commercial-only --port 48080. Crea un proyecto aislado nuevo con MySQL y Mongo propios; verifica consultas públicas, cambios de participantes, denegación al cliente y el circuito comercial existente con controles de duplicados. No utiliza bases persistentes.

Las evidencias de ejecución se guardan fuera de Git en integration-tests/target/cache y target/security. No se requieren cambios en Compose persistente ni nuevas imágenes salvo Eventos.

Resultado de esta ejecución: 29 pruebas JUnit distintas aprobadas y 9 guardas Node aprobadas. Se detectó y corrigió la ausencia del hook de invalidación en cancelación; las 11 pruebas del servicio afectado se reejecutaron correctamente. La campaña ticketya-e2-reg-8c774520f5a988ed aprobó 204 solicitudes HTTP, incluyendo las comprobaciones de caché y tres escenarios financieros; no se duplicaron cobros, reintegros ni emisiones.
