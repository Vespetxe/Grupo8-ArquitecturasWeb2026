# Correcciones focalizadas: cupo, demo y RAM

Base: `arqui/TicketYa2`, commit `c0e9eb5`. Rama local
`codex/correcciones-entrega2`; sin commits ni push.

## Cupo de reservas

El PDF oficial, punto 4, exige limitar entradas por cliente/evento, sin imponer
un número. El diseño y Compras ya fijan seis por defecto. Inventario ahora
aplica `reservas.limite-entradas-por-cliente-evento` (6) al crear una reserva.
Si se configura otro valor, mantenerlo igual a
`compras.limite-entradas-por-cliente-evento` en Compras.

Cupo ocupado = cantidades CONFIRMADAS + PENDIENTES con vencimiento futuro
menos lugares devueltos persistidos de las CONFIRMADAS. LIBERADA y EXPIRADA
no consumen cupo; tampoco una PENDIENTE vencida aunque el scheduler aún no haya
liberado su stock. No se suman compras a reservas: sería contar dos veces la
misma selección. Una compra pendiente no es venta: conserva únicamente el cupo
de su reserva vigente. La confirmación conserva la cantidad, la compensación
libera y la devolución parcial devuelve solo sus unidades, de forma idempotente.
Una confirmación sigue consumiendo cupo durante resultados financieros inciertos;
no se consulta ni se presume un cobro por timeout.

Antes de contar se bloquea la primera fila de inventario del evento, ordenada
por sector, y luego el sector solicitado. Todas las altas toman el mismo ancla,
también si no hay control de cola. READ_COMMITTED evita contar sobre un snapshot
InnoDB anterior al bloqueo. No se bloquean filas de reserva al contar, evitando
invertir el orden reserva→sector de confirmaciones/devoluciones. La contención
queda por evento (simplicidad frente a granularidad); no hay nuevas tablas.
El rechazo del cupo usa la validación 400 existente. No cambian DTO, rutas,
OAuth2, pagos ni contratos entre microservicios.

## macOS y confidencialidad

La versión base monta `mysql-init.sh`; no se encontraron las correcciones de
Valentina en las referencias disponibles. No se importó ni leyó su runtime.
Las demos nuevas marcan `mysqlInitializer: sql` y generan un SQL privado por
computadora, con los mismos cinco esquemas/usuarios y permisos mínimos.
`NO_BACKSLASH_ESCAPES` y patrones de esquema escapados evitan permisos cruzados.
MySQL procesa `.sql` por lectura, evitando el permiso de ejecución del archivo
compartido. El padre privado protege el SQL en el host; el bind es solo lectura
y legible por MySQL. No se incluyen contraseñas en archivos versionados.

La definición anterior sigue disponible exclusivamente para runtimes previos.
No se reescriben sus manifiestos/credenciales ni se recrean recursos existentes.
El patrón `/runtime*/` también excluye `runtime-macfix` y su archivo de accesos.
El ensayo en Docker Linux/Windows no prueba por sí solo Docker Desktop en macOS.

## RAM: hechos y medidas conservadoras

Ocho servicios son necesarios para la demo completa; no incluye colector ni
frontend dentro de Docker. El arranque ya reutiliza su runtime y bloquea proyectos
parciales; no inicia copias si está ready. Los contenedores CLI finalizados no
explican consumo de una JVM activa. No se borran para intentar reparar RAM.

Se conserva el presupuesto previo (768 MiB por contenedor, suma de límites 6 GiB)
y el margen estimado de preflight de 7 GiB. Se secuencian las cuatro JVM de negocio
para reducir superposición de picos de arranque. Los diagnósticos incluyen
exit code, OOMKilled y deadline; 137 por sí solo no demuestra OOM. No se cambiaron
heap, recursos globales de Desktop ni límites arbitrarios.

