# Ejecución portable de TicketYa

Ejecutar desde la raíz Git (contiene `TicketYa/`, `demo/` y `Dockerfile.tests`).
Esta revisión no realizó commit: los archivos nuevos deben incorporarse al
entregable tras revisión. Un clon del HEAD anterior todavía no los incluye.
No copiar `.env`, `demo/runtime/`, claves ni `target/` entre computadoras.

## Herramientas y recursos

- Git, Docker Desktop/Engine **Linux**, plugin `docker compose` v2 o posterior
  (verificado con Engine 29.7.2 y Compose 5.4.0), Node **>=22.12**, npm.
- Frontend: Vite 8.3.8, React 19.3.1 y Playwright 1.65.1 fijados en su lockfile.
  Usar `npm --prefix TicketYa/frontend ci`; no hay paquete npm raíz.
- Java/Maven del host no son necesarios para Docker. Las seis imágenes usan
  JDK/JRE 21; Eventos e Inventario conservan compilación Java 17.
- El test image fija Maven 3.9.11/JDK 21 y Node 22.21.1; no instala Node 18
  desde apt. No depende de la imagen local antigua `ticketya-maven-node`.
- Internet inicial hacia Docker Hub, Maven Central y npm; espacio para imágenes,
  dependencias y volúmenes. Reservar varios GB de disco; no existe una medición
  de mínimo universal. Referencia práctica: Docker con 12–16 GiB y una campaña
  a la vez. La demo exige al menos 7 GiB estimados libres antes de iniciar.
  No detener proyectos ajenos para conseguir memoria automáticamente.
- Demo: 49080 Gateway y 5190 Vite, solo loopback. E2: Gateway 48080–48089,
  Forward derivado por el runner (ej. 48084 → 38228) y Vite 5174. Las bases no
  publican puertos. El frontend manual usa 5173 salvo override explícito.

## Demo desde una carpeta nueva

```powershell
git clone <URL-del-repositorio> TicketYa_PruebaFinal
Set-Location TicketYa_PruebaFinal
git switch entrega-2
node TicketYa/integration-tests/e2/environment.cjs --prepare --demo
npm --prefix TicketYa/frontend ci
node demo/prepare.cjs
node demo/start.cjs
node demo/seed.cjs
node demo/verify.cjs
$env:TICKETYA_GATEWAY_URL = 'http://127.0.0.1:49080'
npm --prefix TicketYa/frontend run dev -- --port 5190
```

En Bash sustituir `Set-Location` por `cd` y la asignación por
`export TICKETYA_GATEWAY_URL=http://127.0.0.1:49080`.
Abrir http://127.0.0.1:5190. Las credenciales ficticias se generan en cada
máquina en `demo/runtime/ACCESOS-DEMO.md` (privado, ignorado). Nunca publicarlas.
No se requiere `.env` para esta ruta aislada. El arranque prepara MySQL/Mongo,
Identidad, provisionamiento y luego los servicios/Gateway; no hay SQL manual.

`prepare` conserva credenciales existentes; `start` no recrea recursos parciales;
`seed` conserva su diario y bloquea escrituras con resultado incierto. No borrar
el lock/diario para forzar un reintento. `verify` comprueba el **dataset inicial**:
después de compras, QR u otros cambios manuales puede fallar legítimamente.
No repoblar ni modificar datos para ocultar esa diferencia. Las fechas del
catálogo son fijas: el cargador rechaza eventos a menos de 72 horas.

El provisionamiento nuevo envía properties por stdin, con `--interactive -T`
e `IDENTITY_PROVISION_FILE=-`. El CLI usa `System.in` directamente: no crea
archivos `.properties` sensibles ni los monta ni requiere cambiar su propietario.
No usar `/dev/stdin` como archivo: reabrirlo puede fallar tras cambiar de UID.
Identidad conserva UID/GID 10001:10001 y el soporte de archivos para otros usos.
Si una copia ya tenía la imagen anterior de Identidad, reconstruir **solo esa
imagen** antes de iniciar una campaña/demo nueva:
`docker build --tag ticketya-e2-identidad:dev TicketYa/identidad_service`.
La preparación de imágenes faltantes no reemplaza imágenes ya existentes.
No reiniciar ni reprovisionar la demo actualmente en uso para aplicar este cambio.

En Linux el padre `runtime/` es 0700. Las claves nuevas del bind de Identidad
son legibles por su UID no privilegiado dentro del contenedor; el padre privado
protege el acceso de otros usuarios del host. El montaje es solo lectura.
Windows usa ACL para el usuario actual. No se modifican claves ya generadas.

