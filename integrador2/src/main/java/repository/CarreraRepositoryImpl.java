package repository;

import dto.CarreraDTO;
import entities.Carrera;
import factory.JPAUtil;
import jakarta.persistence.EntityManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CarreraRepositoryImpl implements CarreraRepository {

    @Override
    public void insertCarrera(int id_carrera, String nombre_carrera, int duracion_carrera) {
        //TODO:
    }

    @Override
    public List<CarreraDTO> findCarrerasConMasIncriptos() {
        EntityManager em = JPAUtil.getEntityManager();
        List<CarreraDTO> carreras = new ArrayList<>();

        try{
           carreras = em.createQuery("SELECT c.id_carrera, c.nombre_carrera, COUNT(ec.dni_estudiante) AS cantidad_inscriptos " +
                   "FROM Carrera c " +
                   "INNER JOIN EstudianteCarrera ec ON c.id_carrera = ec.id_carrera " +
                   "GROUP BY c.id_carrera, c.nombre_carrera " +
                   "ORDER BY cantidad_inscriptos DESC;",  CarreraDTO.class).getResultList();
        }catch (Exception e){
            System.out.println("Error al ejecutar la consulta: " + e.getMessage());
        }
        return carreras;
    }
}
