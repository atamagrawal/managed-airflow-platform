package com.airflow.platform.service;

import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.util.AirflowApiUrlUtils;
import com.airflow.platform.util.AirflowVersionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Calls Airflow HTTP APIs (JWT for 3+, basic auth for 2.x), aligned with {@link ProjectService} DAG triggers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AirflowRemoteApiService {

    private final RestTemplate restTemplate;

    @Value("${airflow.api.username:admin}")
    private String airflowApiUsername;

    @Value("${airflow.api.password:admin}")
    private String airflowApiPassword;

    /**
     * Creates or updates a connection on the target Airflow instance.
     */
    public void upsertConnection(String webserverUrl, String airflowVersion, String connectionId,
                                 String connType, String description, String host, String login, String password,
                                 Integer port, String schema, String extraJson) {
        String baseUrl = AirflowApiUrlUtils.normalizeAirflowBaseUrl(webserverUrl);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new DeploymentException("Airflow base URL is empty");
        }
        Map<String, Object> body = buildConnectionJson(connectionId, connType, description, host, login, password, port, schema, extraJson);
        if (AirflowVersionUtils.isAirflow3OrLater(airflowVersion)) {
            upsertConnectionV3(baseUrl, connectionId, body);
        } else {
            upsertConnectionV2(baseUrl, connectionId, body);
        }
    }

    private Map<String, Object> buildConnectionJson(String connectionId, String connType, String description,
                                                    String host, String login, String password,
                                                    Integer port, String schema, String extraJson) {
        Map<String, Object> body = new HashMap<>();
        body.put("connection_id", connectionId);
        body.put("conn_type", connType);
        if (StringUtils.hasText(description)) {
            body.put("description", description);
        }
        if (StringUtils.hasText(host)) {
            body.put("host", host);
        }
        if (StringUtils.hasText(login)) {
            body.put("login", login);
        }
        if (password != null) {
            body.put("password", password);
        }
        if (port != null) {
            body.put("port", port);
        }
        if (StringUtils.hasText(schema)) {
            body.put("schema", schema);
        }
        if (StringUtils.hasText(extraJson)) {
            body.put("extra", extraJson);
        }
        return body;
    }

    private void upsertConnectionV2(String baseUrl, String connectionId, Map<String, Object> body) {
        String enc = UriUtils.encodePathSegment(connectionId, StandardCharsets.UTF_8);
        String getUrl = baseUrl + "/api/v1/connections/" + enc;
        boolean exists = resourceExistsV2(getUrl);
        String methodUrl = exists ? getUrl : baseUrl + "/api/v1/connections";
        HttpMethod method = exists ? HttpMethod.PATCH : HttpMethod.POST;
        log.info("Airflow 2.x connection {} {} ({})", method, connectionId, methodUrl);
        exchangeWithBasicAuth(methodUrl, method, body);
    }

    private void upsertConnectionV3(String baseUrl, String connectionId, Map<String, Object> body) {
        String enc = UriUtils.encodePathSegment(connectionId, StandardCharsets.UTF_8);
        String getUrl = baseUrl + "/api/v2/connections/" + enc;
        boolean exists = resourceExistsV3(baseUrl, getUrl);
        String methodUrl = exists ? getUrl : baseUrl + "/api/v2/connections";
        HttpMethod method = exists ? HttpMethod.PATCH : HttpMethod.POST;
        log.info("Airflow 3+ connection {} {} ({})", method, connectionId, methodUrl);
        exchangeWithBearerAuth(baseUrl, methodUrl, method, body);
    }

    private boolean resourceExistsV2(String getUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(airflowApiUsername, airflowApiPassword);
        try {
            restTemplate.exchange(getUrl, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (HttpClientErrorException e) {
            throw new DeploymentException("Airflow connection check failed: HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new DeploymentException("Airflow connection check failed: " + e.getMessage(), e);
        }
    }

    private boolean resourceExistsV3(String baseUrl, String getUrl) {
        HttpHeaders headers = bearerHeaders(baseUrl);
        try {
            restTemplate.exchange(getUrl, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (HttpClientErrorException e) {
            throw new DeploymentException("Airflow connection check failed: HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new DeploymentException("Airflow connection check failed: " + e.getMessage(), e);
        }
    }

    private void exchangeWithBasicAuth(String url, HttpMethod method, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(airflowApiUsername, airflowApiPassword);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(url, method, request, new ParameterizedTypeReference<>() {});
            log.debug("Airflow connection sync response status: {}", response.getStatusCode());
        } catch (RestClientException e) {
            throw new DeploymentException("Airflow API error: " + e.getMessage(), e);
        }
    }

    private void exchangeWithBearerAuth(String baseUrl, String url, HttpMethod method, Map<String, Object> body) {
        HttpHeaders headers = bearerHeaders(baseUrl);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(url, method, request, new ParameterizedTypeReference<>() {});
            log.debug("Airflow connection sync response status: {}", response.getStatusCode());
        } catch (RestClientException e) {
            throw new DeploymentException("Airflow API error: " + e.getMessage(), e);
        }
    }

    private HttpHeaders bearerHeaders(String baseUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(obtainAirflowAccessToken(baseUrl));
        return headers;
    }

    private String obtainAirflowAccessToken(String baseUrl) {
        String tokenUrl = baseUrl + "/auth/token";
        Map<String, String> tokenBody = new HashMap<>();
        tokenBody.put("username", airflowApiUsername);
        tokenBody.put("password", airflowApiPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(tokenBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
        Map<String, Object> respBody = response.getBody();
        if (respBody == null || respBody.get("access_token") == null) {
            throw new DeploymentException("Airflow auth response missing access_token");
        }
        return respBody.get("access_token").toString();
    }

    /**
     * Authenticated GET returning parsed JSON object (Airflow stable REST). Used for DAG insights sync.
     *
     * @param pathAndQuery path starting with {@code /api/v1} or {@code /api/v2}, including query string if any
     */
    public Map<String, Object> getJson(String webserverUrl, String airflowVersion, String pathAndQuery) {
        String baseUrl = AirflowApiUrlUtils.normalizeAirflowBaseUrl(webserverUrl);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new DeploymentException("Airflow base URL is empty");
        }
        if (pathAndQuery == null || !pathAndQuery.startsWith("/")) {
            throw new DeploymentException("path must start with /");
        }
        String url = baseUrl + pathAndQuery;
        if (AirflowVersionUtils.isAirflow3OrLater(airflowVersion)) {
            HttpHeaders headers = bearerHeaders(baseUrl);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(airflowApiUsername, airflowApiPassword);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
        return response.getBody() != null ? response.getBody() : Collections.emptyMap();
    }
}
