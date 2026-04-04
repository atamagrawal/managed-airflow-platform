package com.airflow.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI managedAirflowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowDeck API")
                        .description("FlowDeck operations API for Apache Airflow deployments, projects, DAGs, and tenants")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Platform Team")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