Medición local: Docker ofrece 15,44 GiB y al abrirlo no había servicios activos.
Una JVM de la imagen de Inventario, sin Spring y con límite de 768 MiB, informó
heap máximo estimado de 185,62 MiB. Es heap, no RSS ni consumo de ocho servicios.
**No está confirmada la causa de los fallos de Aarón.** Solicitar en su equipo
`docker stats --no-stream`, estado/exit/OOMKilled de contenedores exactos, memoria
asignada y qué proyectos estaban activos. No imprimir `.Config.Env` ni secretos.

## Validación y repetición segura

Inventario: `mvn test` en su proyecto con JDK 21/Maven; guarda de bases sin cambios.
La suite usa H2 en memoria por defecto. `LimiteReservasTest` cubre 11 casos,
incluida concurrencia entre sectores sin fila de control de cola. Su limpieza
elimina únicamente registros del evento aleatorio creado por cada caso.

Offline:
`node --test demo/demo.test.cjs TicketYa/integration-tests/e2/environment.test.cjs TicketYa/integration-tests/e2/provisioning.test.cjs`.
Frontend: `npm --prefix TicketYa/frontend test` y `npm --prefix TicketYa/frontend run build`.

Prueba MySQL optativa (requiere imágenes locales `mysql:8.4.8` y
`ticketya-tests:java21-node22` y Docker Linux):

```powershell
$env:TY_DEMO_MYSQL_TEST='1'
node --test demo/mysql-portability.test.cjs
Remove-Item Env:TY_DEMO_MYSQL_TEST
```

Crea un único MySQL nuevo, red y volumen propios, sin puertos publicados.
Prueba el inicializador real y acceso propio/denegación a otros cuatro esquemas.
Un sexto esquema solo de JUnit (`ticketya_inventario_test`) permite ejecutar los
11 casos sin relajar la guarda existente: JVM y MySQL comparten únicamente la
red de ESTE contenedor, usando localhost:33079. El permiso DROP se concede solo
al usuario del esquema descartable de pruebas, nunca a usuarios de la demo.
No conecta a un 33079 del host. Los recursos y logs se conservan para auditoría;
no repetirla mientras otra suite escriba el mismo `target` de Inventario.

Pendiente: comprobación nativa en macOS y mediciones del equipo de Aarón. No se
ejecutó una campaña comercial completa ni se modificaron bases persistentes.

### Resultados del 22/11/2026

| Verificación ejecutada | Resultado |
|---|---|
| Compilación y suite Inventario sobre H2 | 92/92, BUILD SUCCESS |
| Los 11 casos de cupo sobre MySQL 8.4.8 nuevo | 11/11, BUILD SUCCESS |
| Prueba SQL real y aislamiento del usuario de Inventario | PASS; cinco esquemas y cuatro accesos ajenos denegados |
| Demo offline | 50/50 |
| Preflight y provisionamiento offline | 23 aprobadas; prueba de UID Docker omitida explícitamente en esta campaña |
| Frontend unitarias y build | 40/40; build aprobado |
| Imagen de Inventario | Construcción multietapa aprobada; no se inició la aplicación |
| `git diff --check` | Sin errores |

Los primeros intentos de JUnit identificaron contaminación entre fixtures y un
caso de stock que pedía diez entradas (ahora viola antes el cupo). Se ajustaron
los fixtures y su limpieza propia, y se conservó la guarda de URL sin cambios.
No se relajó la seguridad para permitir una nueva base de pruebas.

El MySQL aislado conservado es
`ticketya-demo-check-bbb1d2cac67b33c1-mysql`, con su red y volumen exclusivos.
Después de JUnit se observó 495,2 MiB / 768 MiB; no representa una demo completa.
Los resultados están en `TicketYa/integration-tests/target/demo-mysql/<id>/` y
los logs de suite en el `target` de Inventario, fuera de Git. No se eliminaron
recursos. Detener este MySQL, si se decide liberar RAM después de la auditoría,
requiere seleccionar ese nombre exacto; no usar limpieza global.

Para que una nueva demo use la corrección de cupo, reconstruir solo
`ticketya-e2-inventario:dev` desde `TicketYa/inventarioReserva_service`; ya se
hizo en esta máquina. Los contenedores preexistentes no se recrearon.
