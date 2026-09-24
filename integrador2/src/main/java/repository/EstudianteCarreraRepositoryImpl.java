package repository;

import com.opencsv.CSVReader;
import entities.Carrera;
import entities.Estudiante;
import entities.EstudianteCarrera;
import factory.JPAUtil;
import jakarta.persistence.EntityManager;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;

public class EstudianteCarreraRepositoryImpl implements EstudianteCarreraRepository {

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

                    EstudianteRepositoryImpl er = new EstudianteRepositoryImpl();
                    Estudiante estudiante = er.obtenerEstudiantePorDNI(Integer.parseInt(linea[0]));
                    CarreraRepositoryImpl cr = new CarreraRepositoryImpl();
                    Carrera carrera = cr.obtenerCarreraPorId(Integer.parseInt(linea[1]));

                    EstudianteCarrera estudianteCarrera = new EstudianteCarrera(estudiante, carrera, LocalDate.parse(linea[2]), LocalDate.parse(linea[3]));

                    em.persist(estudianteCarrera);
                }

                em.getTransaction().commit();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            em.close();
        }
    }
}
