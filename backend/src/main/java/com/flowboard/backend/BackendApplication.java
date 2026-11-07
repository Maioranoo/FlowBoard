package com.flowboard.backend;


//importa funcionalidades prontas igual java.Scanner do apache.
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // liga as tarefas agendadas (@Scheduled), como a limpeza diária de visitantes
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
