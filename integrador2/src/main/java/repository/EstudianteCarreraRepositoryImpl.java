package repository;

import com.opencsv.CSVReader;
import dto.ReporteCarreraDTO;
import entities.Carrera;
import entities.Estudiante;
import entities.EstudianteCarrera;
import factory.JPAUtil;
import jakarta.persistence.EntityManager;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
                        "No se encontró el recurso: " + nombreArchivo);
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

                    EstudianteCarrera estudianteCarrera = new EstudianteCarrera(estudiante, carrera,
                            LocalDate.parse(linea[2]), LocalDate.parse(linea[3]));

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

    //Resolucion Ejercicio 2 Inciso B
    @Override
    public void matricularEstudiante(String dni, int idCarrera) {
        EntityManager em = JPAUtil.getEntityManager();

        try {
            em.getTransaction().begin();

            Estudiante estudiante = em.find(Estudiante.class, dni);

            Carrera carrera = em.find(Carrera.class, idCarrera);

            EstudianteCarrera estudianteCarrera = new EstudianteCarrera();

            estudianteCarrera.setEstudiante(estudiante);
            estudianteCarrera.setCarrera(carrera);

            estudianteCarrera.setInscripcion(LocalDate.now());

            em.persist(estudianteCarrera);

            em.getTransaction().commit();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    //Resolucion Ejercicio 3
    @Override
    public List<ReporteCarreraDTO> getReporteCarreras() {

        EntityManager em = JPAUtil.getEntityManager();

        List<ReporteCarreraDTO> reporte = new ArrayList<>();

        LocalDate fechaInicio = em
                .createQuery(
                        "SELECT MIN(ec.inscripcion) FROM EstudianteCarrera ec",
                        LocalDate.class
                )
                .getSingleResult();

        int anioInicio = fechaInicio.getYear();
        int anioFin = LocalDate.now().getYear();

        List<Carrera> carreras = em.createQuery(
                "SELECT c FROM Carrera c ORDER BY c.nombre_carrera",
                Carrera.class
        ).getResultList();

        for(Carrera carrera : carreras) {

            for(int anio = anioInicio; anio <= anioFin; anio++) {

                List<Estudiante> inscriptos = em.createQuery(
                                "SELECT ec.estudiante " +
                                        "FROM EstudianteCarrera ec " +
                                        "WHERE ec.carrera = :carrera " +
                                        "AND YEAR(ec.inscripcion) = :anio",
                                Estudiante.class
                        )
                        .setParameter("carrera", carrera)
                        .setParameter("anio", anio)
                        .getResultList();


                List<Estudiante> graduados = em.createQuery(
                                "SELECT ec.estudiante " +
                                        "FROM EstudianteCarrera ec " +
                                        "WHERE ec.carrera = :carrera " +
                                        "AND YEAR(ec.graduacion) = :anio",
                                Estudiante.class
                        )
                        .setParameter("carrera", carrera)
                        .setParameter("anio", anio)
                        .getResultList();


                ReporteCarreraDTO dto =
                        new ReporteCarreraDTO(
                                carrera,
                                anio,
                                inscriptos,
                                graduados
                        );

                reporte.add(dto);

            }
        }

        em.close();

        return reporte;
    }

}
