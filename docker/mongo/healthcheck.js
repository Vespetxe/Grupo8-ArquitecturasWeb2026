// Authentication and ping only; no writes and no password arguments/output.
(() => {
  const database = db.getSiblingDB(process.env.MONGO_INITDB_DATABASE);
  if (!database.auth('ty_eventos_preferences', process.env.TY_E2_EVENTOS_MONGO_PASSWORD)) quit(1);
  quit(database.runCommand({ping: 1}).ok === 1 ? 0 : 1);
})();
