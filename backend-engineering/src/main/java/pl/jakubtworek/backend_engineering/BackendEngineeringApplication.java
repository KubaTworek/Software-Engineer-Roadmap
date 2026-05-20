package pl.jakubtworek.backend_engineering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(
		exclude = { DataSourceAutoConfiguration.class }
)public class BackendEngineeringApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendEngineeringApplication.class, args);
	}

}
