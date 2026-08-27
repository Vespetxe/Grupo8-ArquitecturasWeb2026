package dao;

import entities.Producto;

import java.util.ArrayList;

public interface ProductoDAO {

    Producto findById(int idProducto);
    ArrayList<Producto> FindAll();
    void create(Producto producto);
    void update(Producto producto);
    void delete(int idProducto);
    void deleteAll();
}
