> Estado vigente de la etapa 36: ver [autorización por propiedad](autorizacion-propiedad.md). Las notas de cierre de rutas y regresión pendiente de la etapa 35 se conservan como registro histórico.

# Actualización de la Implementación 35

La regresión comercial de esta guía corresponde al Bloque 1. Con las imágenes
seguras, ejecutar `node TicketYa/integration-tests/e2/run-regression.cjs --security`.
El modo comercial queda cerrado hasta completar autorización por propiedad;
no desactivar seguridad para ejecutar los escenarios anteriores. El builder
incluye ahora Identidad como sexta imagen. Consultar
[seguridad distribuida y pruebas aisladas](seguridad-distribuida.md).

# Docker y regresion de Entrega 2

## Dos entornos distintos

| Uso | Proyecto | HTTP | Datos |
|---|---|---|---|
| Demostracion persistente y validaciones 21C-26 | `ticketya-e2-infra` | `127.0.0.1:38080` | Volumen `ticketya-e2-infra_mysql_data`, cuatro esquemas `ticketya_e2_docker_*` |
| Regresion automatizada | `ticketya-e2-reg-<id aleatorio>` | `127.0.0.1:48080` a `48089` | Un volumen, red y cuatro esquemas nuevos `ty_e2_reg_<id>_*` por corrida |

La regresion no admite URL, JDBC, proyecto, volumen ni archivo Compose suministrados por el usuario. Solo permite elegir un puerto dentro de ese rango. No se reutilizan bases de desarrollo, de E1 o de las validaciones persistentes. Un puerto ocupado produce un error: no se detiene a su propietario.

## Construir desde un clon limpio

Requisitos: Docker Desktop operativo con contenedores Linux, Node.js 22 o posterior, Git y acceso a los registros Docker/Maven durante la primera construccion. No hace falta Maven ni Java instalados en el host. No cambia el stack: JDK/JRE 21; Eventos e Inventario conservan target Java 17.

Desde la raiz del repositorio:

```powershell
node TicketYa/integration-tests/e2/build-images.cjs
```

El script construye los cinco proyectos Maven independientes, secuencialmente, desde sus propios contextos y con las etiquetas `ticketya-e2-*:dev`. Los Dockerfiles no utilizan `target` del host y omiten compilacion/ejecucion de tests. Construir imagenes no sustituye ejecutar la regresion ni modifica los contenedores que ya estan ejecutandose.

El Compose raiz tambien incluye `build.context` por aplicacion; para un entorno NUEVO se puede usar `docker compose --project-name ticketya-e2-infra up --detach --build`. `pull_policy: never` conserva las imagenes locales: el `--build` es explicito.

## Credenciales y primer arranque persistente

`generate-docker-env.ps1` se ejecuta solo si no existe `.env`, desde PowerShell en Windows. Genera cinco valores criptograficos, UTF-8 sin BOM y ACL restrictiva. No sobrescribir ni volver a generar credenciales de un volumen inicializado. Para otros sistemas operativos, suministrar las cinco variables de `.env.example` mediante el entorno o un `.env` local ignorado, sin versionar valores reales.

Variables necesarias: `TY_E2_MYSQL_ROOT_PASSWORD`, `TY_E2_EVENTOS_DB_PASSWORD`, `TY_E2_INVENTARIO_DB_PASSWORD`, `TY_E2_ENTRADAS_DB_PASSWORD`, `TY_E2_COMPRAS_DB_PASSWORD`.

El proceso de Compose debe tener **ausente**, no vacia, una variable heredada `COMPOSE_DISABLE_ENV_FILE` invalida. En PowerShell puede quitarse solo de esa terminal:

```powershell
Remove-Item Env:COMPOSE_DISABLE_ENV_FILE -ErrorAction SilentlyContinue
docker compose --project-name ticketya-e2-infra config --quiet
```

No mostrar `docker compose config` completo: contiene credenciales resueltas. Los inicializadores de `docker/mysql` solo se ejecutan con directorio de datos nuevo. Cambiar `.env` no cambia usuarios existentes.

## Recuperar contenedores persistentes ya existentes

Estos comandos conservan los contenedores e imagenes actuales y no aplican una configuracion nueva que requiera recreacion:

```powershell
docker compose --project-name ticketya-e2-infra up --detach --no-build --no-deps --no-recreate mysql
docker inspect --format '{{.State.Health.Status}}' ticketya-e2-infra-mysql-1
```

Esperar `healthy`, revisar logs del arranque actual y, si hubo cierre anormal, comprobar los registros con SQL de lectura antes de iniciar las aplicaciones. Si no recupera, detenerse: no borrar volumen ni intentar reparar automaticamente.

Luego:

```powershell
docker compose --project-name ticketya-e2-infra up --detach --no-build --no-deps --no-recreate eventos inventario entradas compras gateway
```

