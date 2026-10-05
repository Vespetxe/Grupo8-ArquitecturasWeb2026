package grupo22.integrador3.Utils;

import grupo22.integrador3.Entitites.Carrera;
import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Repositories.CarreraRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@Component
public class CarreraCSVLoader {
    private final CarreraRepository carreraRepository;

    @Autowired
    public CarreraCSVLoader(CarreraRepository carreraRepository) {
        this.carreraRepository = carreraRepository;
    }

    public void load() throws IOException {
        File csv = ResourceUtils.getFile("src/main/java/grupo22/integrador3/CSV/carreras.csv");

        try(FileReader fileReader = new FileReader(csv);
            CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(fileReader)){

            for (CSVRecord csvRecord : csvParser) {
                Carrera carrera = new Carrera(Long.parseLong(csvRecord.get("id_carrera")));
                carrera.setNombre_carrera(csvRecord.get("carrera"));
                carrera.setDuracion_carrera(Integer.parseInt(csvRecord.get("duracion")));

                carreraRepository.save(carrera);
            }
        }
    }
}
