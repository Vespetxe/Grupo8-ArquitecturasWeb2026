package dto;

public class CarreraDTO {
    int idCarrera;
    String nombre_carrera;
    int cantidad_inscriptos;

    public CarreraDTO() {
    }

    public CarreraDTO(int idCarrera, String nombre_carrera, int cantidad_inscriptos) {
        this.idCarrera = idCarrera;
        this.nombre_carrera = nombre_carrera;
        this.cantidad_inscriptos = cantidad_inscriptos;
    }

    public int getIdCarrera() {
        return idCarrera;
    }

    public String getNombre_carrera() {
        return nombre_carrera;
    }

    public int getCantidad_inscriptos() {
        return cantidad_inscriptos;
    }
}
