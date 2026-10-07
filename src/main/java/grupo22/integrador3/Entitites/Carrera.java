package grupo22.integrador3.Entitites;


import jakarta.persistence.*;

@Entity
public class Carrera {

    @Id
    private Long id_carrera;

    @Column(name="carrera")
    private String nombre_carrera;

    @Column(name="duracion")
    private Integer duracion_carrera;

    public Carrera(Long id_carrera, String nombre_carrera, Integer duracion_carrera) {
        this.id_carrera = id_carrera;
        this.nombre_carrera = nombre_carrera;
        this.duracion_carrera = duracion_carrera;
    }

    public Carrera(Long id_carrera) {
        this.id_carrera = id_carrera;
    }

    public Carrera() {

    }

    public Integer getDuracion_carrera() {
        return duracion_carrera;
    }

    public void setDuracion_carrera(Integer duracion_carrera) {
        this.duracion_carrera = duracion_carrera;
    }

    public String getNombre_carrera() {
        return nombre_carrera;
    }

    public void setNombre_carrera(String nombre_carrera) {
        this.nombre_carrera = nombre_carrera;
    }

    public Long getId_carrera() {
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