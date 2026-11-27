# Reentrega: comunicación de negocio mediante OpenFeign

Spring Boot 4.1.2 y los niveles Java 17/21 se conservan. Eventos, Compras y Entradas importan el BOM Spring Cloud 2025.1.4, que administra OpenFeign 5.0.4. Cada consumidor habilita explícitamente sus interfaces con `@EnableFeignClients`.

## Clientes reales

| Consumidor | Interfaces Feign | Proveedores |
|---|---|---|
| Eventos | EventosInventarioFeignClient, EventosEntradasFeignClient, EventosComprasFeignClient | Inventario, Entradas y Compras |
| Compras | ComprasEventosFeignClient, ComprasInventarioFeignClient, ComprasEntradasFeignClient | Eventos, Inventario y Entradas |
| Entradas | EntradasComprasFeignClient | Compras |

Las siete interfaces contienen 24 operaciones HTTP. Los adaptadores existentes conservan validación de respuestas, propiedad, clasificación por contrato y recuperación. Sus constructores Spring reciben los clientes Feign. Los constructores programáticos se conservan para fixtures aislados y también ejecutan Feign; no constituyen un transporte RestClient alternativo.

En Compras, los servicios dependen de `EventoClient`, `ReservaClient` y `EntradaClient`: sus implementaciones en el perfil `integracion` son `FeignEventoAdapter`, `FeignReservaAdapter` y `FeignEntradaAdapter`. Estas invocan `ComprasEventosFeignClient`, `ComprasInventarioFeignClient` y `ComprasEntradasFeignClient`. En Entradas, `OrderClient` se implementa mediante `FeignComprasOrderAdapter`, que invoca `EntradasComprasFeignClient`; sus pruebas inyectan Feign por el mismo constructor que Spring, sin builders RestClient descartados. En Eventos, EventWorkflow y el controller de reportes invocan Feign directamente; el historial conserva PurchaseHistoryClient como adaptador específico.

El patrón es **servicio → interfaz de negocio → adaptador → proxy `@FeignClient` → HTTP**. El adaptador valida y convierte DTO, aplica resiliencia y traduce errores; no sustituye Feign ni agrega otra llamada de red. `RemoteCallSupport` clasifica fallos según el contrato de la operación y no es un cliente HTTP. `OAuth2RestClientConfiguration` en Entradas personaliza el RestClient legítimo utilizado para adquirir tokens técnicos; conserva el bean `technicalClient`. Las excepciones Spring `RestClientException`/`RestClientResponseException` mantienen la clasificación existente y su nombre no indica un transporte RestClient de negocio.

`FeignSupport` implementa el contrato de transporte `feign.Client` sobre el request factory HC5 existente. No ejecuta RestClient. Conserva pools, límites de tiempo por solicitud, lectura de cuerpos, OAuth2 y correlación. El decoder y la capability preservan las categorías que consumen los servicios y circuitos. La respuesta vacía/inválida nunca confirma una operación.

`Retryer.NEVER_RETRY`, retries HC5 desactivados, sin redirecciones ni fallback comercial. Los circuitos/bulkheads por familia siguen rodeando los adaptadores; no se agrega un circuito global duplicado. Se mantienen IDs originales, scopes, auditoría post-commit y recuperación explícita. Logging Feign de cuerpos/headers permanece desactivado; Micrometer observa las llamadas reales.

Gateway conserva WebFlux y SSE. Identidad emite tokens y no consume negocio. Inventario es proveedor. Spring Security conserva el cliente estándar de adquisición OAuth2, su caché, renovación single-flight, circuitos y límites. Ninguno recibe Feign artificialmente.

## Construcción y entorno aislado

Requisitos: Docker operativo, Node 22.12+ y recursos suficientes. No utilizar la demo histórica como destino de pruebas.

```powershell
node TicketYa/integration-tests/e2/build-feign-images.cjs
# Copiar la etiqueta feign-xxxxxxxx informada; también queda en target/openfeign/images.json.
$env:TY_FEIGN_IMAGE_TAG = 'feign-xxxxxxxx'
npm install --prefix TicketYa/integration-tests/target/openfeign/newman --no-audit --no-fund newman@6.2.1
node TicketYa/integration-tests/e2/run-security.cjs --newman --queue --qr --notifications --reports --cache --audit --faults --load --frontend --port 48088
```

