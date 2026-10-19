package pl.jakubtworek.chatsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@EnableScheduling
@SpringBootApplication
public class ChatSystemApplication {
    @Bean Clock clock() { return Clock.systemUTC(); }
    public static void main(String[] args) {
        SpringApplication.run(ChatSystemApplication.class, args);
    }
}
