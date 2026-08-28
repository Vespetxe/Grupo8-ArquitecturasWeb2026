package factories;

import dao.ClienteDAO;
import dao.FacturaDAO;
import dao.Factura_ProductoDAO;
import dao.ProductoDAO;
import entities.Factura_Producto;
import repositories.FacturaMySQLDAO;

public abstract class AbstractFactory {

    public static final int MYSQL_JDBC = 1;
    abstract public ClienteDAO getClienteDAO();
    abstract public ProductoDAO getProductoDAO();
    abstract public FacturaDAO getFacturaDAO();
    abstract public Factura_ProductoDAO getFacturaProductoDAO();

    public static MySQLFactory getFactory(int tipo) {
        switch (tipo) {
            case MYSQL_JDBC:
                return MySQLFactory.getInstance();
        }
        return null;
    }
}