Readiness significa seis contenedores running sin reinicios y HTTP 200 en los cuatro GET por Gateway: `/api/v1/participants`, `/reservas?idCliente=1`, `/tickets?clientId=1`, `/compras?clienteId=1`. Reintentar solamente estas lecturas hasta 180 segundos. `depends_on: service_started` del Gateway no demuestra readiness HTTP. No hay dependencias circulares de healthchecks entre microservicios.

El Compose ahora declara `stop_grace_period: 60s` para MySQL. **No se aplica retroactivamente** al contenedor persistente existente arrancado con `--no-recreate`. Para un cierre posterior autorizado de ese contenedor usar `docker compose --project-name ticketya-e2-infra stop --timeout 60`, revisar el resultado y el log de cierre; no se garantiza que cualquier codigo 137 se resuelva solo con ampliar el plazo.

## Ejecutar regresion aislada

Primero las pruebas negativas de las guardas, sin Docker ni HTTP:

```powershell
node --test TicketYa/integration-tests/e2/isolation.test.cjs
```

Con las imagenes construidas:

```powershell
node TicketYa/integration-tests/e2/run-regression.cjs
# Otra corrida independiente si 48080 esta ocupado:
node TicketYa/integration-tests/e2/run-regression.cjs --port 48081
```

El runner:

1. Genera una identidad nueva, cinco contrasenas independientes en memoria y un Compose canonico. Las claves se pasan al proceso; no se guardan en el Compose, el informe, `.env` ni argumentos SQL.
2. Rechaza recursos preexistentes con esa identidad. Solo publica el Gateway, en loopback; MySQL no publica puertos. La red bridge es exclusiva del proyecto y no se conecta a redes externas. Se evita `internal: true`, que en Docker Desktop puede impedir la publicacion efectiva del Gateway. Se verifican tanto el binding solicitado como el efectivo.
3. Arranca solo MySQL, acredita su identidad, credencial, volumen nuevo y etiquetas. Solo entonces crea los cuatro esquemas/usuarios propios. Es la unica inicializacion SQL de escritura del runner; no contiene DROP, TRUNCATE ni borrados.
4. Inicia las aplicaciones con `integracion` y `ddl-auto=update`, comprueba readiness y que el modelo nuevo tiene 26 tablas y no contiene datos de negocio.
5. Antes de cada escritura HTTP y de GET `/compras/{id}/entradas`, acredita otra vez las etiquetas, JDBC, perfiles, credenciales, puertos, redes, montajes y comandos efectivos. El acceso HTTP no utiliza proxies del entorno ni sigue redirecciones.
6. Ejecuta escenarios propios con IDs capturados: publicacion repetida, reserva numerada, compra/pago repetido, emision, devolucion parcial repetida, cancelacion repetida, pago rechazado/liberacion y recuperacion de pago incierto con la misma clave. No ejecuta ni importa `Entrega1IT.java` ni evalua scripts Postman.
7. Contrasta estados, importes, identidad de asientos, tickets/QR, lotes y efectos del simulador mediante GET y SQL SELECT sobre sus esquemas. Los POST iniciales no se reintentan; solo se repiten las operaciones idempotentes expresamente verificadas.
8. Guarda `compose.json` sin valores secretos y `report.json` bajo `TicketYa/integration-tests/target/e2/<id>/`, ignorado por Git. El informe registra imagenes, revision fuente, huella del runner, escenarios y codigos HTTP. Un error aborta nuevas escrituras y produce FAIL, conservando recursos para diagnostico.

No es una prueba de carga, seguridad JWT ni concurrencia con dos instancias de Inventario. Es una regresion funcional de la topologia actual. Tampoco ejecuta las suites JUnit ni valida los datos del volumen persistente: la recuperacion persistente se comprueba por separado y solo con lecturas.

## Conservacion y parada posterior

El runner no ejecuta stop, down, rm, prune ni elimina recursos, ni siquiera ante error. Al terminar, permanecen disponibles tanto el entorno persistente como el entorno de regresion. No volver a apuntar manualmente los tests a esos esquemas ni reutilizar su Compose sin las credenciales de su corrida. Las credenciales permanecen en la configuracion de sus contenedores, accesible a quien controla Docker: no publicar un `docker inspect` completo.

Una parada posterior debe autorizarse expresamente, verificando el proyecto y utilizando los nombres concretos de sus seis contenedores con un plazo de cierre de 60 segundos. No realizar limpieza global. Conservar volumen y registros para diagnostico; este procedimiento no incorpora eliminacion automatica de entornos de prueba.

## Protocolo de cada bloque E2

Implementacion pequena -> pruebas de guardas -> construccion de imagenes actualizadas -> regresion nueva aislada -> informe -> auditoria. No ejecutar pruebas con `deleteAll` o perfiles `create-drop` sobre el volumen persistente. Al incorporar JWT se adaptaran credenciales y expectativas del runner; no se desactivara seguridad en Docker para mantener los tests verdes.
