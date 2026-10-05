package grupo22.integrador3.Services;

import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Repositories.EstudianteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("EstudianteService")
public class EstudianteService implements BaseService<Estudiante>{


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
