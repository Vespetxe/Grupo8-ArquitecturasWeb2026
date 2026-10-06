package grupo22.integrador3.DTO;

public class CarreraInscriptosDTO {
    private Long id_carrera;
    private String carrera;
    private long inscriptos;

    public CarreraInscriptosDTO(Long id_carrera, String carrera, long inscriptos) {
        this.id_carrera = id_carrera;
        this.carrera = carrera;
        this.inscriptos = inscriptos;
    }

    public Long getId_carrera() {
        return id_carrera;
    }

    public void setId_carrera(Long id_carrera) {
        this.id_carrera = id_carrera;
    }

    public String getCarrera() {
        return carrera;
    }

    public void setCarrera(String carrera) {
        this.carrera = carrera;
    }

    public long getInscriptos() {
        return inscriptos;
    }

    public void setInscriptos(long inscriptos) {
        this.inscriptos = inscriptos;
    }

    @Override
    public String toString() {
        return "CarreraInscriptosDTO{" +
                "id_carrera=" + id_carrera +
                ", carrera='" + carrera + '\'' +
                ", inscriptos=" + inscriptos +
                '}';
    }
}
