package com.ridesharing.mvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RideSharingMvpApplication {
    public static void main(String[] args) {
        SpringApplication.run(RideSharingMvpApplication.class, args);
    }
}
