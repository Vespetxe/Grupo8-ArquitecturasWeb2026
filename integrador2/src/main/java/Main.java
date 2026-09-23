import dto.CarreraDTO;
import repository.CarreraRepositoryImpl;

public class Main {
    public static void main(String[] args) {
        CarreraRepositoryImpl carreraRepository = new CarreraRepositoryImpl();

        carreraRepository.populateTable("src/main/resources/carreras.csv");

        for(CarreraDTO carrera : carreraRepository.findCarrerasConMasIncriptos()) {
            System.out.println(carrera.toString());
        }
    }
}
