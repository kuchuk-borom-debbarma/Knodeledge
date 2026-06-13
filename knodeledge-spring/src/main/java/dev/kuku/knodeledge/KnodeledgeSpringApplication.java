package dev.kuku.knodeledge;

import dev.kuku.topotracer.spring.TracingClientHttpRequestInterceptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@SpringBootApplication
public class KnodeledgeSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnodeledgeSpringApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate(TracingClientHttpRequestInterceptor interceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(Collections.singletonList(interceptor));
        return restTemplate;
    }
}
