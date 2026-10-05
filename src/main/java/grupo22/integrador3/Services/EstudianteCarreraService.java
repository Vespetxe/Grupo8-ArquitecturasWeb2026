package grupo22.integrador3.Services;

import grupo22.integrador3.DTO.CarreraInscriptosDTO;
import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Entitites.EstudianteCarrera;
import grupo22.integrador3.Entitites.EstudianteCarreraPK;
import grupo22.integrador3.Repositories.EstudianteCarreraRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service("EstudianteCarreraService")
public class EstudianteCarreraService implements BaseService<EstudianteCarrera, EstudianteCarreraPK>{

    @Autowired
    private EstudianteCarreraRepository estudianteCarreraRepository;

    @Override
    @Transactional
    public List<EstudianteCarrera> findAll() throws Exception {
        return estudianteCarreraRepository.findAll();
    }

    @Override
    @Transactional
    public EstudianteCarrera findById(EstudianteCarreraPK id) throws Exception {
        try {
            Optional<EstudianteCarrera> estudianteCarrera = estudianteCarreraRepository.findById(id);
            return estudianteCarrera.get();
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    @Transactional
    public EstudianteCarrera save(EstudianteCarrera entity) throws Exception {
        try {
            return estudianteCarreraRepository.save(entity);
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    @Transactional
    public EstudianteCarrera update(EstudianteCarreraPK id, EstudianteCarrera entity) throws Exception {
        try {
            Optional<EstudianteCarrera> estudianteCarreraOpcional = estudianteCarreraRepository.findById(id);
            EstudianteCarrera estudianteCarrera = estudianteCarreraOpcional.get();
            estudianteCarrera = estudianteCarreraRepository.save(estudianteCarrera);
            return estudianteCarrera;
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean delete(EstudianteCarreraPK id) throws Exception {
        try {
            if (estudianteCarreraRepository.existsById(id)) {
                estudianteCarreraRepository.deleteById(id);
                return true;
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Transactional
    public List<CarreraInscriptosDTO> obtenerEstudiantesPorGenero(String genero) throws Exception {
        List<CarreraInscriptosDTO> carreras = new ArrayList<>();

        try {
            carreras = estudianteCarreraRepository.obtenerInscriptosPorCarrera();
        }
        catch (Exception e) {
            throw new Exception(e.getMessage());
        }
        return carreras;
    }
}