El builder utiliza etiquetas propias y hashes del código, sin sobrescribir `:dev`. Reutiliza imágenes propias únicamente si su etiqueta de procedencia coincide. El runner crea proyecto, claves, credenciales, red, MySQL y MongoDB nuevos; solo publica Gateway en loopback. El puerto debe estar libre. Los contenedores históricos se acreditan antes/después. No se ejecutan `down`, `prune` ni borrado de volúmenes. Los recursos nuevos se conservan para revisión.

## Postman y cobertura

Importar `postman-openfeign.json` y la plantilla `postman-openfeign.environment.json`. No versionar ambientes completos con credenciales.

- **Flujo completo reproducible:** registro, logins reales, catálogo, publicación, reserva, pago, replay, emisión, devolución parcial, reintegro, QR, reporte, cancelación, rechazo, recuperación incierta y preferencias. Obtiene IDs desde respuestas. Requiere cuentas privilegiadas ficticias provisionadas con el mecanismo CLI existente y sus variables de entorno privadas. No existe registro público de administradores.
- **Carpetas por servicio:** inventario de las 87 operaciones explícitas de controllers, para ejecutar individualmente con IDs/estado preparados según OpenAPI.
- **OAuth2 técnico:** siete adquisiciones con scopes existentes. Solo desde la red de prueba autorizada; los endpoints internos no se publican en Gateway.

El runner `--newman` ejecuta realmente Newman para las solicitudes comerciales, conservando las verificaciones de estados/SQL del runner. Además ejecuta probes de las 87 operaciones y adquisiciones/readiness técnicas desde un contenedor de pruebas dentro de la red. Los probes de autorización no se presentan como éxitos comerciales. El flujo autónomo de la colección se ejecuta por separado sobre datos nuevos del mismo entorno aislado.

Para Postman Desktop, completar el ambiente local privado con `gatewayUrl` y las tres cuentas privilegiadas ficticias; ejecutar **solo** la carpeta «Flujo completo reproducible» mediante Collection Runner, deteniéndose ante errores. El cliente se registra automáticamente. Las carpetas de inventario necesitan IDs y estado preparados; no son una secuencia comercial ejecutable de principio a fin. La carpeta complementaria requiere haber completado el flujo y ejecutarse dentro de la red aislada: utiliza OAuth2 propio y nombres DNS internos, sin publicar esos endpoints.

Equivalente Newman para el flujo público (el ambiente privado queda fuera de Git):

```powershell
node TicketYa/integration-tests/target/openfeign/newman/node_modules/newman/bin/newman.js run docs/entrega2/postman-openfeign.json -e C:/privado/ticketya-pruebas.json --folder "Flujo completo reproducible" --bail --ignore-redirects --timeout-request 10000
```

No ejecutar toda la colección a ciegas sobre la demo. Los scripts obtienen tokens e IDs; los pagos y reintegros repetidos conservan sus claves originales. Para una nueva corrida usar un ambiente/colección sin variables de ejecución anteriores.

La prueba SSE autenticada recibe eventos y cierra la conexión de forma acotada mediante el helper de streaming existente. No se atribuye a Newman una validación de contenido SSE que no realizó. Las rutas de Springdoc y del authorization server no se confunden con controllers de negocio; la emisión OAuth2 se prueba explícitamente.

Reportes privados bajo `TicketYa/integration-tests/target/security/<corrida>/`: campaña, Newman comercial, probes de controllers y flujo autónomo. Contienen IDs ficticios y resultados; nunca deben incorporarse como ambientes con tokens. La matriz de cobertura distingue negocio, negativas y streaming complementario.

## Defensa breve

