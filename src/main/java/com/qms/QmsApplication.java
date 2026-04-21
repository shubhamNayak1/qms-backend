package com.qms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableAsync
@EnableScheduling
public class QmsApplication {

//    public static void main(String[] args) {
//        System.out.println(new BCryptPasswordEncoder(12).encode("Admin@123"));
//    }

    public static void main(String[] args) {
        SpringApplication.run(QmsApplication.class, args);
    }
}
