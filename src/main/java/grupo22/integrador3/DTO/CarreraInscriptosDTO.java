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
}
