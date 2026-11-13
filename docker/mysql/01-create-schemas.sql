-- Bootstrap only: the official entrypoint runs this on a new data directory.
-- Fail on pre-existing application schemas rather than silently accepting them.
CREATE DATABASE `ticketya_e2_docker_eventos` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE `ticketya_e2_docker_inventario` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE `ticketya_e2_docker_entradas` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE `ticketya_e2_docker_compras` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
