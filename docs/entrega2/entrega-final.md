# Entrega 2 — Matriz y arquitectura final

Fuente: TPE-ArqWeb-TicketYa-2026.pdf, Funcionalidad 1–12, consideraciones no funcionales y tareas de Entrega 2. El punto 13 está vacío. La interfaz gráfica se valora; no es el foco obligatorio del backend. Se permiten simplificaciones de FIFO y recomendaciones, justificadas en sus documentos. Resultados y fechas de la campaña: [regresión final](regresion-final.md).

## Matriz breve

| Requisito | Implementación / evidencia | Estado |
|---|---|---|
| 1–3. Eventos, lugares, sectores, precios, publicar/suspender/cancelar | Eventos + EventWorkflow; campaña real con propiedad, configuración y estados persistidos | Cumplido |
| 4. Reserva temporal, pago, QR emitido, detalle y límite por cliente | Inventario; Compras/Entradas. CompraRegistroService bloquea control por cliente/evento, límite configurable 6; JUnit existentes de límite y campaña de compra | Cumplido |
| 5. No doble venta e idempotencia | Bloqueos transaccionales MySQL, unicidad de lotes y claves; carrera FIFO, carga y repeticiones de pago/reintegro | Cumplido para pruebas ejecutadas |
| 6. FIFO de alta demanda por tandas | ColaService/turno_cola, reserva consume turno dentro de transacción; queue-smoke y ColaIntegrationTest | Cumplido con simplificación permitida |
| 7. Devolución hasta 72 h, decisión y reintegro; cancelación total | Compra/pago/devolución/reintegro recuperables; prueba numerada con devolución parcial, cancelación y replays sin duplicados | Cumplido |
| 8. QR único, evento, rol/asignación y un ingreso | AdmissionService bloquea gate/ticket; QR real concurrente, anulado/otro evento y validación desde React | Cumplido |
| 9. Búsqueda multicriterio y orden | EventSearchController y especificaciones JPA: tipo, organizador, participante, ciudad, fechas; DATE/PRICE/RELEVANCE; JUnit de búsqueda y HTTP | Cumplido |
| 10. Preferencias, historial y recomendaciones | MongoDB en Eventos; OAuth2 hacia Compras; ranking determinista máximo 10, degradación explícita, sin IA | Cumplido con simplificación permitida |
| 11. Avisos de compra, cambio/cancelación y artista favorito en ciudad | Outbox transaccional, bandeja MySQL, SSE con JWT; notifications-smoke prueba push y recuperación | Cumplido |
| 12. Reportes propios por sector, devoluciones, ocupación y recaudación | Tres lecturas técnicas OAuth2, reportes bruto/reintegro/neto; null si neto incierto; snapshots y recuperación | Cumplido |
| Microservicios independientes, Gateway y REST | Seis POM/Dockerfile; ocho componentes backend en Compose canónico; solo Gateway publicado | Cumplido en aislamiento |
| Persistencia separada y NoSQL | Cinco esquemas MySQL; colección preferences solo de Eventos; sin consultas a BD ajenas | Cumplido |
| Consistencia, compensación y resiliencia | Etapas persistidas, identidad original, resultado desconocido; timeouts, circuitos y bulkheads selectivos; fallos reales y JUnit deterministas | Cumplido en escenarios probados |
| Caché | Participantes públicos en Caffeine, TTL 30 s y límites; invalidación post-commit y generación contra cargas tardías; nueve JUnit y smoke HTTP | Cumplido |
| JWT y roles | RS256, iss/aud/exp/nbf/token_use; propiedad en servidor; siete enlaces técnicos con scopes limitados; seguridad HTTP y frontend | Cumplido |
| Bitácora y trazas centralizadas | ECS/W3C y auditoría post-commit; fuente actual y evidencia anterior Fluent Bit, 274 eventos archivados, caída/recuperación/retención | Cumplido en topología optativa probada |
| Despliegue único incluyendo colector y Mongo | Canonical combinado de nueve servicios, guardas y coexistencia mínima Mongo/colector verificadas; plantilla raíz de ocho conservada | Cumplido en configuración; campaña completa de nueve procesos no repetida |
| Garantía contra doble venta entre instancias | Bloqueos PESSIMISTIC_WRITE y restricciones únicas en MySQL; doce transacciones concurrentes y pruebas de mecanismo | Cumplido el mecanismo; despliegue físico multirréplica y escala de producción no ensayados |
| Prueba de carga y seguridad/integración | Campaña acotada, latencias y resultados reproducibles; evidencia adjunta en informe | Cumplido al alcance medido |
| OpenAPI, diagramas, colección y ejecución | OpenAPI público E2, colección de referencia, diagramas aquí, README actual y guías específicas | Cumplido documental; ver límites de los contratos |
| Interfaz deseable | React real por Gateway, roles, asientos, compra, QR, preferencias, notificaciones y reportes; pruebas de navegador | Cumplido al alcance documentado |

No se considera pendiente activar el volumen histórico para demostrar funcionalidad: está expresamente fuera del alcance autorizado. Su migración/activación sigue pendiente de decisión operativa y no se realizó.

## Módulos y fuentes de verdad

~~~mermaid
flowchart LR
  UI[React / clientes REST] --> GW[API Gateway 8080]
  GW --> ID[Identidad 8085]
  GW --> EV[Eventos 8081]
  GW --> IN[Inventario 8082]
  GW --> CP[Compras 8084]
  GW --> EN[Entradas 8083]
  EV -->|OAuth2 REST| IN
  EV -->|OAuth2 REST| EN
  EV -->|OAuth2 REST| CP
  CP -->|OAuth2 REST| EV
  CP -->|OAuth2 REST| IN
  CP -->|OAuth2 REST| EN
  EN -->|OAuth2 REST| CP
  EV --> M[(Mongo: preferencias)]
  EV --> DBE[(MySQL Eventos)]
  IN --> DBI[(MySQL Inventario)]
  CP --> DBC[(MySQL Compras)]
  EN --> DBT[(MySQL Entradas)]
  ID --> DBA[(MySQL Identidad)]
