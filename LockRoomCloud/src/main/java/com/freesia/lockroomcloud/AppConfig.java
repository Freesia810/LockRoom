package com.freesia.lockroomcloud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class AppConfig {
    @Bean
    public DataSource dataSource(){

        DriverManagerDataSource d = new DriverManagerDataSource() ;
        d.setUrl("***");
        d.setUsername("**");
        d.setPassword("**");
        return d;
    }
}
