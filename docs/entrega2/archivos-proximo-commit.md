# Archivos propuestos para el próximo commit

Lista del árbol de trabajo tras las correcciones de portabilidad y Astra.
No se preparó staging ni se creó commit. Revisar el diff antes de incorporar.

Se incluyen los cambios previos conservados y los scripts de demo existentes.
`DocumentacionTest.java` conserva solamente el formato previo del usuario.

| Archivo |
|---|
| `demo/.gitattributes` |
| `demo/.gitignore` |
| `demo/catalog.cjs` |
| `demo/demo.test.cjs` |
| `demo/lib.cjs` |
| `demo/mysql-init.sh` |
| `demo/prepare.cjs` |
| `demo/README.md` |
| `demo/seed.cjs` |
| `demo/start.cjs` |
| `demo/verify.cjs` |
| `Dockerfile.tests` |
| `docs/entrega1/openapi/compras.json` |
| `docs/entrega1/openapi/entradas.json` |
| `docs/entrega1/openapi/eventos.json` |
| `docs/entrega1/openapi/gateway.json` |
| `docs/entrega1/openapi/inventario.json` |
| `docs/entrega1/openapi/README.md` |
| `docs/entrega2/archivos-proximo-commit.md` |
| `docs/entrega2/portabilidad-entrega.md` |
| `README.md` |
| `TicketYa/entradas_service/docs/openapi.yaml` |
| `TicketYa/frontend/playwright.config.js` |
| `TicketYa/frontend/playwright.delivery.config.js` |
| `TicketYa/frontend/src/main.jsx` |
| `TicketYa/identidad_service/src/main/java/ar/edu/ticketya/identidad/config/ProvisioningCommand.java` |
| `TicketYa/identidad_service/src/test/java/ar/edu/ticketya/identidad/config/ProvisioningCommandTest.java` |
| `TicketYa/integration-tests/e2/environment.cjs` |
| `TicketYa/integration-tests/e2/environment.test.cjs` |
| `TicketYa/integration-tests/e2/frontend-delivery.cjs` |
| `TicketYa/integration-tests/e2/provisioning.test.cjs` |
| `TicketYa/integration-tests/e2/run-security.cjs` |
| `TicketYa/integration-tests/run-tests.cjs` |
| `TicketYa/integration-tests/src/test/java/ar/edu/ticketya/pruebas/ContratosTest.java` |
| `TicketYa/integration-tests/src/test/java/ar/edu/ticketya/pruebas/DocumentacionTest.java` |

Total: **35 archivos**.

Excluir y conservar localmente: `package-lock.json` de la raíz (no hay paquete npm raíz),
`demo/runtime/`, `.env` privados, claves, `target/`, `node_modules/` y resultados.
El lockfile válido del frontend ya está versionado y no cambió.

Comprobaciones previas al próximo commit:
`git diff --check`, `git status --short --untracked-files=all`,
`git check-ignore demo/runtime/private.json demo/runtime/ACCESOS-DEMO.md`.
Usar rutas explícitas para staging y revisar el diff preparado; no usar `git add .`.
