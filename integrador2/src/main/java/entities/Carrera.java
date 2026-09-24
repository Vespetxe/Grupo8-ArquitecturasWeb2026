package entities;

import jakarta.persistence.*;

@Entity
public class Carrera {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private int id_carrera;

    @Column(name="carrera")
    private String nombre_carrera;

    @Column(name="duracion")
    private int duracion_carrera;

    public Carrera(int id_carrera, String nombre_carrera, int duracion_carrera) {
        this.id_carrera = id_carrera;
        this.nombre_carrera = nombre_carrera;
        this.duracion_carrera = duracion_carrera;
    }

    public Carrera() {

    }

    public int getDuracion_carrera() {
        return duracion_carrera;
    }

    public void setDuracion_carrera(int duracion_carrera) {
        this.duracion_carrera = duracion_carrera;
    }

    public String getNombre_carrera() {
        return nombre_carrera;
    }

    public void setNombre_carrera(String nombre_carrera) {
        this.nombre_carrera = nombre_carrera;
    }

    public void setId_carrera(int id_carrera) {
        this.id_carrera = id_carrera;
    }

    public int getId_carrera() {
        return id_carrera;
    }

    @Override
    public String toString() {
        return "Carrera{" +
                "duracion_carrera=" + duracion_carrera +
                ", nombre_carrera='" + nombre_carrera + '\'' +
                ", id_carrera=" + id_carrera +
                '}';
    }
}
