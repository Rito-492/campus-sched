package com.campus.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class CampusEventsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusEventsApplication.class, args);
    }
}
