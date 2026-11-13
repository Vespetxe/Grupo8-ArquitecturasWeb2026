package ar.edu.ticketya.identidad.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationSafetyTest {
    @Test void rejectsBusinessAndHistoricalDatabasesBeforeConnecting(){
        for(String schema:new String[]{"ticketya_e2_docker_compras","ticketya_e2_docker_eventos","compras_test","mysql","ty_e2_identity_bad"}){
            var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:mysql://mysql:3306/"+schema)
                .withProperty("spring.datasource.username","ty_identidad").withProperty("spring.datasource.password","not-a-secret");
            assertThrows(IllegalStateException.class,()->new DataSourceConfiguration().dataSource(env));
        }
    }
    @Test void rejectsAdministrativeDatasourceUserBeforeConnecting(){
        var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:mysql://mysql:3306/ticketya_e2_docker_identidad")
            .withProperty("spring.datasource.username","root").withProperty("spring.datasource.password","not-a-secret");
        assertThrows(IllegalStateException.class,()->new DataSourceConfiguration().dataSource(env));
    }
}
