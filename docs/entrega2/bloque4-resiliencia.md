# Bloque 4 — Resiliencia y validación

## Protecciones y límites

Resilience4j programático protege lecturas, confirmación de reservas, emisión y coordinación de Eventos. Los circuitos se separan por dependencia/familia; no hay reintentos automáticos de escrituras ni fallbacks comerciales. OAuth2 conserva tokens válidos en caché, limita renovaciones y mantiene scopes y audiencias.

Valores iniciales: ventana de 20 llamadas, mínimo 10, umbral técnico 50 %, OPEN 10 s, dos llamadas reales HALF_OPEN. Semáforos comerciales de ocho llamadas, sin espera. OAuth2: dos adquisiciones simultáneas y espera de exclusión por destino de hasta 500 ms. Son límites configurables, no valores óptimos de producción.

Presupuesto HTTP interno de 12 s y Gateway de 15 s; cada salto solo puede reducirlo. Transporte: conexión/TLS 1 s, lectura GET 2 s, escritura 4 s, token 3 s y adquisición de conexión 500 ms. No se cancela un commit financiero mediante un timeout de socket JDBC.

## Clasificación y recuperación

401/403 técnicos no son rechazos comerciales y no habilitan operaciones; tampoco alimentan el circuito como éxitos. 404/409/422 dependen del contrato. 429, 5xx y transporte son fallos técnicos. Una respuesta vacía o inválida no confirma éxito ni ausencia.

Circuito abierto o semáforo lleno rechazan antes del despacho. Una escritura enviada sin respuesta válida tiene resultado incierto. Ambos conservan etapas recuperables sin compensaciones automáticas. La recuperación consulta resultados y reutiliza identificadores originales de pago, reintegro, reserva y emisión; mantiene el administrador originalmente autorizado para cancelar. No se agregaron estados persistidos.

## Campaña reproducible y aislada

Con Docker operativo e imágenes actuales:

```powershell
node TicketYa/integration-tests/e2/test-security.cjs EventoCatalogo_Service
node TicketYa/integration-tests/e2/test-security.cjs comprapago_service
node TicketYa/integration-tests/e2/test-security.cjs entradas_service
node --test TicketYa/integration-tests/e2/*.test.cjs
node TicketYa/integration-tests/e2/run-security.cjs --centralized --faults --port 48083
```

El runner crea un único proyecto aleatorio, con esquemas, claves, red y volúmenes propios; no usa las bases persistentes. Las fallas Docker solo afectan Compras e Identidad del proyecto recién creado. No elimina recursos al terminar. Reportes y claves quedan en `integration-tests/target`, excluido de Git.

La ráfaga de 24 lecturas mantiene ocho llamadas ocupadas pausando brevemente Compras. Cada 503 debe coincidir por correlationId con BULKHEAD_FULL; exigir solo HTTP 200 o reducir la ráfaga no demuestra saturación. Luego se detiene únicamente Compras aislado para observar OPEN y recuperación mediante dos GET reales. Se comprueba caché OAuth2 con Identidad aislada detenida y concurrencia de ocho repeticiones explícitas de pago y de reintegro ya procesados.

Snapshots SQL de lectura comparan estados, identificadores, referencias, operaciones de pasarela, entradas y autoridad original antes/después de cada falla. El flujo completo verifica publicación, reserva, compra, pago, emisión, devolución parcial, cancelación, rechazo y recuperación incierta con JWT reales; los verificadores existentes validan auditoría y trazas.

## Evidencia y limitaciones

Implementación 51: 648 JUnit (Eventos 109, Compras 433, Entradas 106), 65 Node, 93 comprobaciones HTTP de seguridad y una campaña de 249 solicitudes comerciales (99 negativas). Auditoría: 357 eventos y estados persistidos verificados; trazabilidad: 228 solicitudes distribuidas. Ráfaga de 24: ocho respuestas válidas y 16 rechazos BULKHEAD_FULL correlacionados. Dependencia detenida: 12 rechazos y transición OPEN → HALF_OPEN → CLOSED con dos lecturas reales. OAuth2 funcionó con token válido durante la caída de Identidad. Ocho repeticiones concurrentes de pago y ocho de reintegro conservaron identificadores y snapshots.

Integridad final: dos cobros aprobados por 45000, un rechazo, dos reintegros por 30000, tres entradas y dos lotes. Sin duplicados ni compensaciones indebidas; administrador original conservado.

La campaña inicial falló únicamente en la comprobación de entrega completa al archivo central: se superpusieron reinicios de Compras/Identidad con la caída del colector, perdiendo 78 registros de sus colas asíncronas (71/7). Los registros originales permanecen en docker logs y la auditoría local aprobó. No se reparó ni ocultó ese resultado. Se corrigió el orden del runner: recuperar/verificar la entrega del colector antes de inyectar fallos de aplicaciones. Solo se repitió la comprobación afectada en el mismo proyecto, sin repetir el negocio: seis servicios, siete solicitudes HTTP y seis eventos de auditoría nuevos entregados, sin cambios financieros. La evidencia previa incompleta continúa conservada.

Evidencia ignorada por Git: target/security/64af3977e7df15b7/report.json (campaña y falla inicial), fault-logging-check.json (repetición específica aprobada) y target/resilience51/node-tests.log. Para repetir exclusivamente la comprobación de entrega afectada de esa corrida: `node TicketYa/integration-tests/e2/verify-fault-logging.cjs 64af3977e7df15b7 48083`. Registra un cliente aislado y ejecuta GET; no realiza escrituras comerciales.

429/503 controlados, timeouts posteriores a escritura, respuestas perdidas, recuperación parcial, tokens vencidos, renovación concurrente y ausencia de retry se prueban determinísticamente con las suites JUnit existentes. La campaña Docker complementa esa evidencia con fallos reales de disponibilidad y saturación; no simula todos los fallos en cada enlace ni constituye un benchmark de producción. La centralización mantiene entrega de mejor esfuerzo. Los recursos aislados se conservan para auditoría; el runner no los limpia ni modifica el entorno persistente.
