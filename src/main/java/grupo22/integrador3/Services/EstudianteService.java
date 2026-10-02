package grupo22.integrador3.Services;

import grupo22.integrador3.Entitites.Estudiante;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("EstudianteService")
public class EstudianteService implements BaseService<Estudiante>{
    @Override
    public List<Estudiante> findAll() throws Exception {
        //TODO:
        return List.of();
    }

    @Override
    public Estudiante findById(Long id) throws Exception {
        //TODO:
        return null;
    }

    @Override
    public Estudiante save(Estudiante entity) throws Exception {
        //TODO:
        return null;
    }

    @Override
    public Estudiante update(Long id, Estudiante entity) throws Exception {
        //TODO:
        return null;
    }

    @Override
    public boolean delete(Long id) throws Exception {
        //TODO:
        return false;
    }
}
