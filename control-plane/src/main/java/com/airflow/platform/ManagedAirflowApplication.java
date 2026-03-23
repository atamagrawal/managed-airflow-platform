package com.airflow.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Managed Airflow Control Plane
 */
@SpringBootApplication
@EnableScheduling
public class ManagedAirflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManagedAirflowApplication.class, args);
    }
}