~~~

Cada esquema pertenece a un servicio; las referencias entre ellos son IDs lógicos, no claves foráneas cruzadas. Las claves públicas se montan de solo lectura; la privada queda en Identidad. OAuth2 no se delega al navegador. Caffeine es auxiliar local y nunca fuente comercial.

## Siete enlaces técnicos vigentes

| Origen → destino | Scopes permitidos/provisionados |
|---|---|
| eventos → inventario | inventory.prepare inventory.lifecycle reports.inventory.read |
| eventos → entradas | tickets.event-block reports.tickets.read |
| eventos → compras | purchases.event-cancel purchases.history.read notifications.publish reports.sales.read |
| compras → eventos | events.read |
| compras → inventario | reservations.read reservations.confirm reservations.release reservations.compensate reservations.return |
| compras → entradas | tickets.read tickets.issue tickets.annul tickets.status |
| entradas → compras | orders.read |

Cada par tiene un secreto independiente, una audiencia y un cliente técnico registrados en Identidad. Historial, avisos y reportes agregan solo scopes específicos; no habilitan permisos a JWT humanos. Configuración mediante los siete TY_SECURITY_*_SECRET de .env.example, sin valores versionados. Los grants internos no se publican como rutas de negocio del navegador.

## Modelo E2: extensiones sobre los modelos de Entrega 1

~~~mermaid
erDiagram
  identity_users ||--o{ identity_user_roles : roles
  identity_users {
    bigint id PK
    string email UK
    string password_hash
    string status
  }
  identity_user_roles {
    bigint user_id FK
    string role
  }
  identity_technical_clients {
    string client_id PK
    string secret_hash
    string audience
    string scopes
    boolean enabled
  }
  identity_provisioning_markers {
    string name PK
  }
  control_evento ||..o{ turno_cola : "evento logico, sin FK declarada aqui"
  turno_cola {
    bigint id PK
    bigint evento_id UK
    bigint cliente_id UK
    bigint numero UK
    string estado
    datetime vence_en
    bigint reserva_id
  }
  ticket_batches ||--o{ tickets : emision
  tickets {
    string ticket_id PK
    string qr_payload UK
    string event_id
    string client_id
    string status
    datetime validated_at
    bigint validated_by
  }
  event_access {
    bigint id PK
    string event_id UK
    bigint staff_id UK
    boolean active
  }
  event_notification_outbox {
    bigint id PK
    bigint event_id
    string type
    datetime delivered_at
  }
  notifications {
    string id PK
    bigint owner_id
    string type
    string resource_type
    bigint resource_id
    datetime created_at
    datetime read_at
  }
  preferences_Mongo {
    string _id PK
    array favoriteTypes
    array favoriteParticipantIds
    string preferredCity
    datetime updatedAt
  }
~~~

Las marcas UK agrupadas representan restricciones compuestas: turno_cola(evento_id, cliente_id), turno_cola(evento_id, numero) y event_access(event_id, staff_id). El diagrama resume columnas relevantes; los nombres SQL completos y demás columnas son autoridad del código JPA. La relación de control_evento con turno_cola es conceptual. Los modelos completos originales de eventos, reservas, compras/pagos/reintegros y emisión están en docs/entrega1; las extensiones se explican en las guías de cada bloque. No se migró el catálogo a Mongo.

## Consistencia y decisiones

Se usa una saga orquestada con etapas locales persistidas, no una transacción distribuida. Un error técnico, 429, timeout, circuito abierto o bulkhead lleno no habilita compensaciones comerciales por sí mismo. Las consultas de recuperación conservan pago/reintegro/reserva/emisión y el administrador original. Los pagos y reintegros son simulados y persistentes; no hay una pasarela externa real.

Los eventos de outbox se insertan en la misma transacción que el cambio de negocio y se entregan después. SSE es un aviso push de mejor esfuerzo; REST conserva la bandeja. La auditoría técnica y el archivo central también son de mejor esfuerzo: las interrupciones o presión de buffers pueden perder registros; no son una fuente financiera. El archivo central tiene límite 64 MiB y TTL 24 h. La caché pública tiene TTL local y no invalidación distribuida.

## Límites y decisiones operativas

- El Compose raíz mantiene ocho servicios. El runner combinado genera un único manifiesto de nueve con Mongo y Fluent Bit; su configuración y coexistencia mínima se verificaron. No se repitió el flujo comercial completo con los nueve procesos. La topología persistente no se activó.
- La consigna exige funcionar entre instancias y explicar/probar el mecanismo, sin exigir un despliegue de dos JVM como criterio separado. Los bloqueos/uniqueness son de MySQL y no mutex locales. Las transacciones concurrentes verifican ese mecanismo; una segunda réplica física sigue siendo una limitación de cobertura documentable, no un bloqueante académico identificado.
- La carga breve acredita una prueba de flujo y sus invariantes, no capacidad de producción. No se inventaron SLA ni umbrales de latencia.
- Rotación de claves con solapamiento, TLS de producción, revocación inmediata de JWT emitidos, entrega exactly-once de logs y reconciliación automática del navegador no forman parte de lo probado.
- La colección de referencia se ejecuta por solicitudes y con variables propias; no lanzar todas sus mutaciones en un runner indiscriminadamente. OpenAPI hereda esquemas base de E1 y actualiza seguridad/rutas E2; los DTO agregados de reportes incluyen métricas y nulabilidad; sus controladores siguen siendo autoridad.
