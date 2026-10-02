package grupo22.integrador3.Services;

import grupo22.integrador3.Entitites.Carrera;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("CarreraService")
public class CarreraService implements BaseService<Carrera>{

    @Override
    public List<Carrera> findAll() throws Exception {
        //TODO:
        return List.of();
    }

    @Override
    public Carrera findById(Long id) throws Exception {
        //TODO:
        return null;
    }

    @Override
    public Carrera save(Carrera entity) throws Exception {
        //TODO:
        return null;
    }

    @Override
    public Carrera update(Long id, Carrera entity) throws Exception {
        //TODO:
        return null;
    }

    @Override
    public boolean delete(Long id) throws Exception {
        //TODO:
        return false;
    }
}
