> Actualización de cierre: el modo combinado --centralized junto a los flags Mongo genera un Compose de nueve servicios. Usa loopback 38224–38233 y subredes explícitas; se verificó coexistencia mínima Mongo autenticado + colector + un registro técnico. La campaña de correcciones finales amplía esa evidencia con nueve servicios, negocio y recuperación React; ver [auditoría documental](auditoria-documental.md). No se reejecuta cada fallo histórico del colector. En este modo el colector permanece activo durante reinicios de dependencias; las pruebas de caída/recuperación descritas abajo corresponden al modo central histórico.

# Bloque 3 — Centralización aislada de logs

## Alcance y arquitectura

La opción `--centralized` del runner de seguridad extiende su Compose canónico
aislado. No modifica ni inicia `ticketya-e2-infra`. Conserva los seis servicios
Java, MySQL propio, JWT real y las comprobaciones comerciales existentes.

Flujo: stdout ECS → driver Docker `fluentd` asíncrono → Forward TCP de Fluent Bit
4.2.3 → HTTP **loopback del propio contenedor** → archivo JSONL segmentado.
El pequeño adaptador usa únicamente la biblioteca estándar Python incluida en
la imagen oficial `4.2.3-debug`, fijada por digest. No agrega una plataforma de
almacenamiento ni dependencias pip. Es necesario porque el output `file` de
Fluent Bit no proporciona por sí mismo retención y tamaño máximo del archivo.

El driver recibe `127.0.0.1:49224+(puertoGateway-48080)`, no un nombre DNS Compose.
La dirección se interpreta desde el **daemon Docker**, no desde la aplicación.
El sondeo prueba ese recorrido real en Docker Desktop antes de usarlo. En otro
daemon debe volver a verificarse; no se cambia la configuración global de Docker.
El runner rechaza destinos ya asignados a cualquier contenedor, incluso detenido,
además de comprobar que el puerto esté libre. Esto evita que productores de un
proyecto anterior entreguen su buffer a un colector de otro proyecto.

No hay dependencia de arranque hacia el colector. Los seis servicios arrancan
antes que él. La regresión comercial completa se realiza con el colector detenido,
se recupera y se exige recibir todos los eventos de auditoría de esos servicios.
La caché dual acotada conserva `docker logs`; las verificaciones existentes siguen
leyéndola, sin sustituirla por el archivo central.

## Aislamiento y seguridad

- Colector UID/GID `10001:10001`, raíz de solo lectura, sin capabilities,
  `no-new-privileges`, 192 MiB RAM, 0,5 CPU, límite de procesos y tmpfs acotado.
- Una red propia `logs`, sin conexión a `backend`, sin secretos, sin claves RSA,
  sin `docker.sock` y sin acceso a ningún volumen MySQL.
- Solo Forward TCP se publica en **127.0.0.1**, nunca en todas las interfaces.
  El archivo y su API HTTP de ingestión/salud no tienen puerto publicado.
- Logs del propio colector: driver `local`, dos archivos de 2 MiB; no se realimentan.
- El envelope Docker se descarta. No se copian `env`, labels ni encabezados.
  Se conservan los campos ECS y del contrato 39–42 mediante lista permitida.
  Se rechazan mensajes con patrones de JWT, clave privada o asignaciones de
  credenciales. No se imprimen registros rechazados ni cuerpos de errores.
- La política de origen sigue siendo esencial: detectar patrones no permite
  reconocer cualquier secreto arbitrario. Las pruebas revisan además las
  credenciales reales generadas para esa ejecución y tokens con forma JWT.

Forward no tiene autenticación criptográfica en esta etapa: el daemon, la red
privada del colector y los usuarios locales con acceso a Docker son de confianza.
Un usuario local puede intentar inyectar registros; estos nunca son autoridad
de identidad ni una fuente de decisiones financieras. No se ofrece acceso remoto.
El archivo contiene IDs de actor/recurso; su acceso requiere permisos Docker.

## Persistencia, límites y retención

Cada proyecto recibe un volumen nuevo `<proyecto>_logs_data`, independiente de
`<proyecto>_mysql_data`. No se eliminan volúmenes automáticamente.

