import dao.Factura_ProductoDAO;
import dto.ClienteDTO;
import dto.ProductoDTO;
import entities.Factura_Producto;
import entities.Producto;
import factories.AbstractFactory;
import repositories.Factura_ProductoMySQLDAO;
import utils.HelperMySQL;

import java.sql.SQLException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        HelperMySQL helperMySQL = new HelperMySQL();
        helperMySQL.dropTables();
        helperMySQL.createTables();
        helperMySQL.populateDB();
        //helperMySQL.closeConnection();

        AbstractFactory chosenFactory = AbstractFactory.getFactory(1);

        Factura_ProductoDAO facturaProducto = chosenFactory.getFacturaProductoDAO();

        ProductoDTO productoDTO = facturaProducto.getBestProduct();
        System.out.println("Producto que mas recaudo: " + productoDTO);

        List<ClienteDTO> clienteDTO = facturaProducto.getClientesByFacturacion();
        System.out.println("Listado de Clientes que mas recaudaron: " + clienteDTO);

    }
}
