import dto.CarreraDTO;
import dto.ReporteCarreraDTO;
import entities.Estudiante;
import entities.EstudianteCarrera;
import repository.CarreraRepositoryImpl;
import repository.EstudianteCarreraRepositoryImpl;
import repository.EstudianteRepositoryImpl;

public class Main {
    public static void main(String[] args) {
        EstudianteRepositoryImpl estudianteRepository = new EstudianteRepositoryImpl();
        CarreraRepositoryImpl carreraRepository = new CarreraRepositoryImpl();
        EstudianteCarreraRepositoryImpl estudianteCarreraRepository = new EstudianteCarreraRepositoryImpl();

        estudianteRepository.insertarDesdeCSV("estudiantes.csv");
        carreraRepository.insertarDesdeCSV("carreras.csv");
        estudianteCarreraRepository.insertarDesdeCSV("estudianteCarrera.csv");

        System.out.println("Ejercicio 2.A:");
        System.out.println("\n");

        Estudiante estudiante2A = new Estudiante(54896347, "Brad", "Wybrew", 34, "Male", "Samagaltay", 58942);
        estudianteRepository.guardarEstudiante(estudiante2A);
        Estudiante estudiante2A_2 = estudianteRepository.obtenerEstudiantePorDNI(54896347);
        System.out.println(estudiante2A_2);

        System.out.println("\n");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("\n");
        System.out.println("Ejercicio 2.B:");
        System.out.println("\n");

        estudianteCarreraRepository.matricularEstudiante(54896347, 1);
        for (EstudianteCarrera ec : estudianteCarreraRepository.obtenerEstudianteCarreraPorEstudiante(54896347)) {
            System.out.println(ec);
        }

        System.out.println("\n");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("\n");
        System.out.println("Ejercicio 2.C:");
        System.out.println("\n");

        for (Estudiante e : estudianteRepository.obtenerEstudiantesOrdenados()) {
            System.out.println(e);
        }

        System.out.println("\n");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("\n");
        System.out.println("Ejercicio 2.D:");
        System.out.println("\n");

        Estudiante estudiante2D = estudianteRepository.obtenerEstudiantePorLU(58942);
        System.out.println(estudiante2D);

        System.out.println("\n");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("\n");
        System.out.println("Ejercicio 2.E:");
        System.out.println("\n");

        for (Estudiante e : estudianteRepository.obtenerEstudiantesPorGenero("Female")) {
            System.out.println(e);
        }

        System.out.println("\n");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("\n");
        System.out.println("Ejercicio 2.F:");
        System.out.println("\n");

        for(CarreraDTO c : carreraRepository.obtenerCarrerasConMasIncriptos()) {
            System.out.println(c);
        }

        System.out.println("\n");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("\n");
        System.out.println("Ejercicio 2.G:");
        System.out.println("\n");

        for (Estudiante e : estudianteRepository.obtenerEstudiantesByCarreraAndCiudad(12, "Rym")) {
            System.out.println(e);
        }

        System.out.println("\n");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("|||||||||||||||||||||||||||||||||||||||");
        System.out.println("\n");
        System.out.println("Ejercicio 3:");
        System.out.println("\n");

        for (ReporteCarreraDTO rc : estudianteCarreraRepository.getReporteCarreras()) {
            System.out.println(rc);
        }
    }
}