## Pruebas reproducibles

```powershell
# Pruebas del módulo documental/integración (37); no activa el perfil comercial E2E.
node TicketYa/integration-tests/run-tests.cjs
# Pruebas offline del preflight, guardas y demo:
node --test TicketYa/integration-tests/e2/environment.test.cjs TicketYa/integration-tests/e2/security-isolation.test.cjs TicketYa/integration-tests/e2/recommendation-isolation.test.cjs TicketYa/integration-tests/e2/mongo-central-isolation.test.cjs demo/demo.test.cjs
npm --prefix TicketYa/frontend test
npm --prefix TicketYa/frontend run build
# Campaña real nueva: preparar SOLO imágenes ausentes; no usa la demo.
node TicketYa/integration-tests/e2/environment.cjs --prepare --all
node TicketYa/integration-tests/e2/environment.cjs --all --frontend
node TicketYa/integration-tests/e2/run-security.cjs --centralized --notifications --audit --load --frontend --port 48084
```

Antes de `--frontend`, Windows requiere Edge instalado. En Linux:
`cd TicketYa/frontend`, `npx playwright install --with-deps chromium`; requiere
permisos de instalación del sistema. En Windows también puede usarse Chromium:
`npx playwright install chromium` y `$env:TY_BROWSER_CHANNEL='chromium'`.
En Bash: `export TY_BROWSER_CHANNEL=chromium`. El preflight verifica instalación;
el lanzamiento real todavía puede detectar bibliotecas del SO ausentes.

Para las seis suites Java sin Docker: JDK 21, Maven 3.9 y `mvn test` dentro de
cada proyecto independiente. No existe un POM reactor en la raíz.
El runner del módulo construye su imagen si falta, monta fuentes solo
lectura y `integration-tests/target` escribible, conserva contenedor/evidencias
y reutiliza `ticketya-maven-cache`. No inicia bases ni aplicaciones.
Reportes: `TicketYa/integration-tests/target/runs/<run>/surefire-reports/`;
contratos derivados: `target/documentacion/`. E2 conserva su reporte privado
en `TicketYa/integration-tests/target/security/<run>/report.json` incluso si
falla el preflight. No compartir directorios privados de pruebas.

`--prepare` reutiliza imágenes existentes, no verifica si sus fuentes cambiaron.
Ante cambios Java reconstruir el servicio afectado con su Dockerfile; para
reconstruir los seis: `node TicketYa/integration-tests/e2/build-images.cjs`.
Para el colector: `docker build --tag ticketya-e2-logs:dev docker/observability`.

## Diagnóstico sin borrar recursos

| Síntoma | Acción |
|---|---|
| `DOCKER_ENGINE` / HTTP 500 / timeout | Comprobar `docker version`, Desktop y motor Linux. Detener la campaña; no reiniciar WSL ni limpiar automáticamente. |
| `MISSING_IMAGES` | Preparar con `environment.cjs --prepare --all` (o `--demo`). Se informa la etiqueta exacta, incluido MySQL 8.4.8. |
| `IMAGE_PULL` / `IMAGE_BUILD` | Revisar conectividad, proxy, permisos del registro, espacio y salida de build. No iniciar servicios hasta corregir. |
| Puerto o Forward ocupado | Elegir otro `--port` autorizado; no detener su propietario. Cambiar el puerto Vite manual si corresponde. |
| Colisión de subred | El E2 busca otra identidad canónica antes de crear recursos. La demo falla sin tocar redes existentes: revisar su configuración con el operador. |
| Servicio no saludable en 180 s | Consultar `docker logs <contenedor-exacto>` y `docker inspect --format '{{json .State}}' <contenedor-exacto>`. No imprimir `.Config.Env`. Revisar memoria, JDBC, permisos de claves y healthcheck. |
| Exit 137 | Verificar `OOMKilled` y tiempos: 137 también puede indicar SIGKILL por timeout de stop, no demuestra OOM. |
| MySQL no conecta | Inspeccionar logs y nombres internos del proyecto nuevo, usuarios y esquemas generados. No apuntar a 3306 del host ni recrear volúmenes para corregirlo. |
| Node ausente / Vite incompatible | Node >=22.12 en host; usar el test image reproducible para Maven+Node. |
| Error de navegador | Instalar el canal y dependencias del SO. El runner conserva diagnóstico de proceso saneado en su directorio privado. |
| Carga parcial / respuesta perdida | Conservar journal, IDs y claves; no repetir POST ni borrar datos como reparación. |

## Detención y eliminación selectiva (solo bajo decisión del operador)

