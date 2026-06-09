package pl.jakubtworek.chatsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ChatSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatSystemApplication.class, args);
    }
}
