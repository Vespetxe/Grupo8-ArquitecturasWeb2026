package ar.edu.ticketya.identidad.config;

import com.zaxxer.hikari.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfiguration {
    @Bean DataSource dataSource(Environment env){
        String url=env.getRequiredProperty("spring.datasource.url");
        String user=env.getRequiredProperty("spring.datasource.username");
        boolean memoryTest=url.equals("jdbc:h2:mem:identity;MODE=MySQL;DB_CLOSE_DELAY=-1") && user.equals("sa");
        boolean ownMysql=url.matches("jdbc:mysql://[^/]+/(ticketya_e2_docker_identidad|ty_e2_identity_[a-f0-9]{16})(\\?.*)?") && user.equals("ty_identidad");
        if(!memoryTest && !ownMysql)throw new IllegalStateException("Identity datasource guard rejected configuration");
        HikariConfig config=new HikariConfig();
        config.setJdbcUrl(url);config.setUsername(user);config.setPassword(env.getRequiredProperty("spring.datasource.password"));
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }
}
