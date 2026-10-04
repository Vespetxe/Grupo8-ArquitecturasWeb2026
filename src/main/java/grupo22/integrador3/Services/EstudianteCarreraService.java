package grupo22.integrador3.Services;

import grupo22.integrador3.DTO.CarreraInscriptosDTO;
import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Entitites.EstudianteCarrera;
import grupo22.integrador3.Repositories.EstudianteCarreraRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service("EstudianteCarreraService")
public class EstudianteCarreraService implements BaseService<EstudianteCarrera>{
    @Autowired
    private EstudianteCarreraRepository estudianteCarreraRepository;

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
