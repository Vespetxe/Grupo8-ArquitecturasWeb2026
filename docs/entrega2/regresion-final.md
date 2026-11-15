# Regresión integral y carga — Bloque 11

Fecha: 15/11/2026. Código inicial: 15abfc235000184b95cf625eb1f8ae826303b240, rama entrega-2. No se incorporaron funciones de negocio ni cambios Java. No se reconstruyeron imágenes: las seis imágenes actuales ya correspondían a esas fuentes.

## Reproducir

Desde un clon nuevo, seguir el README (builder de seis imágenes y npm ci en frontend). Con Docker Linux y puertos 48084/5174 libres:

~~~powershell
node TicketYa/integration-tests/e2/run-security.cjs --reports --notifications --queue --qr --cache --faults --audit --load --frontend --port 48084
~~~

Se genera una topología canónica nueva, con esquemas, volumen MySQL, volumen Mongo, red, claves, contraseñas y sujetos propios. No acepta JDBC ni proyecto persistente como argumentos. Las comprobaciones de topología y la comparación de estados anteriores son obligatorias. Las escrituras de negocio pasan por Gateway; SQL se limita a lecturas de los esquemas nuevos, salvo el bootstrap inicial autorizado. Se conserva cada recurso Docker creado, incluidos jobs nuevos de provisionamiento y sondeo.

## Ejecutado ahora

| Comprobación | Resultado |
|---|---|
| Proyecto | ticketya-e2-reg-d83ba9f6a214f437 |
| Gateway | 127.0.0.1:48084; únicos puertos backend publicados |
| Seguridad HTTP | 93 comprobaciones; JWT/roles/propiedad y enlaces OAuth2 |
| HTTP comercial registrado | 499 solicitudes; incluye 43 de la carga |
| Reportes | 17 instantáneas, con devoluciones, neto incierto y recuperación |
| Notificaciones | Push SSE autenticado, propiedad, favorito/ciudad, cambio/cancelación y outbox recuperado |
| Mongo | Persistencia y preferencias aisladas; compras completas con Mongo detenido; degradación explícita sin Compras |
| Caché | Consultas públicas repetidas, invalidación por cambios y rechazo de escritura al cliente |
| FIFO | Tres usuarios y turnos únicos; carrera por último lugar: una aceptación y un conflicto |
| QR backend | Dos escaneos concurrentes: una aceptación y un rechazo; anulado, ajeno, inválido, revocación y conservación del ingreso tras cancelar |
| QR React | Login PERSONAL_ACCESO real, asignación por administrador nuevo, aceptación una vez y rechazos 409/400 posteriores |
| Auditoría | 486 eventos y estados financieros verificados antes de la carga adicional |
| Trazabilidad | 405 solicitudes distribuidas verificadas antes de la carga adicional |
| Node | 73 casos aprobados en la verificación Node final, sin fallos |
| Recursos anteriores | Snapshot de todos los contenedores previos sin cambios durante la corrida aprobada |

El total comercial no incluye los 93 sondeos de seguridad ni cada HTTP adicional que emite el navegador. No se suman como si fueran una única muestra de carga. Las escrituras de negocio no tienen retry automático: las repeticiones de pago/reintegro son explícitas y conservan sus claves.

## Fallos controlados

- Compras detenida: doce lecturas 503 y OPEN → HALF_OPEN → CLOSED, sin despacho mientras OPEN. Recuperación con dos lecturas reales.
- Saturación: ráfaga de 24 lecturas, ocho éxitos y dieciséis 503 correlacionados con BULKHEAD_FULL. No se redujo la ráfaga para obtener PASS. Snapshots financieros sin cambios y lectura posterior correcta.
- Identidad inaccesible: se conservaron los tokens técnicos válidos en caché y la recuperación. La aceptación de tokens vencidos y renovación concurrente se apoyan en JUnit previos, no se fingió una nueva prueba de expiración en esta campaña.
- Pago incierto: resultado de pasarela ya persistido, recuperación con mismo pago/referencia/lote y sin segundo cobro.
- Ocho repeticiones concurrentes de pago y ocho de reintegro: mismos identificadores, autoridad original y snapshot financiero.
- Caída Mongo: publicación, reservas, pagos, emisión, devolución y cancelación siguen operativos. Preferencias vuelven después de recuperar Mongo.
- Caída de Compras durante publicación de un artista: outbox pendiente, entrega después de recuperar y sin avisos duplicados.

