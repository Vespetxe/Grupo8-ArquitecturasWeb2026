# Identidad — Bloque 2, etapa 1

Microservicio independiente `TicketYa/identidad_service`, Java 21 y Spring Boot
4.1.2. Conserva resultados históricos de la etapa inicial. La versión vigente incorpora seguridad distribuida y rutas /auth por Gateway; ver [autorización](autorizacion-propiedad.md) y [scopes vigentes](entrega-final.md#siete-enlaces-técnicos-vigentes). No cambia versiones Java ni Spring de los proyectos anteriores.

## Modelo y contratos

Cuatro tablas propias, sin relaciones JPA con bases de negocio:

| Tabla | Responsabilidad |
| --- | --- |
| identity_users | ID numérico autogenerado, email normalizado único, hash y ACTIVE/DISABLED |
| identity_user_roles | Roles humanos; combinación usuario/rol única |
| identity_technical_clients | ID técnico, hash del secreto, habilitación, una audiencia y scopes permitidos |
| identity_provisioning_markers | Marcador único FIRST_ADMIN; protege el bootstrap concurrente en la misma transacción |

Los IDs nuevos no se vinculan automáticamente con clientes ni organizadores de
validaciones anteriores. La unicidad del email también se protege en la base.

| Método y ruta | Acceso | Entrada / resultado |
| --- | --- | --- |
| POST /auth/register | Público | `{email,password}` → 201, usuario CLIENTE; duplicado 409 |
| POST /auth/login | Público | `{email,password}` → 200, `access_token`, `token_type`, `expires_in`; credenciales inválidas 401 |
| GET /auth/me | JWT humano válido | 200, ID/email/estado/roles de la cuenta activa; sin token o inválido 401 |
| GET /oauth2/jwks | Público, red interna | JWKS público con kid; nunca componentes privados |
| POST /oauth2/token | Cliente técnico registrado | OAuth2 `client_credentials`, `client_secret_basic`; sin roles humanos |

Registro: email válido hasta 254 caracteres; contraseña entre 12 y 128 caracteres.
Los campos desconocidos, incluidos `id`, `roles`, `status` y `permissions`, son
rechazados con 400, no ignorados. Nunca se serializa una entidad de persistencia.
Los cuerpos de errores no incluyen entradas ni excepciones de validación.
Login de usuario inexistente/contraseña incorrecta/cuenta deshabilitada comparte
el mismo 401. Se verifica un hash ficticio si no existe la cuenta. Respuestas de
identidad llevan `Cache-Control: no-store`.

## Contraseñas y JWT

`DelegatingPasswordEncoder`, identificador `{argon2id-v1}`, Argon2id mediante Spring
Security y Bouncy Castle: 19 MiB, dos iteraciones, paralelismo uno, sal aleatoria
de 16 bytes y resultado de 32 bytes. Calibrar bajo carga antes de exposición real;
no hay hash manual ni credenciales predeterminadas. El mismo encoder protege los
secretos técnicos. Los DTO de credenciales y tokens redactan `toString()`.

Firma RS256 mediante Nimbus/Spring Security, RSA de al menos 3072 bits. La pareja
se carga de archivos PEM PKCS#8/SPKI externos; se comprueba correspondencia. No
se genera una clave durante el arranque y no se copia material privado a imágenes.
El emisor, audiencias y kid son configurables.

JWT humano: `iss`, `sub` numérico estable, `aud`, `iat`, `nbf`, `exp`, `jti`,
`token_use=user`, `roles`. TTL cliente 15 minutos; privilegiados cinco. `/auth/me`
valida localmente firma, RS256, issuer, audiencia `ticketya-identidad`, expiración,
nbf y tipo humano, y consulta que la cuenta continúe activa. También fija el kid
actual, exige sub positivo representable como Long, roles de tipo texto conocidos,
iat no futuro fuera de tolerancia y orden coherente iat/nbf/exp. Tolerancia temporal
30 segundos. No confía en encabezados de usuario ni en IDs declarados.

JWT técnico: `sub=service:<clientId>`, `token_use=service`, audiencia única
inmutable asignada al provisionar, scopes autorizados, TTL máximo dos minutos.
No lleva `roles`; `/auth/me` lo rechaza. Emisión oficial de Spring Security
Authorization Server 7.1.2, versión gestionada por Boot, sin grants artesanales.
Solamente se registran clientes confidenciales con `client_credentials`.

Política de clientes permitidos:

| Emisor técnico | Destinatario | Scopes máximos |
| --- | --- | --- |
| eventos | ticketya-inventario | inventory.prepare inventory.lifecycle reports.inventory.read |
| eventos | ticketya-entradas | tickets.event-block reports.tickets.read |
| eventos | ticketya-compras | purchases.event-cancel purchases.history.read notifications.publish reports.sales.read |
| compras | ticketya-eventos | events.read |
| compras | ticketya-inventario | reservations.read reservations.confirm reservations.release reservations.compensate reservations.return |
| compras | ticketya-entradas | tickets.read tickets.issue tickets.annul tickets.status |
| entradas | ticketya-compras | orders.read |

Un cliente por pareja emisor/destino (`entradas-to-compras`, por ejemplo), con un
subconjunto no vacío de scopes de la tabla. No hay alta dinámica pública.
Eventos, Compras y Entradas consumen estos tokens en los siete enlaces; los humanos no reciben estos scopes.

`ClientRegistry` consulta MySQL en cada búsqueda; no contiene un registro de
clientes en memoria que necesite restaurarse. Reconstruye `RegisteredClient` desde
la fila persistida, verificando habilitación y la misma política de ID/audiencia/
scopes utilizada en el provisionamiento. Una fila fuera de política se rechaza,
no se amplía ni se filtra silenciosamente. Deshabilitar bloquea solicitudes nuevas
con 401 `invalid_client`, también después de reiniciar. No revoca firmas de tokens
ya emitidos: siguen sujetos a su TTL y a la política del futuro destinatario.
Esto es distinto de `InMemoryOAuth2AuthorizationService`, cuyo bookkeeping de
autorizaciones sí se pierde al reiniciar; no se reconstruye desde MySQL.

## Configuración

| Variable | Valor / finalidad |
| --- | --- |
| SERVER_PORT | 8085 por defecto; nunca publicado por Compose |
| DB_URL | JDBC MySQL de Identidad exclusivamente |
| DB_USERNAME | ty_identidad |
| DB_PASSWORD | Secreto propio, obligatorio sin valor predeterminado |
| IDENTITY_JWT_ISSUER | Emisor estable, obligatorio; Compose: http://identidad:8085 |
| IDENTITY_JWT_AUDIENCES | Lista separada por comas, debe incluir ticketya-identidad |
| IDENTITY_JWT_PRIVATE_KEY | Ruta absoluta al PEM privado montado |
| IDENTITY_JWT_PUBLIC_KEY | Ruta absoluta al PEM público montado |
| IDENTITY_JWT_KEY_ID | Identificador de la clave actual |
| IDENTITY_KEYS_DIR | Directorio externo de claves para el bind de Compose |
| TY_E2_IDENTIDAD_DB_PASSWORD | Credencial para Compose/migración administrativa futura |
| IDENTITY_PROVISION_FILE | Archivo externo protegido para la herramienta CLI |

La guarda del datasource se ejecuta antes de conectarse: admite exclusivamente
el esquema `ticketya_e2_docker_identidad` o `ty_e2_identity_<16 hex>` con usuario
`ty_identidad`. H2 en memoria tiene una única URL exacta para JUnit y su driver es
dependencia de test. Rechaza los esquemas de negocio, históricos y usuario root.
El `ddl-auto` de aplicación es `validate`; Compose y runner de pruebas eligen
explícitamente `update` al preparar las tablas nuevas. Nunca usan create-drop en
MySQL. JUnit usa create-drop solamente sobre H2 en memoria.

### Claves externas

Preparar explícitamente un directorio nuevo **fuera del repositorio**, por ejemplo:

```powershell
node TicketYa/identidad_service/tools/prepare-keys.cjs C:\Users\JuanM\ticketya-identity-keys
```

La utilidad usa `node:crypto` RSA 3072, rechaza destinos dentro del repositorio,
rutas relativas y destinos existentes. Nunca reemplaza claves. Restringe ACL al
usuario actual en Windows; en Unix directorio 0700 y privado 0600. Si falla, se
detiene y requiere inspección del estado parcial. No se ejecuta implícitamente.
En Linux el operador debe asignar propietario/grupo de lectura al UID/GID 10001
del contenedor, sin abrir lectura a otros usuarios. Docker Desktop debe poder
leer el bind. Las claves no se encuentran en `.env`, Git, logs ni imágenes.

Persistir la pareja entre reinicios; el JWKS y kid permiten distribuir claves
públicas para validación local futura. Rotación con solapamiento de varias claves
de verificación y TLS del despliegue son trabajo posterior; no reemplazar la pareja
sin coordinar la expiración de tokens vigentes.

## Provisionamiento exclusivamente operativo

No existe endpoint HTTP para crear roles privilegiados. La ejecución normal no
provisiona cuentas. El subcomando exacto `provision` arranca un contexto **sin
servidor web**, ejecuta una operación y sale. El perfil `provision` en una app web
se rechaza antes de leer el archivo y escribir. Usar usuario MySQL de Identidad,
nunca root. Un operador con acceso al host/credenciales de la base es la frontera
administrativa de esta herramienta.

Crear fuera del repositorio un archivo de propiedades UTF-8 protegido para el
usuario actual, evitando consola/historial. Ejemplos de **estructura**, sin valores
de credenciales utilizables:

```properties
operation=first-admin
email=<email administrativo>
password=<secreto nuevo de 12 a 128 caracteres>
```

Luego de existir el marcador FIRST_ADMIN:

```properties
operation=user
email=<email organizador o acceso>
password=<secreto nuevo de 12 a 128 caracteres>
role=ORGANIZADOR
```

`role` permite ORGANIZADOR o PERSONAL_ACCESO. No crea administradores adicionales
ni modifica usuarios existentes. Para clientes técnicos:

```properties
operation=client
caller=entradas
audience=ticketya-compras
scopes=orders.read
secret=<secreto nuevo de 32 a 128 caracteres>
```

Preferir secretos hexadecimales generados criptográficamente: en el formato
Properties los caracteres especiales requieren escapes. `IDENTITY_PROVISION_FILE`
apunta al archivo protegido; con la configuración DB/JWT del servicio disponible:

```powershell
java -jar TicketYa/identidad_service/target/identidad-service-0.0.1-SNAPSHOT.jar provision
```

No pasar contraseñas como argumentos. No guardar estos archivos en Git ni en
imágenes. No ejecutar el CLI con el entorno persistente sin su preparación y
autorización previas. Custodiar/eliminar manualmente los archivos operativos según
la política local; la herramienta no elimina archivos ni rota credenciales.

## Migración persistente pendiente de autorización

El volumen histórico conserva sus cuatro esquemas, inicializadores y datos.
La plantilla incluye Identidad bajo perfil optativo `identidad`; esto no convierte
un arranque sin Identidad/keys/provisionamiento en una instalación E2 completa.
No activar ni recrear los seis contenedores históricos con la configuración actual. No se montó un nuevo init script, porque los
inicializadores de la imagen oficial no se ejecutan sobre un volumen existente.

`docker/mysql/create-identity.sh` es una migración administrativa aditiva explícita,
no un entrypoint automático. Crea base, usuario y permisos limitados (escapa `_`
en GRANT con NO_BACKSLASH_ESCAPES). Concede SELECT/INSERT/UPDATE/DELETE y
CREATE/ALTER/INDEX/REFERENCES para Hibernate update; no concede ALL, DROP, permisos
de rutinas, triggers ni GRANT OPTION. Antes de todo DDL consulta si existe el
esquema o el usuario en cualquier host: una segunda ejecución o colisión se
rechaza sin crear ni alterar recursos. No tiene DROP ni IF NOT EXISTS.
MySQL no hace transaccional todo ese DDL: ante error
inspeccionar y solicitar autorización; no volver a ejecutar automáticamente.
El operador debe serializar migraciones: el preflight no es un bloqueo global
contra otro administrador que modifique recursos concurrentemente.

Antes de usar su modo `--persistent-authorized` se requiere aprobación específica:
1. Revisar que no existan schema/usuario de Identidad ni colisiones.
2. Preparar una sexta contraseña independiente de 64 hex sin imprimirla.
3. Autorizar expresamente CREATE DATABASE/CREATE USER/GRANT en ese MySQL.
4. Ejecutar por stdin/env del proceso, dentro del contenedor correcto, con
   `IDENTITY_MIGRATION_APPROVED=ticketya-e2-infra`; no poner secretos en argumentos.
5. Verificar autenticación y denegación sobre las otras cuatro bases.
6. Autorizar después el arranque optativo de Identidad con claves externas.

Cuando se autorice ese arranque, dirigirlo únicamente al servicio nuevo con
`docker compose --profile identidad up --detach --no-build --no-deps identidad`,
después de verificar MySQL healthy. No ejecutar un `up` global para incorporarlo
ni recrear MySQL con motivo de esta migración.

No se ejecutó esa migración ni se modificó el `.env` existente en esta etapa.
El generador anterior conserva sus cinco credenciales; no se lo debe volver a
ejecutar para sobrescribirlas. Configuración del perfil incompleta debe impedir
el arranque de Identidad, sin recurrir a credenciales predeterminadas.

## Verificación reproducible

Con JDK 21 y Maven:

```powershell
mvn -f TicketYa/identidad_service/pom.xml --batch-mode --no-transfer-progress package
node --test TicketYa/identidad_service/tools/prepare-keys.test.cjs
docker build --progress=plain --tag ticketya-e2-identidad:dev TicketYa/identidad_service
node TicketYa/identidad_service/tools/verify-isolated.cjs
```

También puede ejecutarse Maven en `maven:3.9.11-eclipse-temurin-21`, montando
únicamente el proyecto nuevo y una caché Maven propia. El Dockerfile usa ese
builder y `eclipse-temurin:21.0.11_10-jre-jammy`, copia sólo el JAR ejecutable y
ejecuta con 10001:10001. Omite tests en la imagen; el primer comando sí los ejecuta.

JUnit HTTP usa servidor real con puerto aleatorio y H2 en memoria, claves RSA
efímeras, seguridad real sin mock de decoder ni bypass: registro/duplicados,
inyección de autoridad, validación, hash, login, cuenta deshabilitada, firma,
claims, expiración, issuer/audiencia, JWT manipulado/algoritmo none, JWKS público,
401/403, `/auth/me`, bootstrap de roles y client_credentials/scope inválido.
Las guardas JDBC se prueban antes de intentar conexiones.

El runner Node no acepta overrides ni URLs. Genera proyecto
`ticketya-id-reg-<16 hex>`, volumen/red nuevos, MySQL 8.4.8 propio, esquema propio,
credenciales aleatorias sólo en memoria/env del proceso y claves de test bajo
`target/isolated/<id>` ignorado por Git. No publica **ningún puerto**. El helper
`node:22.21.1-alpine` hace HTTP dentro de esa red, nunca hacia los entornos previos.
Inspecciona proyecto, volumen, red y configuración efectiva antes de operaciones.
Las otras cuatro bases de la prueba de permisos son fixtures vacíos nuevos dentro
del mismo MySQL aislado, no bases históricas.

Comprueba migración, cuatro denegaciones cruzadas, arranque, CLI, registro/login,
JWT con verificación RSA independiente, `/auth/me`, token técnico y reinicio del
contenedor **nuevo** conservando un token anterior y la misma clave. Compara los
IDs/estados/instantes/reinicios de todos los contenedores previos. Reporte sin
tokens/secretos en `target/isolated/<id>/report.json`.
Los tokens de smoke se intercambian por stdout capturado entre helper y padre;
el helper usa `--log-driver none` para impedir su registro por Docker. El runner
no los muestra ni guarda. Archivos operativos de bootstrap de pruebas
quedan en ese directorio ignorado y con permisos restringidos para inspección.

No hay limpieza automática de servicios, volúmenes ni redes. Sólo contenedores
temporales de herramientas/CLI propios usan `--rm`. Si falla una etapa, detiene
la secuencia, informa proyecto/etapa sin volcar stderr/configuración/credenciales
y conserva los recursos. Un nuevo intento usa otra identidad, nunca reinicializa
un volumen. Detener o retirar recursos retenidos es una decisión explícita.

## Límites y siguiente etapa

La seguridad de negocio sigue exactamente como antes: este incremento no
protege aún Gateway, compras, reservas ni entradas. Sus claims de IDs históricos
no se convierten en identidades. Preparar validación RS256 distribuida, propiedad
de recursos, scopes de llamadas internas y regresión con usuarios reales.

No hay refresh token ni revocación inmediata distribuida. `/auth/me` observa estado
actual; futuros validadores offline observarán bajas/roles al expirar el JWT, salvo
que se incorpore otro mecanismo aprobado. Authorization Server usa almacenamiento
en memoria de autorizaciones de tokens técnicos; usuarios/clientes/hashes sí son
persistentes. Reiniciar no invalida firmas, pero pierde bookkeeping de revocación/
introspección. No prometer revocación durable en este incremento.

Antes de exposición externa: TLS, límites de intentos/registro, calibración Argon2
con carga, gestión/backup de claves y decisión de rotación/revocación. PERSONAL_ACCESO
ya existe como rol, pero no se inventan endpoints QR ni asignaciones de eventos.

Referencias oficiales: [JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html),
[Authorization Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/getting-started.html).

## Validación realizada en la Implementación 32

- Maven/JDK 21: package exitoso, 11 tests JUnit sin fallos ni omitidos.
- Guardas del generador de claves: 3 tests Node aprobados.
- Guardas E2 anteriores sin cambios: 25 tests Node aprobados.
- Imagen multietapa `ticketya-e2-identidad:dev` construida desde código fuente.
- Proyecto `ticketya-id-reg-edc84eec6fad65fa`: smoke aprobado, 18 comprobaciones
  HTTP, cuatro denegaciones de permisos, CLI de roles/clientes, hash persistido y
  reinicio aislado conservando JWT y claves. Cuatro tablas propias, cero puertos.
- Proyecto anterior `ticketya-id-reg-086ae2073628bae2` retenido: la comprobación
  HTTP inicial se detuvo porque el helper esperaba un array para `aud` técnico;
  JWT admite también texto con una audiencia. Se corrigió sólo el helper y se
  repitió en un proyecto nuevo. No se reinicializó el primero ni se borraron datos.
- Ambos proyectos nuevos permanecen disponibles para auditoría. Los seis
  componentes persistentes siguen funcionando, con cero reinicios, y sus cuatro
  GET de lectura respondieron 200. Los hashes de los cuatro dumps de datos
  coinciden con la validación previa: permanecen 26 tablas de negocio sin cambios.
- Compose validado y comparado contra HEAD: seis servicios previos, red y volumen
  sin cambios; Identidad optativa y sin publicación de puertos.
- Sin migración persistente, commits ni pushes. No se ejecutaron circuitos
  comerciales ni regresión destructiva sobre bases existentes.

Advertencias de herramientas observadas: auto-adjunción del agente Mockito en
JUnit (sin fallos) y normalización Git LF/CRLF en YAML/.env.example. El shell de
MySQL conserva LF por `.gitattributes`. El JDK del builder reporta 21.0.9; el JRE
de la imagen final es 21.0.11+10, siguiendo las imágenes ya aprobadas.

## Auditoría y consolidación — Implementación 33

Revisión directa de configuración, controladores/DTO, servicios, entidades,
repositorios, CLI, Dockerfile/contexto, montajes, generador y migración.
Se conserva la separación controller/service/repository, las entidades no se
exponen por HTTP y la cadena de Authorization Server precede a la API stateless.
Registro público sin roles privilegiados; login genérico, hash Argon2id versionado,
JWKS público y emisión RS256 sin cambios de contratos. No se agregó autenticación
a los clientes REST de negocio ni al Gateway.

Defectos y correcciones concretos:

| Hallazgo previo | Corrección y evidencia |
| --- | --- |
| `sub` admitía números mayores a Long; `/auth/me` podía fallar con NumberFormatException después de una validación JWT exitosa | Rango positivo Long comprobado en el validador; claims firmados fuera de rango reciben 401 |
| El decoder de clave pública ignoraba kid y no imponía estructura estricta de roles ni coherencia iat/nbf/exp | kid fijado, roles textuales conocidos, fechas coherentes y control de excepciones del validador; pruebas HTTP con tokens firmados inválidos |
| Issuer sólo se validaba por prefijo http/https | URI absoluta con host, sin credenciales/query/fragmento; pruebas de configuración inválida |
| Las claves podían ser relativas y los errores de conversión propagaban detalles del proveedor | Archivos absolutos obligatorios, errores redactados, prueba de correspondencia mediante Signature estándar; tests de archivos ausentes, inválidos, débiles y no coincidentes |
| La política técnica se aplicaba únicamente al provisionar | `TechnicalClientPolicy` compartida con `ClientRegistry`; cada lectura persistida rechaza ID/audiencia/scopes fuera de política. Pruebas con secreto correcto y filas alteradas |
| Migración concedía ALL PRIVILEGES y podía crear una base vacía antes de descubrir un usuario preexistente | Permisos explícitos y preflight de ambos recursos antes del DDL; repetición y colisión verificadas en MySQL aislado |
| El runner protegía la ACL de su directorio después de escribir claves de prueba | Restricción del directorio antes de escribirlas en Windows |

Los casos de claims anómalos requieren firma de una clave confiable: no implican
que un atacante sin la clave pudiera falsificar tokens. La revisión endurece
rechazo y manejo de errores, sin atribuir una ruptura criptográfica inexistente.
La comprobación de filas OAuth2 alteradas protege ante configuración fuera de
política; no se identificó alta pública de clientes ni elevación por registro.

Archivos ajustados respecto de la Implementación 32:

- `src/main/.../security/KeyConfiguration.java`, `ClientRegistry.java`;
- `src/main/.../service/ProvisioningService.java`;
- `src/main/.../config/JwtProperties.java`;
- nuevo `src/main/.../security/TechnicalClientPolicy.java`;
- `src/test/.../IdentityHttpTest.java` y nuevo `security/KeyConfigurationTest.java`;
- `tools/verify-isolated.cjs`, `mysql-smoke.cjs`, `prepare-keys.test.cjs`;
- `docker/mysql/create-identity.sh` y este documento.

Resultados ejecutados:

- Maven package: BUILD SUCCESS; 16 pruebas JUnit, cero fallos/errores/omitidas.
- Claves: cuatro pruebas Node aprobadas, incluyendo generación temporal RSA 3072,
  ACL Windows sin herencia y rechazo de sobrescritura conservando el archivo.
  La prueba ACL falló inicialmente dentro del sandbox y pasó con ejecución
  autorizada fuera de él. No se tocaron ni regeneraron claves persistentes.
- Guardas E2 anteriores: 25 pruebas aprobadas, fuentes sin cambios.
- Imagen `ticketya-e2-identidad:dev` reconstruida desde las fuentes corregidas.
- Nuevo proyecto `ticketya-id-reg-713380a5fb16d729`, esquema
  `ty_e2_identity_713380a5fb16d729`: 18 comprobaciones HTTP iniciales más las
  comprobaciones de usuario y emisión técnica tras reinicios; cuatro tablas;
  provisionamiento privilegiado y cliente técnico por CLI aprobados.
- Segunda migración rechazada sin cambios de permisos; colisión de usuario
  rechazada antes de crear otro esquema. Cinco accesos cruzados denegados: cuatro
  fixtures de servicios y un nombre del mismo largo que sustituye `_` por `x`,
  para comprobar el escape de comodines. Sin ALL/DROP/GRANT OPTION en los grants.
- Cliente técnico habilitado: emisión 200 después de reiniciar, usando secreto
  original y registro en MySQL. Luego se deshabilitó **sólo en ese entorno nuevo**:
  nuevas emisiones 401 antes y después de otro reinicio. Queda deshabilitado para
  auditoría. Los JWT ya emitidos no se revocan retroactivamente.
- Logs de la instancia aislada: cero ERROR/CommandAcceptanceException.
- Snapshot de todos los contenedores anteriores sin cambios. Seis persistentes
  running, cero reinicios, MySQL healthy; cuatro GET de lectura por Gateway 200.
  Los hashes de dumps de los cuatro esquemas coinciden con Bloque 1/Impl. 32.
- Sin migración al MySQL persistente, sin modificaciones a sus datos ni a los cinco
  proyectos previos. No hubo commits ni pushes.

Pendientes: aprobar esta auditoría antes de seguridad distribuida; migración
persistente requiere autorización separada. Mantener la decisión explícita sobre
revocación/rotación con solapamiento, rate limiting y TLS. Los permisos DDL
limitados siguen siendo necesarios mientras el perfil Docker use Hibernate update;
su retirada requiere adoptar y validar una estrategia de esquema administrado.
El preflight no elimina la posibilidad de fallos DDL parciales o carrera entre
operadores: no hay reparación ni reejecución automáticas.

Fuentes primarias para los componentes auditados:
[RegisteredClientRepository y configuración OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/configuration-model.html),
[privilegios MySQL 8.4](https://dev.mysql.com/doc/refman/8.4/en/privileges-provided.html).
