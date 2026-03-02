package pl.jakubtworek.backend_systems_lab_stage_1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(
		exclude = { DataSourceAutoConfiguration.class }
)public class BackendSystemsLabStage1Application {

	public static void main(String[] args) {
		SpringApplication.run(BackendSystemsLabStage1Application.class, args);
	}

}
