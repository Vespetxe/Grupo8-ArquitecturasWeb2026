package repositories;

import dao.Factura_ProductoDAO;
import dto.ProductoDTO;
import entities.Factura;
import entities.Factura_Producto;
import entities.Producto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Factura_ProductoMySQLDAO implements Factura_ProductoDAO {
    private final Connection cn;

    public Factura_ProductoMySQLDAO(Connection cn) {
        this.cn = cn;
        //crearTablaSiNoExiste();
    }

    public ProductoDTO getBestProducto() {
        String query = "SELECT p.nombre, p.valor, SUM(fp.cantidad * p.valor) AS recaudacion FROM Factura_Producto fp" +
                "JOIN Producto p ON p.idProducto = fp.idProducto" +
                "GROUP BY p.idProducto, p.nombre" +
                "ORDER BY recaudacion DESC";
        PreparedStatement ps = null;
        ResultSet rs = null;

        ProductoDTO bestProduct = null;

        try{
            ps = cn.prepareStatement(query);
            rs = ps.executeQuery();

            //Verificar si hay resultados
            if(rs.next()){
                String nombre =  rs.getString("nombre");
                float valor =  rs.getFloat("valor");
                float recaudado  = rs.getFloat("recaudacion");

                bestProduct = new ProductoDTO(nombre, valor, recaudado);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return bestProduct;
    }

    @Override
    public Factura_Producto findById(int idFactura, int idProducto) {
        final String sql = "SELECT idFactura, idProducto, cantidad FROM Factura_Producto WHERE idFactura= ? AND idProducto = ? ";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idFactura);
            ps.setLong(2, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en findById(factura, producto)", e);
        }
    }

    @Override
    public List<Factura_Producto> findAll() {
        final String sql = "SELECT idFactura, idProducto, cantidad FROM Factura_Producto";
        List<Factura_Producto> out = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error en findAll(pedido)", e);
        }
        return out;
    }

    @Override
    public List<Factura_Producto> findByFactura(int idFactura) {
        final String sql = "SELECT idFactura, idProducto, cantidad FROM Factura_Producto WHERE idFactura=?";
        List<Factura_Producto> out = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idFactura);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en findByFactura(factura)", e);
        }
        return out;
    }

    @Override
    public List<Factura_Producto> findByProducto(int idProducto) {
        final String sql = "SELECT idFactura, idProducto, cantidad FROM Factura_Producto WHERE idProducto=?";
        List<Factura_Producto> out = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en findByProducto(producto)", e);
        }
        return out;
    }

    @Override
    public void create(Factura_Producto fp) {
        final String sql = "INSERT INTO Factura_Producto (idFactura, idProducto, cantidad) VALUES (?,?,?)";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, fp.getIdFactura());
            ps.setInt(2, fp.getIdProducto());
            ps.setInt(3, fp.getCantidad());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en create(facturaProducto)", e);
        }
    }

    @Override
    public void update(int oldIdFactura, int oldIdProducto, Factura_Producto fp) {
        final String sql = "UPDATE Factura_Producto SET idFactura=?, idProducto=?, cantidad=? " +
                "WHERE idFactura=? AND idProducto=?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, fp.getIdFactura());
            ps.setInt(2, fp.getIdProducto());
            ps.setInt(3, fp.getCantidad());
            ps.setInt(4, oldIdFactura);
            ps.setInt(5, oldIdProducto);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en update(facturaProducto)", e);
        }
    }
    @Override
    public void updateIdFactura(int oldIdFactura, int idProducto, int nuevoIdFactura) {
        final String sql = "UPDATE Factura_Producto SET idFactura=? WHERE idFactura=? AND idProducto=?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, nuevoIdFactura);
            ps.setInt(2, oldIdFactura);
            ps.setInt(3, idProducto);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en updateIdFactura", e);
        }
    }
    @Override
    public void updateIdProducto(int idFactura, int oldIdProducto, int nuevoIdProducto) {
        final String sql = "UPDATE Factura_Producto SET idProducto=? WHERE idFactura=? AND idProducto=?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, nuevoIdProducto);
            ps.setInt(2, idFactura);
            ps.setInt(3, oldIdProducto);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en updateIdProducto", e);
        }
    }

    @Override
    public void delete(int idFactura, int idProducto) {
        final String sql = "DELETE FROM Factura_Producto WHERE idFactura=? AND idProducto=?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idFactura);
            ps.setLong(2, idProducto);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en delete(facturaPedido)", e);
        }
    }
    @Override
    public void deleteAll() {
        try (Statement st = cn.createStatement()) {
            // Padre: NUNCA TRUNCATE con FKs. Usá DELETE y luego reseteo del AI si querés.
            st.executeUpdate("DELETE FROM Factura_Producto");
            st.executeUpdate("ALTER TABLE Factura_Producto AUTO_INCREMENT = 1"); // opcional
        } catch (SQLException e) {
            throw new RuntimeException("Error borrando 'Factura_Producto'", e);
        }
    }


    private Factura_Producto map(ResultSet rs) throws SQLException {
        Factura_Producto fp = new Factura_Producto();
        fp.setIdFactura(rs.getInt("idFactura"));
        fp.setIdProducto(rs.getInt("idProducto"));
        fp.setCantidad(rs.getInt("cantidad"));
        return fp;
    }


}
