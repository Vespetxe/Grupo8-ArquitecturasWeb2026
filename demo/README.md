# Datos ficticios para una demostración independiente

Preparación para la entrega del 22/11/2026. **Todavía no se inició este entorno ni se cargaron estos datos.** Solo se prepararon y verificaron archivos nuevos; la validación integrada con Docker queda para una ejecución autorizada. Ningún archivo del backend, frontend, configuración o runner E2 se modifica.

## Diseño y contratos comprobados

Hay seis aplicaciones Java: Gateway (8080), Identidad (8085), Eventos (8081), Inventario/Reservas (8082), Entradas (8083), Compras/Pagos (8084). La demo añade únicamente sus propias instancias MySQL 8.4.8 y Mongo 8.0.16, sin nuevos servicios de aplicación. Reutiliza las imágenes locales `ticketya-e2-{gateway,identidad,eventos,inventario,entradas,compras}:dev`; no ejecuta los runners E2 ni reconstruye imágenes automáticamente. No requiere otro colector: conserva ECS mediante `docker logs`, con rotación local.

| Propietario | Datos y relaciones utilizadas |
|---|---|
| Identidad / MySQL | Usuarios y roles, hash de contraseñas, siete clientes OAuth2 con secretos distintos. Registro público solo CLIENTE; CLI existente para primer administrador, ORGANIZADOR y PERSONAL_ACCESO. |
| Eventos / MySQL | Lugar → sectores; NUMERADO exige filas × asientos = capacidad; participante ↔ evento; precio por evento/sector; organizador propietario desde JWT. La publicación inicializa/habilita Inventario mediante REST. |
| Eventos / Mongo | Un documento `preferences` por `sub`, campos `favoriteTypes`, `favoriteParticipantIds`, `preferredCity`, `updatedAt`; catálogo sigue en MySQL. |
| Inventario / MySQL | Stock y asientos **operativos**, reserva temporal y selección asociada. Los IDs de asientos operativos se obtienen de disponibilidad; no se infieren de los asientos del catálogo. |
| Compras / MySQL | Compra asociada a reserva exacta, pago original con clave y medio, detalle histórico, devoluciones/reintegros, bandeja de notificaciones. No hay escrituras directas de negocio por SQL. |
| Entradas / MySQL | Lote único por orden, tickets y QR opaco único, asignación de personal. Emisión proviene del pago/orquestación, nunca de inserts del cargador. |

Los IDs numéricos pueden coincidir con los de otro entorno: no son una identidad global. Se capturan de cada respuesta y solo se utilizan dentro del nuevo proyecto. JWT y propiedad siguen vigentes; el navegador no recibe secretos técnicos. Los siete scopes se copian de la política actual y una prueba estática detecta divergencias, incluidos historial, notificaciones y reportes.

## Catálogo preparado

`catalog.cjs`: **18 eventos, ocho lugares, 16 sectores, 12 participantes ficticios y siete usuarios humanos**: un administrador, dos organizadores, un personal de acceso y tres clientes. Se provisionan además siete clientes técnicos OAuth2 y cinco usuarios MySQL restringidos a su esquema; Mongo tiene su administrador y usuario propio de preferencias.

Ciudades: Tandil, Buenos Aires, Mar del Plata, La Plata, Córdoba y Rosario. Categorías: rock nacional, metal, electrónica, pop, música clásica, teatro, stand-up, gastronomía, tecnología y deporte. Son nombres y actuaciones inventados, no anuncios de artistas reales. Fechas: 05/12/2026–13/02/2027, horarios argentinos con offset `-03:00`. La carga rechaza fechas a menos de 72 horas, en lugar de mover fechas o alterar registros automáticamente.

Capacidades: desde un palco privado de dos asientos hasta campos de 1800 personas. Cada evento tiene dos precios diferentes; valores unitarios de 9000–60000, elegidos como referencia en pesos argentinos. **El modelo no almacena código ISO y React muestra “u.m.”**: esta preparación no cambia esa interfaz ni promete moneda ARS persistida.

| Escenario | Operaciones previstas y resultado esperado |
|---|---|
| Risas de Viernes / Ana | Dos asientos del palco, compra/pago 36000, dos tickets EMITIDA; palco agotado, platea disponible. |
| Cuerdas al Atardecer / Bruno | Cinco asientos de una platea íntima de seis, pago 60000, cinco tickets; queda una platea disponible. |
| Sabores de la Ribera / Carla | Dos generales por 28000; solicitud y aprobación administrativa de una devolución de 14000; un ticket anulado, otro vigente; stock recuperado una vez. |
| Pulso de Ciudad / Bruno | Dos VIP por 120000: la pasarela simulada rechaza importes mayores a 100000. Compra FALLIDA, reserva LIBERADA, ningún ticket y capacidad recuperada. No es un cobro real. |
| Bruma del Sur / Ana | Reserva de dos generales, creada al final, sin compra ni pago. TTL actual por defecto: 600 segundos. Puede vencer naturalmente antes de la demostración; no se renueva ni recrea en reejecuciones. |

