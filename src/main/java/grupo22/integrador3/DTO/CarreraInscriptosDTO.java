package grupo22.integrador3.DTO;

public class CarreraInscriptosDTO {
    private long id_carrera;
    private String carrera;
    private long inscriptos;

    public CarreraInscriptosDTO(long id_carrera, String carrera, long inscriptos) {
        this.id_carrera = id_carrera;
        this.carrera = carrera;
        this.inscriptos = inscriptos;
    }
}
