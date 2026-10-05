package grupo22.integrador3;

import grupo22.integrador3.Utils.CarreraCSVLoader;
import grupo22.integrador3.Utils.EstudianteCSVLoader;
import grupo22.integrador3.Utils.EstudianteCarreraCSVLoader;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class Integrador3Application {

    @Autowired
    private EstudianteCSVLoader estudianteCSVLoader;
    @Autowired
    private CarreraCSVLoader carreraCSVLoader;
    @Autowired
    private EstudianteCarreraCSVLoader estudianteCarreraCSVLoader;

    public static void main(String[] args) {
        SpringApplication.run(Integrador3Application.class, args);
    }

    @PostConstruct
    public void init() throws IOException {
        estudianteCSVLoader.load();
        carreraCSVLoader.load();
        estudianteCarreraCSVLoader.load();
    }
}
