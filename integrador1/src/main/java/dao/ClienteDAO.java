package dao;

import entities.Cliente;
import java.util.List;

public interface ClienteDAO {
    //CREATE
    void create(Cliente cliente);

    //READ
    Cliente findById(int idCliente);
    List<Cliente> findAll();

    //UPDATE
    void update(Cliente cliente);

    //DELETE
    void delete(int idCliente);
    void deleteAll();

}
