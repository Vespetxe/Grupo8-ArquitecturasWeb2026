package grupo22.integrador3.Services;

import grupo22.integrador3.DTO.ReporteCarreraDTO;
import grupo22.integrador3.Entitites.Carrera;
import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Entitites.EstudianteCarrera;
import grupo22.integrador3.Repositories.CarreraRepository;
import grupo22.integrador3.Repositories.EstudianteCarreraRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service("EstudianteCarreraService")
public class EstudianteCarreraService implements BaseService<EstudianteCarrera>{

    @Autowired
    private EstudianteCarreraRepository estudianteCarreraRepository;

    @Autowired
    private CarreraRepository carreraRepository;

    // Ejercicio 2 Inciso H
    public List<ReporteCarreraDTO> getReporteCarreras() {

        List<ReporteCarreraDTO> reporte = new ArrayList<>();

        // Carreras ordenadas alfabeticamente
        List<Carrera> carreras = carreraRepository.obtenerCarrerasOrdenadas();

        // Recorremos cada carrera
        for (Carrera carrera : carreras) {

            // Buscamos la primera inscripcion de esa carrera
            LocalDate fechaInicio =
                    estudianteCarreraRepository.obtenerPrimeraInscripcion(carrera);

            int anioInicio = fechaInicio.getYear();
            int anioFin = LocalDate.now().getYear();

            // Recorremos desde el primer año de inscripcion hasta el actual
            for (int anio = anioInicio; anio <= anioFin; anio++) {

                List<Estudiante> inscriptos =
                        estudianteCarreraRepository
                                .obtenerInscriptosPorCarreraYAnio(carrera, anio);

                List<Estudiante> graduados =
                        estudianteCarreraRepository
                                .obtenerGraduadosPorCarreraYAnio(carrera, anio);

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

        return reporte;
    }

    @Override
    public List<EstudianteCarrera> findAll() throws Exception {
        //TODO:
        return List.of();
    }

    @Override
    public EstudianteCarrera findById(Long id) throws Exception {
        //TODO:
        return null;
    }

    @Override
    public EstudianteCarrera save(EstudianteCarrera entity) throws Exception {
        //TODO:
        return null;
    }

    @Override
    public EstudianteCarrera update(Long id, EstudianteCarrera entity) throws Exception {
        //TODO:
        return null;
    }

    @Override
    public boolean delete(Long id) throws Exception {
        //TODO:
        return false;
    }
}