Totales previstos: cinco reservas, cuatro compras/cuatro pagos (tres aprobados, uno rechazado), nueve tickets (uno anulado tras devolución), una devolución/reintegro parcial de 14000. Son **expectativas del dataset, no resultados ejecutados**. No se crean estados pendientes o rechazos mediante SQL. Si una operación queda incierta, el proceso se detiene para recuperación manual del intento original.

Preferencias de Ana/Bruno/Carla se guardan antes de publicar: permiten favoritos y recomendaciones con historial real. Se asigna personal a los tres eventos con tickets aprobados, sin validar ningún QR durante la carga. Los avisos de publicación/compra/devolución los generan los servicios existentes; su entrega SSE/outbox es asíncrona.

## Archivos nuevos

- `catalog.cjs`: datos y escenarios.
- `lib.cjs`: Compose canónico demo, guardas, autenticación por Gateway y diario de operaciones.
- `prepare.cjs`: prepara proyecto aleatorio, credenciales y claves nuevas, sin Docker.
- `start.cjs`: arranca únicamente el proyecto nuevo; bootstrap administrativo y siete clientes técnicos mediante el CLI existente.
- `seed.cjs`: población por APIs públicas y una clave estable por pago/reintegro.
- `verify.cjs`: consultas autenticadas de catálogo, stock, compras, pagos, tickets, preferencias, recomendaciones, notificaciones y reportes. No escanea QR ni marca avisos como leídos.
- `mysql-init.sh`: inicializador del volumen **nuevo**, cinco esquemas/usuarios restringidos. No contiene inserts comerciales ni DROP.
- `demo.test.cjs`: pruebas offline de catálogo, configuración, scopes, idempotencia y guardas.
- `.gitignore`: excluye todo `runtime/`; `.gitattributes`: LF para el shell.
- Este `README.md`.

No se crean `.env` del proyecto principal ni se modifican credenciales anteriores. El `package-lock.json` no versionado que ya estaba en la raíz es ajeno a esta preparación.

## Aislamiento y preflight

Proyecto: `ticketya-demo-<16 hex nuevos>`, volumen MySQL `<proyecto>_mysql_data`, volúmenes Mongo `<proyecto>_mongo_data` y `<proyecto>_mongo_config` (los dos directorios declarados por la imagen oficial), red `<proyecto>_backend`, subred comprobada `10.205.x.0/24`. No se utilizan recursos externos ni `container_name`, montajes de datos del host o puertos de bases publicados. Solo Gateway publica **127.0.0.1:49080**. No se aceptan argumentos para cambiar proyecto, URL, JDBC o puertos hacia E2/desarrollo.

Los cuatro esquemas de negocio se llaman `ty_demo_<id>_{eventos,inventario,entradas,compras}`; Mongo, `ty_demo_<id>_preferences`. **Identidad exige por código** `ty_e2_identity_<id>` y usuario `ty_identidad`; se conserva ese formato, pero con ID aleatorio nuevo, dentro de otro MySQL/volumen/proyecto. No se reutiliza ningún esquema/instancia E2 ni se debilita la guarda existente.

El arranque comprueba ausencia de nombres/volúmenes/red y puerto libre, solapamiento de subredes, imágenes locales, motor Linux y memoria estimada disponible. Los ocho servicios tienen límite individual de 768 MiB. Se exige margen estimado de siete GiB en Docker (límites de la demo más reserva); no es un benchmark ni garantía del host. Si no alcanza, se detiene: **no para otros contenedores**. Revisar también espacio disponible de Docker Desktop antes de autorizar la ejecución; ese espacio físico del VHDX no se mide automáticamente.

Solo se usa `up --no-build --no-recreate` sobre el nuevo proyecto, sin reiniciar recursos anteriores. Una segunda ejecución con `ready.json` solo acredita los ocho contenedores, sin repetir provisionamiento. Un arranque parcial se conserva y se rechaza la continuación automática. Nunca se ejecutan down, rm, prune ni eliminación de recursos. Antes de HTTP se contrastan etiquetas, imágenes, red, mounts, puertos, JDBC/usuarios, Mongo y claves con el manifiesto. No se consulta directamente una base ajena.

