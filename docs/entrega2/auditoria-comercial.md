# Implementación 42: auditoría comercial distribuida

## Contrato

Se emiten eventos ECS a stdout con `eventType=AUDIT`, `eventId` UUID v4 nuevo por registro, `timestamp` UTC y `service.name`. Los demás campos son `action`, `auditScope`, `actorType`, `actorVerified`, `resourceType`, `outcome` y, cuando existen, `actorId`, `resourceId`, `operationId`, `resourceState`, `correlationId`, `trace.id`, `span.id`, `initiatorId`, `initiatorType` y `errorCode`.

`eventId` identifica el registro técnico; no es el ID del evento de catálogo. El timestamp se genera al emitir después del commit, no antes de confirmarlo. `SUCCESS`, `PENDING`, `REJECTED` y `FAILED` se interpretan junto con acción y alcance. El registro HTTP y la auditoría comercial son eventos diferentes.

`operationId` es una representación con namespace de un identificador comercial existente, nunca una nueva identidad de operación:

| Operación | Identidad estable |
|---|---|
| Publicación/cancelación | `EVENT:<eventId persistido>` |
| Reserva/confirmación/liberación/vencimiento | `RESERVATION:<idReserva>` |
| Compra/pago/emisión | `PURCHASE:<compraId/orderId persistido>` |
| Devolución/reintegro | `RETURN:<devolucionId>` |
| Login válido/provisionamiento de usuario | `USER:<id de Identidad>` |
| Provisionamiento de cliente técnico | `TECHNICAL_CLIENT:<clientId registrado>` |

Los namespaces evitan confundir IDs numéricos de distintos servicios. El ID propio del pago, reintegro o ticket se conserva en `resourceId` y su tipo. Los intentos sin recurso autorizado o persistido omiten `operationId` y `resourceId`. Tampoco se inventan correlation IDs para CLI o tareas programadas. No se usan claves de idempotencia, correlation IDs ni trace IDs como identidad comercial.

## Actores y seguridad

Los roles auditados son CLIENTE, ORGANIZADOR, ADMINISTRADOR y PERSONAL_ACCESO, derivados de un `JwtAuthenticationToken` autenticado por la seguridad existente. Una credencial técnica validada se registra como SERVICIO. No se interpretan headers de usuario, cuerpos ni claims de tokens que hayan fallado la validación.

Cuando no hay identidad autenticada se registra el proceso informante como `SISTEMA`, `actorVerified=false`, sin `actorId`: esto no atribuye identidad al solicitante anónimo. El scheduler y el provisionamiento CLI controlado se registran como SISTEMA, sin atribuirles un usuario humano. El login exitoso usa el usuario y rol recuperados de MySQL después de verificar hash, contraseña y estado activo; no decodifica sin verificar el token recién emitido para identificar al actor. El login rechazado no registra email ni identidad declarada.

En cancelación, `actorId` identifica al administrador autenticado que ejecuta el intento actual; `initiatorId` conserva el administrador de `cancellationAuthorizedAdminId`, leído del evento bloqueado y persistido. Otro administrador puede reintentar sin reemplazar esa autorización original. Los servicios receptores registran la credencial técnica real. No se agrega un header de identidad humana original: la atribución humana autoritativa permanece en Eventos y se vincula con los saltos por traza y recursos comerciales.

Solo se materializan snapshots de primitivas y enums controlados. No se retienen entidades, DTO, cuerpos, JWT, excepciones ni argumentos completos para callbacks posteriores. La lista ECS permite exclusivamente los campos conocidos. No se agregan etiquetas de métricas con identidades comerciales.

## Cobertura

| Servicio | Acciones |
|---|---|
| Eventos | Publicación local y coordinada, publicación repetida, autorización persistida de cancelación, cancelación local, coordinación y reintentos con iniciador original |
| Inventario | Creación, confirmación, liberación/compensación que cambia inventario, vencimiento y devolución de lugares |
| Compras | Compra persistida, estado local de pago, etapas de compra, resultado de procesamiento, recuperación, replay de pago, solicitud/decisión de devolución, estado de reintegro, avance de devolución y replay de reintegro |
| Entradas | Lote emitido, replay de emisión, anulación de lote o ticket y estado terminal confirmado |
| Identidad | Login exitoso/rechazado, usuarios privilegiados y clientes técnicos provisionados, rechazo de política CLI |
| Todos/Gateway | Intentos HTTP rechazados o fallidos, asociados a la correlación y traza del registro HTTP |

