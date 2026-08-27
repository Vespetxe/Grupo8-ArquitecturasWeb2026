package factories;

public class AbstactFactory {

    public abstract class AbstractFactory {
        public static final int MYSQL_JDBC = 1;
        abstract public int getClienteDAO();
        abstract public int getProductoDAO();
        abstract public int getFacturaDAO();
        abstract public int getFacturaProductoDAO();

        public static MySQLFactory getFactory(int tipo) {
            switch (tipo) {
                case MYSQL_JDBC:
                    return MySQLFactory.getInstance();
            }
            return null;
        }
    }

}
