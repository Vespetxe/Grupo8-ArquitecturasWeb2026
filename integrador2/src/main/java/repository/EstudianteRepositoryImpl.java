package repository;

import com.opencsv.CSVReader;
import entities.Estudiante;
import factory.JPAUtil;
import jakarta.persistence.EntityManager;

import java.io.FileReader;

public class EstudianteRepositoryImpl implements EstudianteRepository {

    @Override
    public void insertarDesdeCSV(String rutaArchivo) {
        EntityManager em = JPAUtil.getEntityManager();

        try (CSVReader reader = new CSVReader(new FileReader(rutaArchivo))) {
            String[] linea;
            reader.readNext();

            em.getTransaction().begin();

            while ((linea = reader.readNext()) != null) {
                Estudiante estudiante = new Estudiante(Integer.parseInt(linea[0]));
                estudiante.setNombre(linea[1]);
                estudiante.setApellido(linea[2]);
                estudiante.setEdad(Integer.parseInt(linea[3]));
                estudiante.setGenero(linea[4]);
                estudiante.setCiudad(linea[5]);
                estudiante.setLibreta_estudiantil(Integer.parseInt(linea[6]));

                em.persist(estudiante);
            }

            em.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    @Override
    public void saveEstudiante(Estudiante estudiante) {
        EntityManager em = JPAUtil.getEntityManager();
        em.getTransaction().begin();

        if (em.find(Estudiante.class, estudiante.getDNI()) == null) {
            em.persist(estudiante);
        } else {
            em.merge(estudiante);
        }

        em.getTransaction().commit();
        em.close();
    }
}
