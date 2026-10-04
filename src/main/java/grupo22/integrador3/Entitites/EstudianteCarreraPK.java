package grupo22.integrador3.Entitites;


import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EstudianteCarreraPK implements Serializable {

    private Long dni_estudiante;
    private Long id_carrera;

    public EstudianteCarreraPK() {
    }

    public EstudianteCarreraPK(Long dni_estudiante, Long id_carrera) {
        this.dni_estudiante = dni_estudiante;
        this.id_carrera = id_carrera;
    }

    public Long getDni_estudiante() {
        return dni_estudiante;
    }

    public void setDni_estudiante(Long dni_estudiante) {
        this.dni_estudiante = dni_estudiante;
    }

    public Long getId_carrera() {
        return id_carrera;
    }

    public void setId_carrera(Long id_carrera) {
        this.id_carrera = id_carrera;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EstudianteCarreraPK that = (EstudianteCarreraPK) o;
        return dni_estudiante == that.dni_estudiante && id_carrera == that.id_carrera;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dni_estudiante, id_carrera);
    }
}