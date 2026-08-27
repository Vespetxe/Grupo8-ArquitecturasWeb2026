package dao;

import entities.Factura;
import entities.Cliente;

import java.util.List;

public interface FacturaDAO {
    abstract public void insert(Factura factura);
    abstract public void update(Factura factura);
    abstract public void deleteByFactura(Factura factura);
    abstract public List<Factura> findByFactura(Factura factura);
    abstract public List<Factura> findByCliente(Factura cliente);
    abstract public void deleteAll();
}
