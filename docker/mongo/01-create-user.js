// Official Mongo entrypoint, first initialization of a NEW volume only.
(() => {
  const database = process.env.MONGO_INITDB_DATABASE;
  const password = process.env.TY_E2_EVENTOS_MONGO_PASSWORD;
  if (!database || !/^[a-fA-F0-9]{64}$/.test(password || '')) {
    throw new Error('Define database and a 64-character hexadecimal Mongo application password');
  }
  db.getSiblingDB(database).createUser({
    user: 'ty_eventos_preferences', pwd: password,
    roles: [{role: 'readWrite', db: database}]
  });
})();
