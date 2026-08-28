package repositories;

import dao.FacturaDAO;
import entities.Factura;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FacturaMySQLDAO implements FacturaDAO {
    private Connection conn;

    public FacturaMySQLDAO(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void insert(Factura factura) {
        String query = "INSERT INTO factura (idCliente) VALUES (?)";
        PreparedStatement ps = null;

        try{
            ps = conn.prepareStatement(query);
            ps.setInt(1, factura.getIdCliente());

            ps.executeUpdate();
            System.out.println("--Factura insertada con exito!!--");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        finally {
            ClosePreparedStatement(ps);
        }
    }

    @Override
    public void update(Factura factura) {

        String query = "UPDATE factura SET idFactura=?, idCliente=? WHERE idFactura=?";
        PreparedStatement ps = null;

        try{
            ps = conn.prepareStatement(query);
            ps.setInt(1, factura.getIdFactura());
            ps.setInt(2, factura.getIdCliente());
            ps.setInt(3, factura.getIdFactura());

            ps.executeUpdate();

            System.out.println("--Factura modificada con exito!!--");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        finally {
            ClosePreparedStatement(ps);
        }
    }

    @Override
    public void deleteByFactura(Factura factura) {
        String query = "DELETE FROM factura WHERE idFactura = ?";
        PreparedStatement ps = null;

        try{
            ps = conn.prepareStatement(query);
            ps.setInt(1, factura.getIdFactura());

            ps.executeUpdate();
            System.out.println("--Factura borrada con exito!!--");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        finally {
            ClosePreparedStatement(ps);
        }
    }

    @Override
    public List<Factura> findByFactura(Factura factura) {
        String query = "SELECT * FROM factura WHERE idFactura=?";
        List<Factura> out = new ArrayList<>();
        PreparedStatement ps = null;

        try{
            ps = conn.prepareStatement(query);
            ps.setInt(1, factura.getIdFactura());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }

            System.out.println("--Facturas Retornadas--");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        finally {
            ClosePreparedStatement(ps);
        }
        return out;
    }

    @Override
    public List<Factura> findByCliente(Factura factura) {
        String query = "SELECT * FROM factura WHERE idFactura=?";
        List<Factura> out = new ArrayList<>();
        PreparedStatement ps = null;

        try{
            ps = conn.prepareStatement(query);
            ps.setInt(1, factura.getIdCliente());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }

            System.out.println("--Facturas Retornadas--");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        finally {
            ClosePreparedStatement(ps);
        }
        return out;
    }

    @Override
    public void deleteAll() {
        Statement stmt = null;
        try{
            stmt = conn.createStatement();
            stmt.executeUpdate("DELETE FROM productos");
            stmt.execute("ALTER TABLE productos AUTO_INCREMENT = 1");
        } catch (SQLException e) {
            throw new RuntimeException("Error borrando 'productos'", e);
        }
    }

    private void ClosePreparedStatement(PreparedStatement ps) {
        try {
            if (ps != null) {
                ps.close();
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Factura map(ResultSet rs) throws SQLException {
        Factura d = new Factura();
        d.setIdFactura(rs.getInt("idFactura"));
        d.setIdCliente(rs.getInt("idCliente"));
        return d;
    }
}
