package dto;

import entities.Carrera;
import entities.Estudiante;

import java.util.List;

public class ReporteCarreraDTO {

    private Carrera carrera;
    private int anio;
    private List<Estudiante> estudiantesInscriptos;
    private List<Estudiante> estudiantesGraduados;

    public ReporteCarreraDTO(Carrera carrera, int anio,
                             List<Estudiante> inscriptos,
                             List<Estudiante> graduados) {
        this.carrera = carrera;
        this.anio = anio;
        this.estudiantesInscriptos = inscriptos;
        this.estudiantesGraduados = graduados;
    }

    public Carrera getCarrera() {
        return carrera;
    }

    public void setCarrera(Carrera carrera) {
        this.carrera = carrera;
    }

    public int getAnio() {
        return anio;
    }

    public void setAnio(int anio) {
        this.anio = anio;
    }

    public List<Estudiante> getEstudiantesInscriptos() {
        return estudiantesInscriptos;
    }

    public void setEstudiantesInscriptos(List<Estudiante> estudiantesInscriptos) {
        this.estudiantesInscriptos = estudiantesInscriptos;
    }

    public List<Estudiante> getEstudiantesGraduados() {
        return estudiantesGraduados;
    }

    public void setEstudiantesGraduados(List<Estudiante> estudiantesGraduados) {
        this.estudiantesGraduados = estudiantesGraduados;
    }

    @Override
    public String toString() {
        return "ReporteCarreraDTO{" +
                "carrera=" + carrera.getNombre_carrera() +
                ", anio=" + anio +
                ", estudiantesInscriptos=" + estudiantesInscriptos +
                ", estudiantesGraduados=" + estudiantesGraduados +
                '}';
    }
}
