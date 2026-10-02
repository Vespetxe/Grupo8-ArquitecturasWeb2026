package grupo22.integrador3.Services;

import grupo22.integrador3.Entitites.EstudianteCarrera;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("EstudianteCarreraService")
public class EstudianteCarreraService implements BaseService<EstudianteCarrera>{
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
