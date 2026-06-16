package dev.kuku.knodeledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class KnodeledgeSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnodeledgeSpringApplication.class, args);
    }
}
