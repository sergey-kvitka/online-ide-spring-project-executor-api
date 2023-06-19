package com.kvitka.idejarexecutor.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @Bean
    public Runtime runtime() {
        return Runtime.getRuntime();
    }

    @Bean
    public String userDir() {
        return System.getProperty("user.dir");
    }
}
