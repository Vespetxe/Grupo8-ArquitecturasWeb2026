package entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Estudiante {

    @Id
    private int DNI;

    @Column
    private String nombre;

    @Column
    private String apellido;

    @Column
    private int edad;

    @Column
    private String genero;

    @Column
    private String ciudad;

    @Column(name="LU")
    private int libreta_estudiantil;

    public Estudiante(int libreta_estudiantil, String ciudad, String genero, int edad, String apellido, String nombre, int DNI) {
        this.libreta_estudiantil = libreta_estudiantil;
        this.ciudad = ciudad;
        this.genero = genero;
        this.edad = edad;
        this.apellido = apellido;
        this.nombre = nombre;
        this.DNI = DNI;
    }

    public Estudiante() {

    }

    public int getLibreta_estudiantil() {
        return libreta_estudiantil;
    }

    public void setLibreta_estudiantil(int libreta_estudiantil) {
        this.libreta_estudiantil = libreta_estudiantil;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public String getGenero() {
        return genero;
    }

    public void setGenero(String genero) {
        this.genero = genero;
    }

    public int getEdad() {
        return edad;
    }

    public void setEdad(int edad) {
        this.edad = edad;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getDNI() {
        return DNI;
    }

    @Override
    public String toString() {
        return "Estudiante{" +
                "DNI=" + DNI +
                ", nombre='" + nombre + '\'' +
                ", apellido='" + apellido + '\'' +
                ", edad=" + edad +
                ", genero='" + genero + '\'' +
                ", ciudad='" + ciudad + '\'' +
                ", libreta_estudiantil=" + libreta_estudiantil +
                '}';
    }
}
