package grupo22.integrador3.Services;

import grupo22.integrador3.DTO.CarreraInscriptosDTO;
import grupo22.integrador3.Entitites.Carrera;
import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Repositories.CarreraRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service("CarreraService")
public class CarreraService implements BaseService<Carrera, Long>{

    @Autowired
    private CarreraRepository carreraRepository;

    @Override
    @Transactional
    public List<Carrera> findAll() throws Exception {
        return carreraRepository.findAll();
    }

    @Override
    @Transactional
    public Carrera findById(Long id) throws Exception {
        try {
            Optional<Carrera> carrera = carreraRepository.findById(id);
            return carrera.get();
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    @Transactional
    public Carrera save(Carrera entity) throws Exception {
        try {
            return carreraRepository.save(entity);
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    @Transactional
    public Carrera update(Long id, Carrera entity) throws Exception {
        try {
            Optional<Carrera> carreraOpcional = carreraRepository.findById(id);
            // Verifica que la carrera exista
            Carrera carrera = carreraOpcional.get();
            carrera = carreraRepository.save(entity);
            return carrera;
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean delete(Long id) throws Exception {
        try {
            if (carreraRepository.existsById(id)) {
                carreraRepository.deleteById(id);
                return true;
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    //Ejercicio 2 Inciso F
    @Transactional
    public List<CarreraInscriptosDTO> getInscriptosPorCarrera() throws Exception {
        List<CarreraInscriptosDTO> carreras = new ArrayList<>();

        try {
            carreras = carreraRepository.obtenerInscriptosPorCarrera();
        }
        catch (Exception e) {
            throw new Exception(e.getMessage());
        }
        return carreras;
    }
}