No existe actualmente un servicio de aplicación ni endpoint que cambie `enabled` de un cliente técnico: la entidad expone lectura y las pruebas existentes cambian ese dato mediante SQL aislado. Esas modificaciones SQL externas no pueden auditarse con esta instrumentación de aplicación. No se incorporó un endpoint nuevo ni un interceptor de base de datos; un futuro flujo controlado de habilitación/deshabilitación necesitará su auditoría después del commit.

## Consistencia transaccional

`AuditTx.local` registra `TransactionSynchronization` dentro de la transacción local. `afterCommit` emite el snapshot; un rollback genera `FAILED/TX_ROLLBACK`, sin afirmar que existe el recurso asignado dentro de la transacción fallida. Sin transacción activa, una llamada local no puede afirmar SUCCESS: emite PENDING con `NO_LOCAL_TRANSACTION`. Ese caso se prueba con objetos no administrados y no debe aparecer en la integración real.

Los callbacks solo escriben logs: no consultan repositorios ni llaman a otros servicios. Las pruebas utilizan transacciones JDBC reales sobre H2 para comprobar commit, rollback y `REQUIRES_NEW`. Una transacción interna confirmada puede producir un éxito local aunque la externa falle; ese éxito no significa éxito global.

Una notificación `STATUS_UNKNOWN` tampoco se presenta como rollback confirmado: produce PENDING con `TX_OUTCOME_UNKNOWN`, omitiendo el recurso cuyo commit no se conoce. Una prueba específica simula esa señal del transaction manager.

`AuditTx.completed` se usa en orquestadores que ya recibieron resultados de transacciones locales confirmadas. Si hay una transacción externa activa, también difiere la entrega. Publicar el estado de catálogo o registrar CANCELADO localmente se audita PENDING; el éxito coordinado se emite después de las respuestas requeridas de Inventario, Entradas y Compras.

Un pago APROBADO en `PAYMENT_STATE/LOCAL_TRANSACTION` representa el resultado local del cobro, no una compra terminada. `PAYMENT_PROCESS` informa SUCCESS únicamente cuando la orquestación termina; resultados inciertos permanecen PENDING y un pago rechazado es REJECTED. Los pasos de reintegro y anulación mantienen ese criterio: completar el reintegro local no afirma que terminó toda la devolución.

En cancelación, la devolución nueva nace y se aprueba dentro de una misma transacción. Se audita su estado final APROBADA, evitando registrar PENDIENTE como si hubiese sido un estado confirmado separado.

El scheduler conserva el contexto raíz independiente implementado en 41. Los vencimientos generan eventos por reserva efectivamente expirada, con actor SISTEMA, después del commit. Repetir el scan no produce otra transición ni repone stock nuevamente.

### Límite de entrega

**Stdout después del commit no garantiza entrega durable ante una caída abrupta.** Es posible confirmar la operación y perder el evento antes de escribirlo. No hay outbox, cola, confirmación de entrega ni garantía de orden global entre servicios. Las excepciones normales del logging se aíslan del resultado comercial y se intenta informar `AUDIT_DELIVERY_FAILED`; una caída de la JVM o del propio sink puede impedir también ese aviso. Este incremento no constituye una bitácora inalterable ni durable.

El mecanismo utiliza los callbacks estándar de [Spring TransactionSynchronization](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html).

## Idempotencia

Los reintentos tienen otro `eventId` de auditoría y conservan el mismo `operationId` comercial. PAYMENT_REPLAY, PAYMENT_RECOVERY, REFUND_REPLAY y EVENT_CANCELLATION_RETRY identifican observación/recuperación de operaciones existentes. No se imprimen sus claves de idempotencia. No se agregaron llamadas a la pasarela, cobros, reintegros, cambios de TTL ni escrituras financieras para generar auditoría.

## Validación reproducible

```powershell
node TicketYa/integration-tests/e2/test-security.cjs
node --test TicketYa/integration-tests/e2/isolation.test.cjs TicketYa/integration-tests/e2/security-isolation.test.cjs TicketYa/integration-tests/e2/verify-tracing.test.cjs TicketYa/integration-tests/e2/verify-audit.test.cjs
node TicketYa/integration-tests/e2/build-images.cjs
node TicketYa/integration-tests/e2/run-security.cjs --audit --port 48086
```

