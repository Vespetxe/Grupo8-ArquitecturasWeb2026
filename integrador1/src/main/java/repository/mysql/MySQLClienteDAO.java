package repository.mysql;

import dao.ClienteDAO;
import entities.Cliente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MySQLClienteDAO implements ClienteDAO {

    private final Connection cn;

    public MySQLClienteDAO(Connection cn){
        this.cn = cn;
    }

    @Override
    public void create(Cliente cliente) {
        final String sql = "INSERT INTO cliente (nombre, email) VALUES (?, ?)";
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cliente.getNombre());
            ps.setString(2, cliente.getEmail());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    cliente.setIdCliente(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al insertar el registro", e);
            }
    }

    @Override
    public Cliente findById(int idCliente) {
        final String sql = "SELECT * FROM cliente WHERE idCliente = ?";

        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en findByUsuario(Cliente)", e);
        }
        return null;
    }

    @Override
    public List<Cliente> findAll() {
        final String sql = "SELECT * FROM cliente";
        List<Cliente> out = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error en findAll(Cliente)", e);
        }
        return out;
    }

    @Override
    public void update(Cliente cliente) {
        final String sql = "UPDATE cliente SET nombre = ?, email = ? WHERE idCliente = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, cliente.getNombre());
            ps.setString(2, cliente.getEmail());
            ps.setInt(3, cliente.getIdCliente());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en update(Cliente)", e);
        }
    }

    @Override
    public void delete(int idCliente) {
        final String sql = "DELETE FROM cliente WHERE idCliente = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en delete(Cliente)", e);
        }
    }

    @Override
    public void deleteAll() {
        try (Statement st = cn.createStatement()) {
            st.executeUpdate("DELETE FROM cliente");
        } catch (SQLException e) {
            throw new RuntimeException("Error borrando 'Clientes'", e);
        }
    }

    private Cliente map(ResultSet rs) throws SQLException {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(rs.getInt("idCliente"));
        cliente.setNombre(rs.getString("nombre"));
        cliente.setEmail(rs.getString("email"));
        return cliente;
    }
}
