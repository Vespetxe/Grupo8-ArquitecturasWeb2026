package grupo22.integrador3.Utils;

import grupo22.integrador3.Entitites.Carrera;
import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Entitites.EstudianteCarrera;
import grupo22.integrador3.Repositories.EstudianteCarreraRepository;
import grupo22.integrador3.Services.CarreraService;
import grupo22.integrador3.Services.EstudianteService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;

@Component
public class EstudianteCarreraCSVLoader {
    final EstudianteCarreraRepository estudianteCarreraRepository;
    final EstudianteService estudianteService;
    final CarreraService carreraService;

    @Autowired
    public EstudianteCarreraCSVLoader(EstudianteCarreraRepository estudianteCarreraRepository, EstudianteService estudianteService, CarreraService carreraService) {
        this.estudianteCarreraRepository = estudianteCarreraRepository;
        this.estudianteService = estudianteService;
        this.carreraService = carreraService;
    }

    public void load() throws IOException {
        File csv = ResourceUtils.getFile("src/main/java/grupo22/integrador3/CSV/estudianteCarrera.csv");

        try(FileReader fileReader = new FileReader(csv);
            CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(fileReader)){

            for (CSVRecord csvRecord : csvParser) {
                Estudiante estudiante  = null;
                Carrera carrera = null;

                try{
                    estudiante = estudianteService.findById(Long.parseLong(csvRecord.get("id_estudiante")));
                    carrera = carreraService.findById(Long.parseLong(csvRecord.get("id_carrera")));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }


                EstudianteCarrera estudianteCarrera = new EstudianteCarrera();
                estudianteCarrera.setEstudiante(estudiante);
                estudianteCarrera.setCarrera(carrera);
                estudianteCarrera.setInscripcion(LocalDate.parse(csvRecord.get("inscripcion")));
                estudianteCarrera.setGraduacion(LocalDate.parse(csvRecord.get("graduacion")));

                estudianteCarreraRepository.save(estudianteCarrera);
            }
        }
    }
}