`--audit` también activa la comprobación de trazabilidad. Se conserva la topología canónica aislada de 41, con proyecto, claves, credenciales y MySQL propios; no se inicia ni consulta MySQL persistente. El runner conserva en memoria la salida de provisionamiento CLI para verificar sus eventos sin publicarla ni persistir logs completos en el reporte.

Elegir un puerto libre entre 48080 y 48089 para otra corrida; el puerto del ejemplo queda ocupado mientras se conserve funcionando la corrida final. El preflight rechaza un puerto ocupado antes de crear recursos.

La regresión mantiene los circuitos comerciales con JWT reales, negativos de autorización, operaciones idempotentes y pago incierto. Agrega un login rechazado. `verify-audit.cjs` comprueba contrato, unicidad técnica, identidad y traza, presencia de acciones, transiciones financieras no duplicadas, administrador original y correspondencia con consultas SELECT sobre el MySQL de la corrida. Los reportes y datos de prueba permanecen bajo `target`, ignorado por Git.

Las suites JUnit comprueban además falla del sink, saneamiento, rollback, independencia de commits internos, captura del contexto antes de su limpieza, vencimiento SISTEMA y reintento por un administrador distinto. Los cambios previos 39–41 se conservan y se extienden; no se agregan dependencias, versiones, tablas de auditoría ni endpoints administrativos.

## Resultado verificado del 14/11/2026

Las suites finales aprobaron 524 JUnit, sin fallos, errores ni omisiones: Gateway 21, Eventos 51, Inventario 64, Entradas 46, Compras 316 e Identidad 26. Aprobaron 50 pruebas Node: 30 guardas de aislamiento, 10 de trazabilidad y 10 del verificador de auditoría. Las seis imágenes se construyeron correctamente. POM, Java y Spring permanecen sin cambios.

La primera corrida, ticketya-e2-reg-ec01dd2bb5c2e29a (puerto 48085), terminó FAIL en auditoría, después de completar comercio y trazabilidad. Faltaba LOGIN/REJECTED; la inspección también detectó errorCode ausente en las listas JSON de cinco servicios. Se corrigieron ambas omisiones y se agregaron pruebas que las detectan. No se cambió el reporte FAIL ni se borraron sus datos. En el diagnóstico se probaron un rechazo CLI por cliente duplicado y un alta aislada de PERSONAL_ACCESO (usuario 9), sin cambiar sus registros financieros.

La corrida final ticketya-e2-reg-e855b29347fa755f, Gateway 127.0.0.1:48086, terminó PASS: 93 comprobaciones de seguridad y 192 solicitudes comerciales (71 negativas), incluidas 24 consultas concurrentes. Se verificaron 285 eventos de auditoría: 57 SUCCESS, 205 REJECTED y 23 PENDING. No hubo FAILED en este recorrido; rollback y resultado transaccional desconocido se probaron con JUnit.

Se comprobaron cuatro provisionamientos privilegiados y siete clientes técnicos. SELECT confirmó estados de reservas, reintegros, devoluciones y administrador original. La trazabilidad verificó 171 solicitudes distribuidas, 589 registros HTTP y cinco scans independientes. El vencimiento efectivo con actor SISTEMA se comprobó con H2/JUnit; no se esperó el TTL en Docker.

Evidencia financiera: tres intentos de cobro, dos aprobados por 45000 y uno rechazado; dos reintegros distintos por 30000, tres tickets y dos lotes. Repeticiones y recuperación conservaron los conteos. Una prueba CLI posterior rechazó un cliente existente: exit 1 esperado, PRIVILEGED_PROVISION/REJECTED, errorCode PROVISION_REJECTED, actor SISTEMA, traza válida y sin creación de recurso. No modificó datos.

Los 56 contenedores preexistentes conservan ID, nombre, estado, inicio, fin y reinicios; el volumen ticketya-e2-infra_mysql_data mantiene sus metadatos. Los seis persistentes siguen detenidos; no se arrancó ni consultó su MySQL para comparar filas. Los siete componentes de la corrida aprobada permanecen running, cero reinicios y MySQL healthy. La corrida inicial también se conserva. No se eliminaron volúmenes ni hubo commits o pushes.

Git acumulado 39–42: entrega-2, HEAD c822345, 55 archivos versionados modificados y 100 nuevos (155 rutas), staging vacío. Este incremento alcanza 64 rutas; las restantes pendientes anteriores conservan su SHA-256 inicial. Secretos y reportes siguen fuera de Git. Los IDs comerciales pertenecen a su entorno; el futuro colector deberá conservar la etiqueta de proyecto o ambiente.

