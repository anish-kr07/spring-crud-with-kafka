package com.example.spring_crud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

// @Value("${prop}")           — quick & dirty, single property, no validation
// @ConfigurationProperties    — type-safe POJO bound to a prefix, supports @Validated, IDE autocomplete, immutable records. Production
@Configuration
public class EnvironmentInfo {

    @Bean
    @Profile("dev")
    public String devBanner() {
        System.out.println(">>> Running with DEV profile (H2 in-memory, verbose SQL)");
        return "dev";
    }

    @Bean
    @Profile("prod")
    public String prodBanner() {
        System.out.println(">>> Running with PROD profile");
        return "prod";
    }


    @Bean
    @Profile("test")
    public String testBanner() {
        System.out.println(">>> Running with TEST profile (H2 in-memory, logging SQL)");
        return "test";
    }

}