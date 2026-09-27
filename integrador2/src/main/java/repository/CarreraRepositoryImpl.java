package repository;

import com.opencsv.CSVReader;
import dto.CarreraDTO;
import entities.Carrera;
import factory.JPAUtil;
import jakarta.persistence.EntityManager;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CarreraRepositoryImpl implements CarreraRepository {
    @Override
    public void insertarDesdeCSV(String nombreArchivo) {
        EntityManager em = JPAUtil.getEntityManager();

        try {
            InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream(nombreArchivo);

            if (input == null) {
                throw new FileNotFoundException(
                        "No se encontró el recurso: " + nombreArchivo
                );
            }

            try (CSVReader reader = new CSVReader(new InputStreamReader(input))) {

                String[] linea;
                reader.readNext();

                em.getTransaction().begin();

                while ((linea = reader.readNext()) != null) {
                    Carrera carrera = new Carrera(Integer.parseInt(linea[0]));

                    carrera.setNombre_carrera(linea[1]);
                    carrera.setDuracion_carrera(Integer.parseInt(linea[2]));

                    em.persist(carrera);
                }

                em.getTransaction().commit();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    // Resolucion Ejercicio 2 Inciso F
    @Override
    public List<CarreraDTO> obtenerCarrerasConMasIncriptos() {
        EntityManager em = JPAUtil.getEntityManager();
        List<CarreraDTO> carreras = new ArrayList<>();

        try{
            carreras = em.createQuery(
                    "SELECT new dto.CarreraDTO(c.id_carrera, c.nombre_carrera, COUNT(ec.estudiante.DNI)) " +
                            "FROM EstudianteCarrera ec JOIN ec.carrera c " +
                            "WHERE ec.carrera.id_carrera = c.id_carrera " +
                            "GROUP BY c.id_carrera, c.nombre_carrera " +
                            "ORDER BY COUNT(ec.estudiante.DNI) DESC",
                    CarreraDTO.class).getResultList();
        }catch (Exception e){
            System.out.println("Error al ejecutar la consulta: " + e.getMessage());
        }
        return carreras;
    }


    @Override
    public Carrera obtenerCarreraPorId (Integer id_carrera) {
        EntityManager em = JPAUtil.getEntityManager();

        Carrera carrera = em.createQuery(
                "SELECT c FROM Carrera c WHERE c.id_carrera = :id_carrera",
                Carrera.class).setParameter("id_carrera", id_carrera).getSingleResult();

        em.close();
        return carrera;
    }
}
