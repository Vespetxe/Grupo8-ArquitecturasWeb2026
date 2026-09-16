package entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class EstudianteCarrera {

    @Id
    private int id;

    @ManyToOne
    private int dni_estudiante;

    @ManyToOne
    private int id_carrera;

    @Column
    private int inscripcion;

    @Column
    private int graduacion;

    @Column
    private int antiguedad;

    public EstudianteCarrera(int antiguedad, int graduacion, int inscripcion, int id_carrera, int dni_estudiante, int id) {
        this.antiguedad = antiguedad;
        this.graduacion = graduacion;
        this.inscripcion = inscripcion;
        this.id_carrera = id_carrera;
        this.dni_estudiante = dni_estudiante;
        this.id = id;
    }

    public EstudianteCarrera() {

    }

    public int getAntiguedad() {
        return antiguedad;
    }

    public void setAntiguedad(int antiguedad) {
        this.antiguedad = antiguedad;
    }

    public int getGraduacion() {
        return graduacion;
    }

    public void setGraduacion(int graduacion) {
        this.graduacion = graduacion;
    }

    public int getInscripcion() {
        return inscripcion;
    }

    public void setInscripcion(int inscripcion) {
        this.inscripcion = inscripcion;
    }

    public int getId_carrera() {
        return id_carrera;
    }

    public void setId_carrera(int id_carrera) {
        this.id_carrera = id_carrera;
    }

    public int getDni_estudiante() {
        return dni_estudiante;
    }

    public void setDni_estudiante(int dni_estudiante) {
        this.dni_estudiante = dni_estudiante;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "EstudianteCarrera{" +
                "id=" + id +
                ", dni_estudiante=" + dni_estudiante +
                ", id_carrera=" + id_carrera +
                ", inscripcion=" + inscripcion +
                ", graduacion=" + graduacion +
                ", antiguedad=" + antiguedad +
                '}';
    }
}