429, 500/502/503/504 por contrato, timeout posterior a escritura y respuesta perdida se cubren determinísticamente con las evidencias JUnit anteriores del Bloque 4; no se inyectó cada variante en cada enlace Docker nuevamente. El escenario simular-timeout de pago es la pasarela simulada existente, no una caída real de una pasarela externa.

## Carga acotada

Se creó un evento GENERAL nuevo con capacidad dos y precio unitario 15000. Doce solicitudes simultáneas de reserva, luego compra/pago real de las dos reservas aceptadas y ocho replays explícitos con las mismas claves. Tres identidades de cliente; no doce compradores distintos.

| Medida | Valor |
|---|---|
| Solicitudes de la cohorte (incluye preparación y consultas) | 43 |
| Respuestas exitosas | 29 |
| Rechazos esperados | 14 (10 por stock; 4 por propiedad en preparación) |
| Errores inesperados | 0 |
| Reservas concurrentes | 12: 2 aceptadas, 10 conflictos de stock, 0 rechazos técnicos |
| Latencia HTTP p50 | 29.73 ms |
| Latencia HTTP p95 | 279.56 ms |
| Latencia HTTP máxima | 513.38 ms |
| Cobros adicionales aprobados | 2, total 30000 |
| Entradas/lotes adicionales | 2/2 |
| Duplicados / sobreventa | 0/0 |

Las latencias se miden en el cliente desde el envío HTTP hasta recibir/parsing de la respuesta, sin incluir los procesos Docker de acreditación previos. Incluyen la planificación local del cliente. No son SLA ni comparación con producción. La prueba de saturación de 24 lecturas se reporta por separado y no se mezcla con los conflictos de stock.

Integridad base: tres intentos de cobro, dos aprobados por 45000, uno rechazado; dos reintegros por 30000; tres tickets/dos lotes. La carga agrega dos cobros/tickets/lotes, sin cambiar los registros anteriores. La UI QR solo registra el ingreso: no cambia estado comercial de tickets ni pagos/reintegros. Totales al terminar: cinco pagos/intentos; cuatro cobros aprobados históricamente y uno rechazado. Estados de pago actuales: tres APROBADO, uno REINTEGRADO_TOTAL y uno RECHAZADO. Hay cinco tickets y cuatro lotes.

Comprobación posterior de solo lectura, incluyendo carga y QR React: contrato de auditoría aprobado con 542 eventos de servidores, 450 solicitudes distribuidas verificadas y escaneo de secretos conocidos aprobado. No incluye las líneas de provisionamiento CLI del contador inicial. Los totales SQL finales coinciden con los anteriores. Evidencia: target/delivery/post-campaign-verification.json.

## Evidencia reutilizada (no reejecutada)

- Frontend Bloque 12: instalación/build, trece unitarias, seis escenarios distintos de navegador y compra real única. El Bloque 11 ejecutó un escenario adicional de navegador QR aceptado, no repitió esas seis pantallas completas.
- JUnit guardados en target/surefire-reports de los seis servicios: XML acumulados sin errores/fallos registrados, 1262 casos sumados de distintas ejecuciones. Este número NO representa una suite ejecutada ahora ni una única campaña homogénea.
- Caché: TTL, límites, invalidación post-commit, rollback y carrera de carga tardía; código productivo sin cambios desde su aprobación.
- Centralización histórica ticketya-e2-reg-d6d81f83c25269e2: 1061 registros de seis servicios, 274 eventos de auditoría archivados, arranque sin colector, caída/recuperación y límites/retención. El perfil de colector no se combinó con Mongo en esta corrida. Es entrega de mejor esfuerzo: puede perder eventos durante interrupciones y reinicios.