### Archivos alcanzados por 42

- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/service/EventServiceImpl.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/service/EventWorkflow.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/service/CompraRegistroService.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/service/DevolucionRegistroService.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/service/PagoRegistroService.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/service/PagoService.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/service/ReintegroRegistroService.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/service/TicketAnnulmentService.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/service/TicketBatchWriter.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/service/TicketIssuanceService.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/service/TicketStatusService.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/config/ProvisioningCommand.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/service/IdentityService.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/service/ProvisioningService.java`.
- `TicketYa/identidad_service/src/test/java/ar/edu/ticketya/identidad/IdentityHttpTest.java`.
- `TicketYa/integration-tests/e2/run-security.cjs`.
- `TicketYa/integration-tests/e2/security-smoke.cjs`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/services/DevolucionInventarioService.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/services/ReservaService.java`.
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/integration/ReservaCicloTest.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/Audit.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/AuditTx.java`.
- `TicketYa/EventoCatalogo_Service/src/main/java/grupo22/eventocatalogo_service/observability/HttpLogState.java`.
- `TicketYa/EventoCatalogo_Service/src/main/resources/logging.properties`.
- `TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/observability/AuditCapture.java`.
- `TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/observability/AuditContractTest.java`.
- `TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/observability/AuditTransactionTest.java`.
- `TicketYa/EventoCatalogo_Service/src/test/java/grupo22/eventocatalogo_service/observability/CancellationAuditTest.java`.
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/observability/Audit.java`.
- `TicketYa/api-gateway/src/main/java/ar/edu/ticketya/gateway/observability/HttpLogState.java`.
- `TicketYa/api-gateway/src/main/resources/logging.properties`.
- `TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/observability/AuditCapture.java`.
- `TicketYa/api-gateway/src/test/java/ar/edu/ticketya/gateway/observability/AuditContractTest.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/Audit.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/AuditTx.java`.
- `TicketYa/comprapago_service/src/main/java/ar/edu/unicen/grupo8/comprasservice/observability/HttpLogState.java`.
- `TicketYa/comprapago_service/src/main/resources/logging.properties`.
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/observability/AuditCapture.java`.
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/observability/AuditContractTest.java`.
- `TicketYa/comprapago_service/src/test/java/ar/edu/unicen/grupo8/comprasservice/observability/AuditTransactionTest.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/Audit.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/AuditTx.java`.
- `TicketYa/entradas_service/src/main/java/ar/edu/tudai/ticketya/servicio_entradas/observability/HttpLogState.java`.
- `TicketYa/entradas_service/src/main/resources/logging.properties`.
- `TicketYa/entradas_service/src/test/java/ar/edu/tudai/ticketya/servicio_entradas/observability/AuditCapture.java`.
- `TicketYa/entradas_service/src/test/java/ar/edu/tudai/ticketya/servicio_entradas/observability/AuditContractTest.java`.
- `TicketYa/entradas_service/src/test/java/ar/edu/tudai/ticketya/servicio_entradas/observability/AuditTransactionTest.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/observability/Audit.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/observability/AuditTx.java`.
- `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/observability/HttpLogState.java`.
- `TicketYa/identidad_service/src/main/resources/logging.properties`.
- `TicketYa/identidad_service/src/test/java/ar/edu/ticketya/identidad/observability/AuditCapture.java`.
- `TicketYa/identidad_service/src/test/java/ar/edu/ticketya/identidad/observability/AuditContractTest.java`.
- `TicketYa/identidad_service/src/test/java/ar/edu/ticketya/identidad/observability/AuditTransactionTest.java`.
- `TicketYa/integration-tests/e2/verify-audit.cjs`.
- `TicketYa/integration-tests/e2/verify-audit.test.cjs`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/Audit.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/AuditTx.java`.
- `TicketYa/inventarioReserva_service/src/main/java/example/microservicio_inventario_reservas/observability/HttpLogState.java`.
- `TicketYa/inventarioReserva_service/src/main/resources/logging.properties`.
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/observability/AuditCapture.java`.
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/observability/AuditContractTest.java`.
- `TicketYa/inventarioReserva_service/src/test/java/example/microservicio_inventario_reservas/observability/AuditTransactionTest.java`.
- `docs/entrega2/auditoria-comercial.md`.