MySQL inicializa usuarios/esquemas exclusivamente si su volumen nuevo está vacío; Hibernate `update` crea tablas en esos esquemas. Mongo usa los inicializadores ya existentes, montados de solo lectura. Un fallo de inicialización parcial exige diagnóstico: no se regenera el volumen.

## Ejecutar mañana en Windows PowerShell (requiere autorización de arranque/carga)

**No se ejecutaron los comandos de arranque/carga durante la preparación.** Ejecutarlos desde la raíz `TicketYa_PruebaFinal`, con Docker Desktop Linux operativo, Node 22.12+ y las ocho imágenes disponibles localmente. Evitar campañas E2 concurrentes. Si falta una imagen, detenerse y acordar su construcción; los scripts no la descargan ni reconstruyen automáticamente.

```powershell
Set-Location 'C:\Users\JuanM\Desktop\Varios\TUDAI\Arquitectura Web\TicketYa_PruebaFinal'

# Solo verificaciones offline, sin Docker ni bases:
node --test demo/demo.test.cjs

# Preparar una única identidad de entorno y credenciales nuevas; no arranca nada:
node demo/prepare.cjs
if ($LASTEXITCODE -ne 0) { throw 'Preparación incompleta; inspeccionar antes de continuar' }

# Solo tras autorizar iniciar el entorno demo:
node demo/start.cjs
if ($LASTEXITCODE -ne 0) { throw 'Arranque incompleto; no reintentar ni eliminar recursos' }

# Un comando para poblarlo, solo tras autorizar la carga:
node demo/seed.cjs
if ($LASTEXITCODE -ne 0) { throw 'Carga detenida; revisar diario privado antes de repetir escrituras' }

# Comprobar resultados mediante lecturas, sin nuevos pagos ni reservas:
node demo/verify.cjs
if ($LASTEXITCODE -ne 0) { throw 'La demo requiere revisión; no forzar estados ni stock' }
```

`prepare` no sobrescribe una preparación existente: la valida y conserva las mismas claves/contraseñas. Si queda parcial, falla cerrado. Genera RSA 3072 mediante `node:crypto` y contraseñas independientes de 64 caracteres hexadecimales, sin dependencias nuevas. `runtime/` tiene ACL restrictiva para el usuario actual en Windows; si falla la ACL se detiene. Las claves se montan read-only. Revisar permisos de Docker Desktop si falla un bind; no abrir lectura pública ni regenerar claves como reparación.

Se genera **un solo archivo Compose** `demo/runtime/docker-compose.json` (JSON válido como YAML), sin overrides. Lo ejecuta `start.cjs` con `--env-file NUL` y el entorno privado; elimina variables COMPOSE heredadas y rechaza `DOCKER_HOST` remoto. No ejecutar `docker compose up` manualmente sin las guardas ni imprimir `compose config` completo: resuelve secretos. Las credenciales y claves de demo quedan solo en `runtime/private.json`, `runtime/keys/` y el archivo privado de accesos; nunca en Git, imágenes, argumentos de contraseñas o mensajes de error.

Consultar cuentas de demostración **solo localmente**, después de preparar:

```powershell
notepad demo/runtime/ACCESOS-DEMO.md
```

Ese documento ignorado contiene únicamente accesos humanos ficticios. No copiarlo al entregable ni publicar capturas de sus contraseñas. No hay credenciales predeterminadas versionadas. El subcomando de Identidad se ejecuta como job nuevo sin servidor; los jobs terminados se conservan.

## Frontend, verificación y recorrido

En otra terminal PowerShell, desde la raíz:

```powershell
$env:TICKETYA_GATEWAY_URL = 'http://127.0.0.1:49080'
# Solo si faltan node_modules: npm --prefix TicketYa/frontend ci
npm --prefix TicketYa/frontend run dev -- --host 127.0.0.1 --port 5190
```

Gateway: **http://127.0.0.1:49080**. Frontend: **http://127.0.0.1:5190**. No se modifica Vite: su variable de entorno existente apunta exclusivamente al Gateway demo. El puerto 5190 debe estar libre; no detener su propietario si está ocupado. Vite no es un servidor de producción.

