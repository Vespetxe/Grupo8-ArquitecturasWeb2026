package repository;

import com.opencsv.CSVReader;
import entities.Estudiante;
import factory.JPAUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class EstudianteRepositoryImpl implements EstudianteRepository {

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
                    Estudiante estudiante =
                            new Estudiante(Integer.parseInt(linea[0]));

                    estudiante.setNombre(linea[1]);
                    estudiante.setApellido(linea[2]);
                    estudiante.setEdad(Integer.parseInt(linea[3]));
                    estudiante.setGenero(linea[4]);
                    estudiante.setCiudad(linea[5]);
                    estudiante.setLibreta_estudiantil(
                            Integer.parseInt(linea[6])
                    );

                    em.persist(estudiante);
                }

                em.getTransaction().commit();
            }

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

    @Override
    public List<Estudiante> findEstudiantesByCarreraAndCiudad(int idCarrera, String ciudad) {

        EntityManager em = JPAUtil.getEntityManager();

        List<Estudiante> estudiantes = em.createQuery(
                "SELECT e FROM EstudianteCarrera ec " +
                        "JOIN ec.estudiante e " +
                        "JOIN ec.carrera c " +
                        "WHERE c.id_carrera = :idCarrera AND e.ciudad = :ciudad",
                Estudiante.class
        )
                .setParameter("idCarrera", idCarrera)
                .setParameter("ciudad", ciudad)
                .getResultList();

        em.close();
        return estudiantes;
    }

    @Override
    public List<Estudiante> obtenerTodosOrdenados() {
        EntityManager em = JPAUtil.getEntityManager();

        try {
            TypedQuery<Estudiante> query = em.createQuery(
                    "SELECT e FROM Estudiante e ORDER BY e.apellido, e.nombre",
                    Estudiante.class
            );

            return query.getResultList();

        } finally {
            em.close();
        }
    }
}
