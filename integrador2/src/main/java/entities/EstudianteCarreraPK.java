package entities;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EstudianteCarreraPK implements Serializable {

    private int id_estudiante;
    private int id_carrera;

    public EstudianteCarreraPK() {
    }

    public EstudianteCarreraPK(int id_estudiante, int id_carrera) {
        this.id_estudiante = id_estudiante;
        this.id_carrera = id_carrera;
    }

    public int getId_estudiante() {
        return id_estudiante;
    }

    public void setId_estudiante(int id_estudiante) {
        this.id_estudiante = id_estudiante;
    }

    public int getId_carrera() {
        return id_carrera;
    }

    public void setId_carrera(int id_carrera) {
        this.id_carrera = id_carrera;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EstudianteCarreraPK that = (EstudianteCarreraPK) o;
        return id_estudiante == that.id_estudiante && id_carrera == that.id_carrera;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id_estudiante, id_carrera);
    }
}