| Capa | Límite |
| --- | --- |
| Buffer no bloqueante del driver, por aplicación | 4 MiB |
| Cola asíncrona Forward, por aplicación | 1024 eventos |
| Caché dual, por aplicación | 2 archivos de 4 MiB |
| Cola filesystem Fluent Bit | 8 MiB por output, chunks y metadatos adicionales |
| Archivo central | 64 MiB de contenido JSONL total |
| Segmento | 1 MiB máximo |
| Registro admitido | 64 KiB máximo |
| Lote HTTP | 2 MiB máximo |
| Retención | 24 horas desde ingestión local |

Antes de escribir se eliminan únicamente los segmentos propios más antiguos
necesarios para mantener el límite. Se usa creación exclusiva, append, flush y
fsync; reiniciar no trunca segmentos anteriores. Una tarea cada segundo vence
segmentos por mtime local, sin confiar en timestamps suministrados por el emisor.
Se rota también cada 60 segundos: la granularidad de retención puede prolongar
la vida del primer registro de un segmento hasta unos 60 segundos adicionales.
No se toca ningún archivo ajeno al patrón `segment-[0-9]+.jsonl` ni symlinks.

El límite JSONL se comprueba con archivos reales. **No es una cuota física del
volumen**: existen metadatos del filesystem, chunks de Fluent Bit y posibles
redondeos por chunk; el espacio total no equivale exactamente a 64+8 MiB.
Los proyectos de pruebas conservados acumulan volúmenes independientes. Hay que
revisar su ocupación operativamente; este runner no elimina recursos anteriores.

Las métricas de aceptación/rechazo/evicción son contadores del proceso actual;
el contenido persiste, los contadores se reinician. `eventId` original permite
deduplicar auditorías en una ventana de hasta 100000 IDs, reconstruida desde los
segmentos retenidos al arrancar. No se inventan eventos comerciales. Ni stdout
ni esta canalización garantizan entrega exactamente una vez: una caída larga,
saturación, disco lleno o pérdida del daemon puede perder registros. Los servicios
continúan; no se introduce un outbox ni se promete auditoría financiera duradera.
Un último registro incompleto tras un corte abrupto se ignora al consultar.

## Ejecución reproducible (PowerShell, raíz del repositorio)

```powershell
node TicketYa/integration-tests/e2/build-images.cjs
docker build --progress=plain --tag ticketya-e2-logs:dev docker/observability
node --test TicketYa/integration-tests/e2/*.test.cjs
node TicketYa/integration-tests/e2/test-security.cjs
node TicketYa/integration-tests/e2/test-collector.cjs
node TicketYa/integration-tests/e2/run-security.cjs --centralized --port 48087
```

Se necesita Docker Desktop operativo y autorización de acceso a Docker/loopback
desde el proceso ejecutor. No se requiere el `.env` persistente. Los secretos y
claves son nuevos y los archivos locales del runner permanecen bajo `target`,
ignorado por Git y con sus guardas de ACL existentes. Los reportes no contienen
tokens ni contraseñas. El puerto Gateway debe estar libre, en 48080–48089, y su
puerto de logging derivado no debe estar asignado a otro contenedor.

`test-collector.cjs` genera un proyecto `ticketya-e2-logs-probe-<id>`, volumen nuevo,
puerto loopback exclusivo, emisor sin red y límites reducidos (32 KiB, segmentos
de 4 KiB, TTL 20 segundos). Prueba recepción desde el daemon, arranque sin
colector, caché dual, caída/recuperación, límite real, lectura independiente del
volumen detenido, reinicio y expiración real. Al aprobar detiene sus propios
contenedores y conserva sus recursos. Si falla los conserva para diagnóstico;
no detiene otros proyectos. No se deben ejecutar dos regresiones simultáneas:
las guardas también comprueban que los contenedores anteriores no cambiaron.

## Consultas sin API pública

Sustituir `<proyecto>` y el identificador con valores del reporte propio:

```powershell
docker exec <proyecto>-collector-1 python3 /collector/archive.py --query correlationId --value <uuid> --limit 100
docker exec <proyecto>-collector-1 python3 /collector/archive.py --query traceId --value <trace-id> --limit 100
docker exec <proyecto>-collector-1 python3 /collector/archive.py --query operationId --value <operation-id> --limit 100
```

