package dto;

public class ClienteDTO {
    private int idCliente;
    private String nombre;
    private String email;
    private float facturado;

    public ClienteDTO(){};

    public ClienteDTO(int idCliente, String nombre, String email, float facturado) {
        this.idCliente = idCliente;
        this.nombre = nombre;
        this.email = email;
        this.facturado = facturado;
    }


    public int getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(int idCliente) {
        this.idCliente = idCliente;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    public float getFacturado() {
        return facturado;
    }

    public void setFacturado(float facturado) {
        this.facturado = facturado;
    }

    @Override
    public String toString() {
        return "ClienteDTO{" +
                "idCliente=" + idCliente +
                ", nombre='" + nombre + '\'' +
                ", email='" + email + '\'' +
                ", facturado=" + facturado +
                '}';
    }

}