1. Abrir las siete interfaces en el paquete `feign` de los tres consumidores; mostrar anotación, método y URL configurable.
2. Mostrar su inyección directa en EventWorkflow y su inyección a través de FeignEventoAdapter, FeignReservaAdapter, FeignEntradaAdapter y FeignComprasOrderAdapter. Explicar que son adaptadores con responsabilidades de validación/resiliencia, no clientes RestClient. Las cadenas `RestClient.get/post/...` de negocio ya no existen.
3. Ejecutar en Postman publicación: Gateway → EventController → EventWorkflow → EventosInventarioFeignClient → InventarioController.
4. Ejecutar reserva/compra/pago: Compras consulta Eventos e Inventario; confirma reserva y emite mediante ComprasEntradasFeignClient; Entradas consulta la orden mediante EntradasComprasFeignClient.
5. Mostrar correlación/traza de las respuestas y logs de ambos servicios. Mostrar las pruebas de proxies Spring y las imágenes concretas del reporte, no solamente anotaciones.
6. Mostrar reporte, recomendaciones y cancelación para completar los tres enlaces de Eventos. Avisos del outbox usan EventosComprasFeignClient.
7. Mostrar por Postman lo que React no ofrece: administración de devoluciones/cancelación, configuración de cola y asignación de personal.

No mostrar JWT, secretos técnicos ni claves privadas en consola o durante la defensa. No repetir escrituras automáticamente cuando el resultado sea incierto.

Mapa para seguir una llamada concreta en el código (muestras de los siete enlaces):

| Interfaz | Origen público / componente | Controller destino y operación interna |
|---|---|---|
| EventosInventarioFeignClient | EventController → EventWorkflow, publicar | InventarioController: `POST /eventos/{id}/inventario` y publicación interna |
| EventosEntradasFeignClient | EventController → EventWorkflow, cancelar | EventEmissionController: `POST /internal/events/{id}/cancel-emission` |
| EventosComprasFeignClient | EventController → EventWorkflow, cancelar | DevolucionAdminController: `POST /admin/eventos/{id}/cancelacion` |
| ComprasEventosFeignClient | CompraController → servicio de compra | EventController: `GET /api/v1/events/{id}` |
| ComprasInventarioFeignClient | PagoController → PagoService | ReservaController: `POST /reservas/{id}/confirmar` |
| ComprasEntradasFeignClient | PagoController → PagoService | TicketController: `POST /internal/tickets/issue` |
| EntradasComprasFeignClient | TicketController → servicio de emisión | OrdenInternaController: `GET /internal/orders/{id}` |

Para acreditar la ejecución, obtener `X-Correlation-Id` de la respuesta pública y buscar ese valor en ambos servicios, por ejemplo:

```powershell
docker logs --since 10m ticketya-e2-reg-b2a0b652b0337244-eventos-1 2>&1 | Select-String -SimpleMatch '<correlationId>'
docker logs --since 10m ticketya-e2-reg-b2a0b652b0337244-inventario-1 2>&1 | Select-String -SimpleMatch '<correlationId>'
docker inspect --format '{{.Config.Image}} {{.Image}}' ticketya-e2-reg-b2a0b652b0337244-eventos-1
```

Los nombres pertenecen a esta corrida; en otra usar los emitidos por el runner. Un log de controller demuestra el destino; los proxies Spring, la interfaz inyectada y los JAR de la imagen acreditan que el transporte de esa llamada es Feign.

## Evidencias de esta migración

Validación ejecutada en `ticketya-e2-reg-b2a0b652b0337244`, Gateway `127.0.0.1:48088`. Los resultados están bajo `TicketYa/integration-tests/target/`; están ignorados, conservados localmente y no forman parte de los contratos públicos.

