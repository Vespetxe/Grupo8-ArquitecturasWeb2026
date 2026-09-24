import dto.CarreraDTO;
import repository.CarreraRepositoryImpl;
import repository.EstudianteRepositoryImpl;

public class Main {
    public static void main(String[] args) {
        CarreraRepositoryImpl carreraRepository = new CarreraRepositoryImpl();
        EstudianteRepositoryImpl estudianteRepository = new EstudianteRepositoryImpl();

        carreraRepository.insertarDesdeCSV("carreras.csv");

        for(CarreraDTO carrera : carreraRepository.obtenerCarrerasConMasIncriptos()) {
            System.out.println(carrera.toString());
        }
    }
}
