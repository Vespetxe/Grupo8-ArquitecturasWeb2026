package example.microservicio_inventario_reservas.entities;
import jakarta.persistence.*;
import example.microservicio_inventario_reservas.enums.EstadoInventario;
@Entity @Table(name="control_evento")
public class ControlEvento {
    @Id @Column(name="id_evento") private Long id;
    @Enumerated(EnumType.STRING) private EstadoInventario estado;
    @Column(nullable=false,columnDefinition="boolean default false") private boolean colaActiva;
    @Column(nullable=false,columnDefinition="integer default 2") private int colaTanda=2;
    @Column(nullable=false,columnDefinition="integer default 120") private int colaSegundos=120;
    @Column(nullable=false,columnDefinition="bigint default 0") private long colaSecuencia;
    public boolean isColaActiva(){return colaActiva;}
    public int getColaTanda(){return colaTanda;}
    public int getColaSegundos(){return colaSegundos;}
    public void activarCola(int tanda,int segundos){colaActiva=true;colaTanda=tanda;colaSegundos=segundos;}
    public long siguienteCola(){colaSecuencia=Math.addExact(colaSecuencia,1);return colaSecuencia;}
    public EstadoInventario getEstado(){return estado;}
    public void setEstado(EstadoInventario estado){this.estado=estado;}
}
