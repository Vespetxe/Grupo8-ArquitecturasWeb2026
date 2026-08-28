package dao;

import dto.ProductoDTO;
import entities.Factura_Producto;

import java.util.List;

public interface Factura_ProductoDAO {

    Factura_Producto findById(int idFactura, int idProducto);
    List<Factura_Producto> findAll();
    List<Factura_Producto> findByFactura(int idFactura);
    List<Factura_Producto> findByProducto(int idProducto);

    void create(Factura_Producto fp);
    void update(int oldIdFactura, int oldIdProducto, Factura_Producto fp);
    void updateIdFactura(int oldIdFactura, int idProducto, int nuevoIdFactura);
    void updateIdProducto(int idFactura, int oldIdProducto, int nuevoIdProducto);
    void delete(int idFactura, int idProduct);
    ProductoDTO getBestProduct();

    void deleteAll();

}

}