Las consultas son estrictamente de lectura. `traceId` consulta el campo ECS
`trace.id`, no modifica el contrato existente. No hay UI ni índice externo;
se recorren segmentos acotados. Para diagnóstico se conserva `docker logs`.
No copiar salidas completas a documentación o a archivos versionados.

## Criterios de aceptación

El reporte de la ejecución centralizada exige seis servicios recibidos, JSON
válido, consultas por los tres identificadores, ausencia de credenciales conocidas,
auditorías sin duplicación de `eventId`, todos los eventos fuente presentes,
trazas distribuidas correctas y tamaño bajo el límite. Además conserva todas las
verificaciones JWT, propiedad, idempotencia, concurrencia y SQL financiero del
runner. La persistencia y expiración se demuestran por separado en el sondeo.
Los one-off CLI de provisionamiento tienen su verificación original en memoria;
no se garantiza vaciar su cola asíncrona al terminar inmediatamente el proceso.
La obligación de recepción centralizada abarca los seis servicios de larga vida.

## Evidencia de esta implementación — 14/11/2026

Ejecutado durante la reanudación de la 43, no inferido del reporte anterior:

- JUnit: Gateway 21, Eventos 51, Inventario 64, Entradas 46, Compras 316,
  Identidad 26; total **524**, sin fallos, errores ni omisiones.
- Node: **61** pruebas de aislamiento, seguridad de configuración, trazabilidad,
  auditoría y topología centralizada; todas aprobadas.
- Archivo: **7** pruebas Python, incluidas retención/capacidad con archivos reales,
  deduplicación tras reconstrucción, señuelos sensibles y entradas malformadas.
- Sondeo `ticketya-e2-logs-probe-a8404af83dc446a3`: PASS; recepción Forward real,
  caché Docker, arranque sin colector, caída, recuperación y lectura independiente
  del volumen detenido. Archivo máximo observado 32580 bytes con cap 32768;
  225 segmentos desalojados por capacidad tras recuperación; luego vencieron los
  9 segmentos restantes y el archivo llegó a **0 bytes** con TTL de prueba de 20 s.
  No se esperaron 24 horas para ensayar el valor predeterminado.
- Regresión `ticketya-e2-reg-367914aed40c67fc`, Gateway 48087, Forward 49231:
  PASS; **93** comprobaciones HTTP JWT, **192** solicitudes comerciales,
  incluidas **71** negativas y **24** concurrentes con tres actores.
- Los 1006 registros centralizados examinados incluyen los seis servicios y
  **274 auditorías**, sin duplicación de eventId y con todos los eventos de los
  seis contenedores fuente presentes. Las **285** auditorías de la comprobación
  global incluyen además 11 provisionamientos CLI verificados en memoria.
- Consultas por correlationId, traceId y operationId aprobadas; **171** solicitudes
  distribuidas verificadas también desde el archivo central. La comprobación de
  credenciales generadas y JWT no encontró exposición.
- Consistencia SQL aislada: tres intentos de cobro, dos aprobados por 45000 en
  total, uno rechazado; dos reintegros por 30000; tres tickets y dos lotes.
  Idempotencia de pago, devolución y cancelación, recuperación de pago incierto,
  propiedad y autoridad administrativa original verificadas.
- Los 70 contenedores de la línea de base conservaron ID, estado, instantes de
  arranque/cierre y reinicios. Los seis persistentes continuaron detenidos y el
  volumen `ticketya-e2-infra_mysql_data` conservó su identidad y fecha de creación.
  No se consultaron sus tablas ni se ejecutaron migraciones persistentes.

Los primeros sondeos detectaron un intervalo sin unidad incompatible con Docker
29.7.2 y una prueba de persistencia afectada por emisores previos que reutilizaban
el puerto. Se corrigieron la unidad y las guardas de destino exclusivo. Sus
volúmenes se conservaron para diagnóstico; no se borraron recursos. El sondeo
aprobado y la regresión comercial posterior son las evidencias válidas.

Los reportes detallados, logs de pruebas y claves locales permanecen bajo
`TicketYa/integration-tests/target`, excluido de Git. Las evidencias de la 42
solo se revisaron como antecedente; los resultados anteriores son ejecuciones
nuevas de esta reanudación. No se hizo commit ni push.