1. Visitante: explorar 18 eventos por ciudad/tipo y consultar lugares. El detalle completo requiere login según seguridad actual.
2. Ana: entradas del palco agotado, avisos, favoritos y recomendaciones. La reserva de rock puede vencer; para probar una nueva reserva después, crearla manualmente desde la UI dentro de los límites existentes, no volver a poblar para renovar TTL.
3. Bruno: cinco entradas de cámara y compra rechazada. Observar el único asiento restante y capacidad recuperada en el evento electrónico. La compra rechazada es un ejemplo intencional de la pasarela simulada.
4. Carla: compra con una entrada anulada por devolución y otra vigente. No hay pantalla de devolución en el frontend actual: el escenario lo prepara la API, sin agregar una pantalla ficticia.
5. Organizador Sierras o Urbanos: sus eventos y reportes reales. `verification.json` conserva IDs/nombres para facilitar abrir los reportes.
6. Personal de acceso: copiar un QR vigente desde la cuenta cliente, ingresar el ID del evento preparado y validarlo. Esta operación manual cambiará solo la demo; la segunda validación debe rechazarse. No usar la entrada anulada de Carla como caso aceptado.

El frontend necesita estado PUBLICADO, lugar, fecha, participantes, precios y stock; el script publica de verdad y usa las dos APIs de asientos según su responsabilidad. No filtra eventos E2 ni altera React: esos eventos pertenecen a otro Gateway y no están en estas bases.

## Preparación en otra computadora

Desde la raíz Git (el directorio padre de `TicketYa/`):
`node TicketYa/integration-tests/e2/environment.cjs --prepare --demo` prepara
solo las ocho imágenes faltantes, incluidas MySQL 8.4.8 y MongoDB 8.0.16.
No inicia recursos ni reconstruye imágenes ya presentes. Si cambiaron fuentes,
reconstruir explícitamente la imagen correspondiente. Node del host >=22.12.
La guía de [portabilidad y limpieza selectiva](../docs/entrega2/portabilidad-entrega.md)
incluye Windows/Linux, navegador, memoria y diagnóstico. Los once archivos
fuente de `demo/` deben formar parte del entregable; `runtime/` permanece privado.

## Idempotencia, recuperación y límites

El provisionamiento de demos nuevas utiliza stdin sin TTY y
`IDENTITY_PROVISION_FILE=-`: no monta archivos `.properties` ni amplía sus
permisos. Identidad sigue como UID/GID 10001:10001. Si existe una imagen antigua,
reconstruir solo Identidad antes de crear otra demo; no reiniciar ni reprovisionar
la demo actual. La [guía](../docs/entrega2/portabilidad-entrega.md) incluye el
comando y la prueba Linux con usuarios distintos.

`seed-journal.json` registra un intento PENDING **antes** de cada escritura y el resultado DONE con sus IDs reales tras confirmarla. En una carga terminada, repetir `seed.cjs` no vuelve a enviar esas escrituras: reutiliza resultados y verifica estado. Pagos y reintegros tienen claves estables por ID de demo/escenario, medio original e importe del backend; no hay reintentos financieros automáticos. Una reserva/alta que pierda respuesta no se reenvía a ciegas: esos endpoints no tienen clave de idempotencia nativa.

Una exclusión `seed.lock` impide dos cargadores concurrentes. Al completar se conserva un archivo de cierre; si falla o se interrumpe queda el lock y/o paso PENDING. **No borrar el diario/lock ni repetir POST como reparación.** Revisar los IDs y respuestas con el operador; cualquier asociación de un resultado perdido requiere revisión explícita. No se promete recuperación automática de toda interrupción ni idempotencia sin conservar `runtime/`. No borrar ni sustituir ese directorio para “reiniciar” una demo con recursos vivos.

La verificación no recrea datos si cambiaron por uso manual, vencimiento o devoluciones posteriores. Puede informar diferencias legítimas; se investigan, no se reparan con SQL. No se fuerza una cola, cancelación de evento, pérdida de respuesta o estado financiero incierto para la demostración. El usuario informó una carga real y verificación exitosas, y luego una compra manual de dos entradas por 96000. Esa compra cambia los acumulados iniciales: no volver a ejecutar el verificador de baseline esperando las cifras anteriores. Esta revisión de portabilidad no repitió la carga ni consultó las bases de esa demo.

La preparación original verificó sintaxis Node/Bash, 16 pruebas offline y `docker compose config --quiet` con credenciales ficticias. La verificación reforzada contiene actualmente **43 pruebas offline** (incluidos falsos PASS financieros/inventario/preferencias/reportes), aprobadas nuevamente en esta revisión. No confundirlas con una nueva ejecución de carga, SQL o HTTP real. Las claves de demos nuevas en Linux son legibles por el UID 10001 dentro del bind de solo lectura, manteniendo `runtime/` privado; no se cambiaron claves existentes. Se preservan recursos y resultados E2. No hay commit ni push de esta revisión.
