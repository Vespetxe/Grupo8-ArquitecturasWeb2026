package utils;

import entities.Cliente;
import entities.Factura;
import entities.Factura_Producto;
import entities.Producto;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public class HelperMySQL {
    private Connection conn = null;

    public HelperMySQL() { //Constructor
        final String DRIVER = "com.mysql.cj.jdbc.Driver";
        final String URL = "jdbc:mysql://localhost:3306/integrador1?createDatabaseIfNotExist=true";
        final String USER = "root";
        final String PASSWORD = "root";

        try {
            Class.forName(DRIVER).getDeclaredConstructor().newInstance();
            this.conn = DriverManager.getConnection(URL, USER, PASSWORD);
            this.conn.setAutoCommit(false);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException |
                 SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }

    }
    public void closeConnection() {
        if (conn != null){
            try {
                conn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public void dropTables() throws SQLException {

        String dropFactura_Producto = "DROP TABLE IF EXISTS Factura_Producto";
        this.conn.prepareStatement(dropFactura_Producto).execute();
        this.conn.commit();

        String dropFactura = "DROP TABLE IF EXISTS Factura";
        this.conn.prepareStatement(dropFactura).execute();
        this.conn.commit();

        String dropProducto = "DROP TABLE IF EXISTS Producto";
        this.conn.prepareStatement(dropProducto).execute();
        this.conn.commit();

        String dropCliente = "DROP TABLE IF EXISTS Cliente";
        this.conn.prepareStatement(dropCliente).execute();
        this.conn.commit();


    }

    public void createTables() throws SQLException {
        String tableCliente = "CREATE TABLE IF NOT EXISTS Cliente(" +
                "idCliente INT NOT NULL, " +
                "nombre VARCHAR(500), " +
                "email VARCHAR(150), " +
                "CONSTRAINT Cliente_pk PRIMARY KEY (idCliente));" ;
        this.conn.prepareStatement(tableCliente).execute();
        this.conn.commit();
        String tableFactura = "CREATE TABLE IF NOT EXISTS Factura(" +
                "idFactura INT NOT NULL, " +
                "idCliente INT NOT NULL, " +
                "CONSTRAINT Factura_pk PRIMARY KEY (idFactura)," +
                "CONSTRAINT FK_idCliente FOREIGN KEY (idCliente) REFERENCES Cliente (idCliente))";
        this.conn.prepareStatement(tableFactura).execute();
        this.conn.commit();
        String tableProducto = "CREATE TABLE IF NOT EXISTS Producto(" +
                "idProducto INT NOT NULL, " +
                "nombre VARCHAR(45), " +
                "valor FLOAT, " +
                "CONSTRAINT Producto_pk PRIMARY KEY (idProducto)) ";
        this.conn.prepareStatement(tableProducto).execute();
        this.conn.commit();
        String tableFactura_Producto = "CREATE TABLE IF NOT EXISTS Factura_Producto(" +
                "idFactura INT NOT NULL, " +
                "idProducto INT NOT NULL, " +
                "cantidad INT, " +
                "CONSTRAINT Factura_Producto_pk PRIMARY KEY (idFactura, idProducto)," +
                "CONSTRAINT FK_idFactura FOREIGN KEY (idFactura) REFERENCES Factura (idFactura), " +
                "CONSTRAINT FK_idProducto FOREIGN KEY (idProducto) REFERENCES Producto (idProducto))";
        this.conn.prepareStatement(tableFactura_Producto).execute();
        this.conn.commit();
    }

    private Iterable<CSVRecord> getData(String archive) throws IOException {
        InputStream inputStream =
                HelperMySQL.class.getClassLoader().getResourceAsStream(archive);

        if (inputStream == null) {
            throw new FileNotFoundException("No se encontró el archivo: " + archive);
        }

        Reader in = new InputStreamReader(inputStream);

        CSVParser csvParser = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(in);

        return csvParser.getRecords();
    }

    public void populateDB() throws Exception {
        try {
            System.out.println("Cargando clientes...");
            for (CSVRecord row : getData("clientes.csv")) {
                if (row.size() >= 3) {
                    String idClienteString = row.get(0);
                    String nombre = row.get(1);
                    String email = row.get(2);

                    if (!idClienteString.isEmpty() && !nombre.isEmpty() && !email.isEmpty()) {
                        int idCliente = Integer.parseInt(idClienteString);

                        Cliente cliente = new Cliente(idCliente, nombre, email);
                        insertCliente(cliente, conn);
                    }
                }
            }

            System.out.println("Cargando facturas...");
            for (CSVRecord row : getData("facturas.csv")) {
                if (row.size() >= 2) {
                    String idFacturaString = row.get(0);
                    String idClienteString = row.get(1);

                    if (!idFacturaString.isEmpty() && !idClienteString.isEmpty()) {
                        int idFactura = Integer.parseInt(idFacturaString);
                        int idCliente = Integer.parseInt(idClienteString);

                        Factura factura = new Factura(idFactura, idCliente);
                        insertFactura(factura, conn);
                    }
                }
            }

            System.out.println("Cargando productos...");
            for (CSVRecord row : getData("productos.csv")) {
                if (row.size() >= 3) {
                    String idProductoString = row.get(0);
                    String nombre = row.get(1);
                    String valorString = row.get(2);

                    if (!idProductoString.isEmpty() && !nombre.isEmpty() && !valorString.isEmpty()) {
                        int idProducto = Integer.parseInt(idProductoString);
                        float valor = Float.parseFloat(valorString);

                        Producto producto = new Producto(idProducto, nombre, valor);
                        insertProducto(producto, conn);
                    }
                }
            }

            System.out.println("Cargando facturas-productos...");
            for (CSVRecord row : getData("facturas-productos.csv")) {
                if (row.size() >= 3) {
                    String idFacturaString = row.get(0);
                    String idProductoString = row.get(1);
                    String cantidadString = row.get(2);

                    if (!idFacturaString.isEmpty() &&
                            !idProductoString.isEmpty() &&
                            !cantidadString.isEmpty()) {

                        int idFactura = Integer.parseInt(idFacturaString);
                        int idProducto = Integer.parseInt(idProductoString);
                        int cantidad = Integer.parseInt(cantidadString);

                        Factura_Producto facturaProducto =
                                new Factura_Producto(idFactura, idProducto, cantidad);

                        insertFactura_Producto(facturaProducto, conn);
                    }
                }
            }

            conn.commit();
            System.out.println("Base cargada correctamente.");

        } catch (Exception e) {
            conn.rollback();
            throw e;
        }
    }

    private void insertCliente(Cliente cliente, Connection conn) throws SQLException {
        String sql = "INSERT INTO Cliente (idCliente, nombre, email) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cliente.getIdCliente());
            ps.setString(2, cliente.getNombre());
            ps.setString(3, cliente.getEmail());
            ps.executeUpdate();
        }
    }

    private void insertFactura(Factura factura, Connection conn) throws SQLException {
        String sql = "INSERT INTO Factura (idFactura, idCliente) VALUES (?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, factura.getIdFactura());
            ps.setInt(2, factura.getIdCliente());
            ps.executeUpdate();
        }
    }

    private void insertProducto(Producto producto, Connection conn) throws SQLException {
        String sql = "INSERT INTO Producto (idProducto, nombre, valor) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, producto.getIdProducto());
            ps.setString(2, producto.getNombre());
            ps.setFloat(3, producto.getValor());
            ps.executeUpdate();
        }
    }

    private void insertFactura_Producto(Factura_Producto fp, Connection conn) throws SQLException {
        String sql =
                "INSERT INTO Factura_Producto (idFactura, idProducto, cantidad) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fp.getIdFactura());
            ps.setInt(2, fp.getIdProducto());
            ps.setInt(3, fp.getCantidad());
            ps.executeUpdate();
        }
    }

}