| Evidencia ejecutada | Resultado |
|---|---|
| JUnit de los tres consumidores y contratos documentales | 739 pruebas distintas aprobadas, cero fallos/omitidas: Eventos 156, Compras 455, Entradas 122, integración 6; XML recuperados y deduplicados por clase y nombre |
| Contratos wire Feign | 24 operaciones HTTP reales hacia servidor de prueba; siete proxies y configuración Spring comprobados |
| Node | 50 pruebas de aislamiento/configuración, cinco de Feign/colección y dos del transporte Newman: 57 distintas aprobadas |
| Frontend | 62 unitarias y compilación aprobadas; navegador real con QR y recuperación del intento original |
| Seguridad distribuida | 93 comprobaciones aprobadas, JWT y siete enlaces OAuth2 |
| Comercio mediante Newman | 455 solicitudes de la campaña; flujo autónomo posterior de 50 solicitudes/78 aserciones aprobado |
| Cobertura adicional | 87 operaciones de controllers con caso HTTP; 7 tokens técnicos, 4 readiness y 2 OpenAPI; 52 solicitudes complementarias aprobadas, incluidas las válidas de continuaciones parciales |
| Trazabilidad/auditoría | 1357 registros HTTP, 406 solicitudes distribuidas, 570 eventos de auditoría; las cifras corresponden al corte anterior a los flujos complementarios |
| Fallos y carga acotada | Ráfaga de 24: 8 aceptadas y 16 rechazos técnicos; circuito OPEN → HALF_OPEN → CLOSED. Carga: concurrencia 12, 34 solicitudes, 24 éxitos, 10 conflictos esperados, cero errores inesperados; p95 474,20 ms |
| Integridad | Cero pagos/lotes duplicados; totales al cierre de flujos financieros: 9 pagos, 4 reintegros, 7 lotes, 9 tickets. Los complementarios no cambiaron pagos, reintegros ni tickets |

La campaña original conserva **FAIL**: el comando padre agotó su timeout al esperar los probes, aunque el worker terminó. No fue una caída del motor. Dos expectativas de probes PUT/DELETE sin JWT se corrigieron de 404 a 401 y se verificaron de forma focalizada. Las continuaciones completaron trazabilidad, auditoría, carga, frontend y Postman sin repetir los 455 pedidos comerciales. También se conservan fallos iniciales de fixtures/scripts de prueba; las correcciones se verificaron solo desde el punto pendiente. `openfeign-final-results.json` consolida las evidencias, no sustituye ni reescribe los reportes originales.

Se construyeron seis etiquetas nuevas `feign-f4532c65`, sin sobrescribir imágenes históricas. Los tres consumidores ejecutan esas imágenes y sus JAR contienen OpenFeign 5.0.4/Feign 13.6.1 y las siete interfaces compiladas (`runtime-feign-jars.json`). Gateway e Identidad también ejecutan las etiquetas nuevas. Inventario, cuyo código no cambió, ejecutó su imagen `:dev` existente; la corrección final del selector asegura que futuras campañas opt-in utilicen la etiqueta aislada para los cinco componentes. No se reinició esta campaña solo para cambiar una imagen de proveedor sin cambios.

Los ocho componentes del entorno permanecen running, sin reinicios; MySQL/Mongo healthy. Los 88 contenedores anteriores a esta tarea no se iniciaron ni detuvieron; el volumen `ticketya-e2-infra_mysql_data` se conserva y no se consultó su base. Los recursos nuevos fallidos/de pruebas se conservaron. No hubo commit ni push.

Limitaciones: el colector histórico no se redeplegó; se verificaron ECS, trazas y auditoría del entorno aislado. SSE usa el helper finito, no el lector de Newman. El modo automático futuro que concatena flujo y complementarios se comprobó por composición/sintaxis; sus escenarios se ejecutaron realmente en continuaciones separadas, sin repetir pagos para producir un único reporte verde. Ver la [matriz de cobertura](openfeign-cobertura.md) para distinguir casos positivos, proveedor y streaming.

## Verificación del cambio de nomenclatura (27/11/2026)

Se compilaron los tres consumidores y se ejecutaron 179 JUnit focalizadas: Compras 136, Entradas 38 y Eventos 5; cero fallos, errores u omisiones. Incluyen los contratos wire de las 24 operaciones Feign, resolución de los adaptadores por tipo/puerto y nuevo nombre de bean, ausencia de los beans históricos, clasificación de fallos, emisión, recuperación y trazabilidad OAuth2. Cinco pruebas Node de la colección/OpenFeign también aprobaron. Los XML quedaron en `TicketYa/integration-tests/target/openfeign-adapters-20261127/`, ignorados por Git. No se repitió la campaña comercial ni se reconstruyeron imágenes. Los contenedores Maven fueron nuevos, sin red y con H2 efímero; no se tocaron aplicaciones ni datos existentes.
