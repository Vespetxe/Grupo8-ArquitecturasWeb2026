package factories;

import dao.ClienteDAO;
import dao.FacturaDAO;
import dao.Factura_ProductoDAO;
import dao.ProductoDAO;
import repositories.ClienteMySQLDAO;
import repositories.FacturaMySQLDAO;
import repositories.Factura_ProductoMySQLDAO;
import repositories.ProductoMySQLDAO;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySQLFactory extends AbstractFactory {
    private static MySQLFactory instance = null;

    public static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    public static final String URL = "jdbc:mysql://localhost:3306/tp-integrador";
    public static final String USER = "root";
    public static final String PASSWORD = "8790";
    public static Connection conn;

    private MySQLFactory() {
    }

    public static synchronized MySQLFactory getInstance() {
        if (instance == null) {
            instance = new MySQLFactory();
        }
        return instance;
    }

    public static Connection createConnection() {
        if (conn != null) {
            return conn;
        }
        String driver = DRIVER;
        try {
            Class.forName(driver).getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                 | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return conn;
    }

    public void closeConnection() {
        try {
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ClienteDAO getClienteDAO() {
        return new ClienteMySQLDAO(createConnection());
    }

    public ProductoDAO getProductoDAO() {
        return new ProductoMySQLDAO(createConnection());
    }

    public FacturaDAO getFacturaDAO() {
        return new FacturaMySQLDAO(createConnection());
    }

    public Factura_ProductoDAO getFacturaProductoDAO() {
        return new Factura_ProductoMySQLDAO(createConnection());
    }
}
