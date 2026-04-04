package com.airflow.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate for outbound calls to Airflow (auth token, DAG triggers, connection sync).
 * <p>
 * Uses {@link SimpleClientHttpRequestFactory} ({@code HttpURLConnection}) instead of
 * {@code JdkClientHttpRequestFactory}: the JDK {@code HttpClient} has been unreliable
 * against local Docker-published Airflow (uvicorn) — e.g. {@code 400 Invalid HTTP request received}
 * (HTTP/2 upgrade on cleartext) and {@code HTTP/1.1 header parser received no bytes} on
 * {@code /auth/token}. {@code HttpURLConnection} avoids those paths. PATCH is supported
 * on Java 11+ for Airflow connection upserts.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(60_000);
        return new RestTemplate(factory);
    }
}
