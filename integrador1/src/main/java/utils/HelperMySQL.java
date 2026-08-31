package utils;

import entities.Cliente;
import entities.Factura;
import entities.Factura_Producto;
import entities.Producto;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
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
        final String PASSWORD = "";

        try {
            Class.forName(DRIVER).getDeclaredConstructor().newInstance();
            this.conn = DriverManager.getConnection(URL, USER, PASSWORD);
            //this.conn = conn.setAutoCommit(false);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException |
                 SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            conn.setAutoCommit(false);
        } catch (Exception e) {
            e.printStackTrace();
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
        String dropCliente = "DROP TABLE IF EXISTS Cliente";
        this.conn.prepareStatement(dropCliente).execute();
        this.conn.commit();

        String dropFactura = "DROP TABLE IF EXISTS Factura";
        this.conn.prepareStatement(dropFactura).execute();
        this.conn.commit();

        String dropProducto = "DROP TABLE IF EXISTS Producto";
        this.conn.prepareStatement(dropProducto).execute();
        this.conn.commit();

        String dropFactura_Producto = "DROP TABLE IF EXISTS Factura_Producto";
        this.conn.prepareStatement(dropFactura_Producto).execute();
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

    private Iterable<utils.CSVRecord> getData(String archive) throws IOException {
        String path = "src\\main\\resources\\" + archive;
        Reader in = new FileReader(path);
        String[] header = {};
        CSVParser csvParser = CSVFormat.EXCEL.withHeader(header).parse(in);

        Iterable<CSVRecord> records = csvParser.getRecords();
        return records;
    }

    public void populateDB() throws Exception {
        System.out.println("Cargando clientes...");
        try {
            for (CSVRecord row : getData("clientes.csv")) {
                if (row.size() >= 3) {
                    String idClienteString = row.get(0);
                    String nombre = row.get(1);
                    String email = row.get(2);

                    if (!idClienteString.isEmpty() && !nombre.isEmpty() && !email.isEmpty()) {
                        try {
                            int idCliente = Integer.parseInt(idClienteString);

                            Cliente cliente = new Cliente(idCliente, nombre, email);
                            insertCliente(cliente, conn);
                        } catch (NumberFormatException e) {
                            System.err.println("Error de formato en datos de cliente: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("Cargando facturas...");
        try {
            for (CSVRecord row : getData("facturas.csv")) {
                if (row.size() >= 2) {
                    String idFacturaString = row.get(0);
                    String idClienteString = row.get(1);

                    if (!idFacturaString.isEmpty() && !idClienteString.isEmpty()) {
                        try {
                            int idFactura = Integer.parseInt(idFacturaString);
                            int idCliente = Integer.parseInt(idClienteString);

                            Factura factura = new Factura(idFactura, idCliente);
                            insertFactura(factura, conn);
                        } catch (NumberFormatException e) {
                            System.err.println("Error de formato en datos de factura: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("Cargando productos...");
        try {
            for (CSVRecord row : getData("productos.csv")) {
                if (row.size() >= 3) {
                    String idProductoString = row.get(0);
                    String nombre = row.get(1);
                    String valorString = row.get(2);

                    if (!idProductoString.isEmpty() && !nombre.isEmpty() && !valorString.isEmpty()) {
                        try {
                            int idProducto = Integer.parseInt(idProductoString);
                            float valor = Float.parseFloat(valorString);

                            Producto producto = new Producto(idProducto, nombre, valor);
                            insertProducto(producto, conn);
                        } catch (NumberFormatException e) {
                            System.err.println("Error de formato en datos de producto: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("Cargando facturas-productos...");
        try {
            for (CSVRecord row : getData("facturas-productos.csv")) {
                if (row.size() >= 3) {
                    String idFacturaString = row.get(0);
                    String idProductoString = row.get(1);
                    String cantidadString = row.get(2);

                    if (!idFacturaString.isEmpty() && !idProductoString.isEmpty() && !cantidadString.isEmpty()) {
                        try {
                            int idFactura = Integer.parseInt(idFacturaString);
                            int idProducto = Integer.parseInt(idProductoString);
                            int cantidad = Integer.parseInt(cantidadString);

                            Factura_Producto factura_producto = new Factura_Producto(idFactura, idProducto, cantidad);
                            insertFactura_Producto(factura_producto, conn);
                        } catch (NumberFormatException e) {
                            System.err.println("Error de formato en datos de factura-producto: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int insertCliente (Cliente cliente, Connection conn) throws Exception{
        String insert = "INSERT INTO Cliente (idCliente, nombre, email) VALUES (?, ?, ?)";
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(insert);
            ps.setInt(1,cliente.getIdCliente());
            ps.setString(2, cliente.getNombre());
            ps.setString(3,cliente.getEmail());
            if (ps.executeUpdate() == 0) {
                throw new Exception("No se pudo insertar cliente");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closePsAndCommit(conn, ps);
        }
        return 0;
    }


    private int insertFactura(Factura factura, Connection conn) throws Exception {

        String insert = "INSERT INTO Factura (idFactura, idCliente) VALUES (?, ?)";
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(insert);
            ps.setInt(1,factura.getIdFactura());
            ps.setInt(2, factura.getIdCliente());
            if (ps.executeUpdate() == 0) {
                throw new Exception("No se pudo insertar factura");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closePsAndCommit(conn, ps);
        }
        return 0;
    }
    private int insertProducto(Producto producto, Connection conn) throws Exception {

        String insert = "INSERT INTO Producto (idProducto, nombre, valor) VALUES (?, ?, ?)";
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(insert);
            ps.setInt(1,producto.getIdProducto());
            ps.setString(2, producto.getNombre());
            ps.setFloat(3, producto.getValor());
            if (ps.executeUpdate() == 0) {
                throw new Exception("No se pudo insertar producto");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closePsAndCommit(conn, ps);
        }
        return 0;
    }

    private int insertFactura_Producto(Factura_Producto factura_producto, Connection conn) throws Exception {

        String insert = "INSERT INTO Factura_Producto (idFactura, idProducto, cantidad) VALUES (?, ?, ?)";
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(insert);
            ps.setInt(1,factura_producto.getIdFactura());
            ps.setInt(2, factura_producto.getIdProducto());
            ps.setInt(3, factura_producto.getCantidad());
            if (ps.executeUpdate() == 0) {
                throw new Exception("No se pudo insertar factura_producto");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closePsAndCommit(conn, ps);
        }
        return 0;
    }

    private void closePsAndCommit(Connection conn, PreparedStatement ps) {
        if (conn != null){
            try {
                ps.close();
                conn.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}