## Defectos de pruebas/documentación corregidos

La primera combinación integral (ticketya-e2-reg-e0c06b5f0472ceaa) se detuvo en 351 solicitudes porque el helper de creación exigía un reporte 200 mientras Notificaciones detenía deliberadamente Compras. El backend devolvió correctamente 503. Se corrigió la expectativa del helper para ese contexto, sin ocultar la indisponibilidad, cambiar el backend ni duplicar estados. Se conservó la evidencia fallida y se detuvieron solo sus ocho servicios nuevos para liberar memoria. La campaña corregida se ejecutó en otro proyecto nuevo. El resumen de carga distingue rechazos de propiedad esperados de errores inesperados y no interpreta cualquier 409 como rechazo financiero.

También se actualizaron README, matriz, OpenAPI/colección y el pendiente QR del frontend. No se encontró un defecto financiero productivo que requiriera cambios.

Las reservas pendientes del caso FIFO pueden vencer naturalmente después de su snapshot; el entorno conserva sus schedulers y TTL originales. Esto no cambia el resultado registrado de la carrera.

## Archivos de evidencia y límites

Resultados privados completos: TicketYa/integration-tests/target/security/d83ba9f6a214f437/report.json y target/delivery; se excluyen de Git. frontend-qr-private.json tiene únicamente credenciales y QR aleatorios de prueba en el directorio protegido de la corrida: no distribuirlo. Las claves privadas y cualquier secreto real siguen ignorados. El informe versionado contiene solo resultados/IDs no secretos.

El Compose raíz se validó con secretos ficticios en el entorno del proceso, sin leer .env: ocho servicios, solo Gateway loopback, sin recursos externos. No se activó. La auditoría posterior verificó configuración conjunta y coexistencia mínima de colector/Mongo; se conserva como límite la falta de una campaña completa de nueve procesos y de una réplica física adicional. La escala de producción no se acredita con esta carga. El frontend no es un servidor de producción ni incorpora TLS. La implementación original se entregó sin commit ni push; el cierre local autorizado se registra en Git. No se realizó push.

## Auditoría de cierre: los dos límites

La separación de flags --centralized/Mongo era una restricción del runner; se compusieron las configuraciones existentes en mongo-central-isolation.cjs, conservando aislamiento, autenticación, límites y logging asíncrono. El modo conjunto mantiene el colector activo durante reinicios de dependencias y no declara haber probado una caída del colector que no ejecutó. La prueba histórica de esa caída sigue vigente. No se incorporaron componentes funcionales ni dependencias.

Prueba mínima: ticketya-e2-reg-8e5a8f4c8fe5cd23, PASS. Un solo manifiesto válido de nueve servicios; se iniciaron únicamente Mongo, el colector existente y un productor técnico. Mongo autenticado y ambos healthchecks correctos; un registro técnico archivado en volumen propio y conservado también en docker logs. No se emitió una auditoría comercial ficticia ni se ejecutaron reservas/pagos. Todos los contenedores previos conservaron su snapshot. Evidencia: target/security/8e5a8f4c8fe5cd23/coexistence-report.json.

Los intentos previos mínimos quedaron conservados: el primero no completó la red de logs, y el segundo arrancó Mongo pero Windows rechazó 49225. La consulta de exclusiones confirmó la reserva de 49152–49551. Se usaron subredes explícitas, comprobación de solapamiento y loopback 38224–38233, sin modificar redes o reservas del sistema. No se eliminaron recursos. La primera causa de red no se registró con detalle suficiente para afirmar un diagnóstico definitivo.

Se aprobaron 20 pruebas Node focalizadas de topología/guardas, incluyendo cinco nuevas del modo conjunto. No se repitieron las suites JUnit ni el circuito comercial. El requisito multinstancia se apoya en bloqueos y unicidad de MySQL probados con transacciones concurrentes, no en memoria local. La consigna no exige desplegar una segunda JVM para la entrega; no se declara esa topología como ensayada ni capacidad masiva de producción. No se identificó un bloqueante obligatorio por estos dos puntos.
