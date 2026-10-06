package grupo22.integrador3.Services;

import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Repositories.EstudianteRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service("EstudianteService")
public class EstudianteService implements BaseService<Estudiante, Long>{

    @Autowired
    private EstudianteRepository estudianteRepository;

    // Ejercicio 2 Inciso G
    public List<Estudiante> obtenerEstudiantesByCarreraAndCiudad(
            int idCarrera,
            String ciudad) {

        return estudianteRepository
                .obtenerEstudiantesByCarreraAndCiudad(idCarrera, ciudad);
    }


    @Override
    @Transactional
    public List<Estudiante> findAll() throws Exception {
        return estudianteRepository.findAll();
    }

    @Override
    @Transactional
    public Estudiante findById(Long id) throws Exception {
        try {
            Optional<Estudiante> estudiante = estudianteRepository.findById(id);
            return estudiante.get();
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    @Transactional
    public Estudiante save(Estudiante entity) throws Exception {
        try {
            return estudianteRepository.save(entity);
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    @Transactional
    public Estudiante update(Long id, Estudiante entity) throws Exception {
        try {
            Optional<Estudiante> estudianteOpcional = estudianteRepository.findById(id);
            Estudiante estudiante = estudianteOpcional.get();
            estudiante = estudianteRepository.save(estudiante);
            return estudiante;
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean delete(Long id) throws Exception {
        try {
            if (estudianteRepository.existsById(id)) {
                estudianteRepository.deleteById(id);
                return true;
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Transactional
    public Estudiante obtenerEstudiantePorLU(Integer libreta_universitaria) throws Exception {
        try {
            Optional<Estudiante> estudiante = estudianteRepository.obtenerEstudiantePorLU(libreta_universitaria);
            return estudiante.get();
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Transactional
    public List<Estudiante> obtenerEstudiantesPorGenero(String genero) throws Exception {
        List<Estudiante> estudiantes = new ArrayList<>();

        try {
            estudiantes = estudianteRepository.obtenerEstudiantesPorGenero(genero);
        }
        catch (Exception e) {
            throw new Exception(e.getMessage());
        }
        return estudiantes;
    }
}
