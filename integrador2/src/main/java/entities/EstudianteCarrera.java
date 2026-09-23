package entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.JoinColumn;
import java.time.LocalDate;
import java.time.Period;
import jakarta.persistence.Transient;

@Entity
public class EstudianteCarrera {

    @EmbeddedId
    private EstudianteCarreraPK id;

    @ManyToOne
    @MapsId("dni_estudiante")
    @JoinColumn(name = "dni_estudiante")
    private Estudiante estudiante;

    @ManyToOne
    @MapsId("id_carrera")
    @JoinColumn(name = "id_carrera")
    private Carrera carrera;

    @Column
    private LocalDate inscripcion;

    @Column
    private LocalDate graduacion;

    public EstudianteCarrera(Estudiante estudiante, Carrera carrera, LocalDate inscripcion, LocalDate graduacion) {
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.inscripcion = inscripcion;
        this.graduacion = graduacion;
        this.id = new EstudianteCarreraPK(estudiante.getDNI(), carrera.getId_carrera());
    }

    public EstudianteCarrera() {
    }

    public EstudianteCarreraPK getId() {
        return id;
    }

    public Estudiante getEstudiante() {
        return estudiante;
    }

    public void setEstudiante(Estudiante estudiante) {
        this.estudiante = estudiante;
    }

    public Carrera getCarrera() {
        return carrera;
    }

    public void setCarrera(Carrera carrera) {
        this.carrera = carrera;
    }

    public LocalDate getInscripcion() {
        return inscripcion;
    }

    public void setInscripcion(LocalDate inscripcion) {
        this.inscripcion = inscripcion;
    }

    public LocalDate getGraduacion() {
        return graduacion;
    }

    public void setGraduacion(LocalDate graduacion) {
        this.graduacion = graduacion;
    }

    @Transient
    public Period getAntiguedad() {
        if (inscripcion == null) return null;
        LocalDate fin = (graduacion != null) ? graduacion : LocalDate.now();
        return Period.between(inscripcion, fin);
    }

    @Override
    public String toString() {
        return "EstudianteCarrera{" +
                "id=" + id +
                ", estudiante=" + estudiante +
                ", carrera=" + carrera +
                ", inscripcion=" + inscripcion +
                ", graduacion=" + graduacion +
                ", antiguedad=" + getAntiguedad() +
                '}';
    }

}
