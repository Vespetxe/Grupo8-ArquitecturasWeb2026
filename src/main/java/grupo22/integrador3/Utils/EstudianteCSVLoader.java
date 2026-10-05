package grupo22.integrador3.Utils;

import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Repositories.EstudianteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

@Component
public class EstudianteCSVLoader {
    private final EstudianteRepository estudianteRepository;

    @Autowired
    public EstudianteCSVLoader(EstudianteRepository estudianteRepository) {
        this.estudianteRepository = estudianteRepository;
    }

    public void load() throws IOException {
        File csv = ResourceUtils.getFile("src/main/java/grupo22/integrador3/CSV/estudiantes.csv");

        try(FileReader fileReader = new FileReader(csv);
            CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(fileReader)){

            for (CSVRecord csvRecord : csvParser) {
                Estudiante estudiante = new Estudiante(Long.parseLong(csvRecord.get("DNI")));
                estudiante.setNombre(csvRecord.get("nombre"));
                estudiante.setApellido(csvRecord.get("apellido"));
                estudiante.setEdad(Integer.parseInt(csvRecord.get("edad")));
                estudiante.setGenero(csvRecord.get("genero"));
                estudiante.setCiudad(csvRecord.get("ciudad"));
                estudiante.setLibreta_estudiantil(Integer.parseInt(csvRecord.get("LU")));

                estudianteRepository.save(estudiante);
            }
        }
    }
}