Nada de lo siguiente se ejecuta automáticamente. Conservar primero evidencias
y datos necesarios. Nunca usar `prune`, `down -v` ni filtros globales.
En PowerShell seleccionar el proyecto **exacto** informado por el runner:

```powershell
$project = 'ticketya-demo-<16-hex-del-proyecto>'
if ($project -notmatch '^ticketya-(demo|e2-reg)-[a-f0-9]{16}$') { throw 'Proyecto no autorizado' }
$ids = @(docker ps -aq --filter "label=com.docker.compose.project=$project")
# Revisar la lista antes de continuar. Sin IDs no ejecutar comandos de parada/borrado.
foreach ($id in $ids) {
  $owner = docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' $id
  if ($owner -ne $project) { throw 'Propietario inesperado' }
}
if ($ids.Count) { docker stop --time 60 $ids }
# OPCIONAL, destruye solo los contenedores ya detenidos de esta lista:
# if ($ids.Count) { docker rm $ids }
```

Después de quitar contenedores, listar volúmenes y redes con
`docker volume ls --filter "label=com.docker.compose.project=$project"` y
`docker network ls --filter "label=com.docker.compose.project=$project"`.
Si se decide eliminarlos, usar **un nombre exacto cada vez**, comprobar etiqueta
del mismo proyecto y nombre que comience por `$project + '_'`; ejecutar
`docker volume rm <nombre-exacto>` / `docker network rm <nombre-exacto>` sin
`--force`. Un volumen eliminado pierde sus datos. Conservar
`ticketya-e2-infra_mysql_data`, caché Maven y recursos de otros proyectos.
No borrar `demo/runtime/` mientras haya recursos asociados. No se promete
reanudar un Compose generado sin recuperar sus credenciales del proceso.

## Evidencia de la revisión inicial de portabilidad

Se ejecutaron las 36 pruebas del módulo de integración: 36 aprobadas. La
comparación controladores/OpenAPI conserva igualdad estricta; se incorporaron
20 operaciones E2 faltantes y se actualizó la proyección derivada de Gateway.
Los reportes históricos locales de los seis servicios suman 869 JUnit aprobadas;
se consultaron, no se reejecutaron. Node: 77 pruebas offline distintas aprobadas
(20 preflight/diagnóstico, 14 guardas y 43 demo). Frontend: 40 unitarias y build
aprobados.
La demo real y compra manual de 96000 son evidencia aportada por el usuario,
no operaciones ejecutadas durante esta revisión. No se repitió campaña HTTP,
QR, SQL ni carga comercial. Windows fue ensayado; Linux fue revisado por código,
no ejecutado en una segunda computadora. El diagnóstico no garantiza
compatibilidad universal ni una instantánea distribuida de datos.

### Correcciones focalizadas posteriores a Astra

- 37 pruebas documentales aprobadas, incluida regresión contra rutas bloqueadas
  y `X-User-Role` en el contrato público. El inventario por servicio conserva
  la coordinación interna, pero Gateway no anuncia `/admin/eventos/{id}/cancelacion`.
  El material Postman E1 es histórico, como indica su índice; no usar esos
  encabezados declarativos como autenticación actual.
- 2 JUnit nuevas de Identidad: stdin directo y compatibilidad con archivos.
- 4 pruebas de provisionamiento aprobadas, incluida Java real en Linux UID
  10001: archivo root/0600 ilegible y properties por `System.in` legibles.
  El ensayo inicial con `/dev/stdin` falló y motivó usar la entrada `-`.
- 77 pruebas offline de preflight/guardas/demo aprobadas nuevamente.
  Sin campaña comercial, SQL ni cambios en contenedores existentes.

Para repetir la prueba Linux aislada, primero ejecutar el runner documental
(prepara `ticketya-tests:java21-node22`), después:

```powershell
$env:TY_PROVISION_DOCKER_TEST='1'
node --test TicketYa/integration-tests/e2/provisioning.test.cjs
Remove-Item Env:TY_PROVISION_DOCKER_TEST
```

En Bash: `TY_PROVISION_DOCKER_TEST=1 node --test TicketYa/integration-tests/e2/provisioning.test.cjs`.
Sin esa variable se ejecutan tres pruebas offline y se omite explícitamente la
cuarta. El ensayo no usa red ni bases y conserva su contenedor; solo el fixture
arranca como root para crear el archivo inaccesible y luego baja a UID 10001.
Para las JUnit focalizadas, con JDK 21/Maven: desde `TicketYa/identidad_service`,
`mvn -Dtest=ProvisioningCommandTest test`.
