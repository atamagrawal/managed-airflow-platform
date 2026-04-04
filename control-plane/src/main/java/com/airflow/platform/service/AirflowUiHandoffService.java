package com.airflow.platform.service;

import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.security.SecurityUtils;
import com.airflow.platform.util.AirflowVersionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time browser handoff: GET serves a page that POSTs same-origin to {@code .../complete}; the response returns
 * Airflow credentials once. The page then submits a hidden HTML form (POST) to Airflow
 * {@code /auth/managed-platform-ui-handoff} so the browser performs a top-level navigation — unlike {@code fetch()},
 * that is not subject to CORS and avoids Safari “Load failed” on cross-origin credentialed requests. The Airflow side
 * (see {@code airflow_local_settings.py}) validates credentials and redirects to {@code /} with the {@code _token}
 * cookie. Browser base URL uses {@code localhost} instead of {@code 127.0.0.1} where applicable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AirflowUiHandoffService {

    private static final String HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="utf-8"/><title>Airflow sign-in</title></head>
            <body>
            <p id="m">Signing you in to Airflow…</p>
            <script>
            try { if (window.opener) { window.opener = null; } } catch (e) {}
            const cfg = HANDOFF_JSON;
            (async () => {
              const m = document.getElementById('m');
              if (!cfg.airflow3) {
                m.textContent = 'Automatic sign-in is only set up for Airflow 3.x. Open Airflow and sign in with the same username and password as this console.';
                return;
              }
              try {
                const completePath = window.location.pathname.replace(/\\/+$/, '') + '/complete';
                const r = await fetch(completePath, {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  credentials: 'include'
                });
                if (!r.ok) {
                  let detail = '';
                  try { detail = await r.text(); } catch (e2) {}
                  m.textContent = 'Could not sign in automatically (HTTP ' + r.status + '). ' + (detail ? detail.slice(0, 200) : 'Open the webserver URL in a new tab and sign in manually.');
                  return;
                }
                const creds = await r.json();
                const base = String(creds.browserBaseUrl || '').replace(/\\/$/, '');
                if (!base || !creds.username) {
                  m.textContent = 'Unexpected response from the console. Try Open Airflow again or sign in manually.';
                  return;
                }
                if (globalThis.location.protocol === 'https:' && base.startsWith('http:')) {
                  m.textContent = 'This console is HTTPS but Airflow is HTTP; the browser blocks submitting the sign-in form (mixed content). Use HTTP for local dev, put Airflow behind HTTPS, or open the webserver URL and sign in manually.';
                  return;
                }
                const action = base + '/auth/managed-platform-ui-handoff';
                const form = document.createElement('form');
                form.method = 'POST';
                form.action = action;
                form.enctype = 'application/x-www-form-urlencoded';
                const u = document.createElement('input');
                u.type = 'hidden';
                u.name = 'username';
                u.value = creds.username;
                const p = document.createElement('input');
                p.type = 'hidden';
                p.name = 'password';
                p.value = creds.password;
                form.appendChild(u);
                form.appendChild(p);
                document.body.appendChild(form);
                m.textContent = 'Redirecting to Airflow…';
                form.submit();
              } catch (e) {
                console.error('Airflow handoff failed', e);
                m.textContent = 'Could not start sign-in (' + (e?.message || 'error') + '). Restart the control plane after upgrading, run a project sync so config/airflow_local_settings.py updates, wait for apiserver restart, then retry. Or sign in manually at the webserver URL.';
              }
            })();
            </script>
            </body>
            </html>
            """;

    private final AirflowDeploymentRepository deploymentRepository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, StoredHandoff> tickets = new ConcurrentHashMap<>();

    private record StoredHandoff(String username, String password, String browserBaseUrl, String airflowVersion,
                                  Instant expiresAt) {
    }

    public String createTicket(String deploymentId) {
        String username = SecurityUtils.getCurrentUsername()
                .orElseThrow(() -> new IllegalArgumentException("Not authenticated"));
        String password = authService.resolveAirflowHandoffPassword(username);

        AirflowDeployment d = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        SecurityUtils.assertTenantInScope(d.getTenant().getTenantId());

        String webserverUrl = d.getWebserverUrl();
        if (!StringUtils.hasText(webserverUrl)) {
            throw new IllegalArgumentException("Deployment has no webserver URL");
        }
        String browserBase = browserBaseUrlForHandoff(webserverUrl.trim());
        String id = UUID.randomUUID().toString();
        tickets.put(id, new StoredHandoff(username, password, browserBase,
                d.getAirflowVersion(), Instant.now().plus(2, ChronoUnit.MINUTES)));
        log.info("Created Airflow UI handoff ticket for user {} deployment {}", username, deploymentId);
        return id;
    }

    public String buildHandoffPageHtml(String ticketId) {
        StoredHandoff t = requireValidTicket(ticketId);
        try {
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("airflow3", AirflowVersionUtils.isAirflow3OrLater(t.airflowVersion));
            String json = objectMapper.writeValueAsString(cfg);
            return HTML.replace("HANDOFF_JSON", json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build handoff page", e);
        }
    }

    /**
     * Consumes the ticket and returns a one-time JSON payload so the handoff page can POST credentials to Airflow
     * {@code /auth/managed-platform-ui-handoff} (form navigation, same effective origin as the UI).
     */
    public ResponseEntity<String> completeHandoff(String ticketId) {
        StoredHandoff t = tickets.remove(ticketId);
        if (t == null) {
            throw new ResourceNotFoundException("Handoff link expired or already used");
        }
        if (t.expiresAt.isBefore(Instant.now())) {
            throw new ResourceNotFoundException("Handoff link expired");
        }
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("browserBaseUrl", t.browserBaseUrl);
            payload.put("username", t.username);
            payload.put("password", t.password);
            String body = objectMapper.writeValueAsString(payload);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize handoff payload", e);
        }
    }

    /**
     * URL the user's browser should use for Airflow UI and {@code /auth/token} (not the JVM-normalized loopback form).
     */
    static String browserBaseUrlForHandoff(String webserverUrl) {
        String trimmed = webserverUrl.endsWith("/")
                ? webserverUrl.substring(0, webserverUrl.length() - 1)
                : webserverUrl;
        return trimmed.replace("://127.0.0.1", "://localhost");
    }

    private StoredHandoff requireValidTicket(String ticketId) {
        StoredHandoff t = tickets.get(ticketId);
        if (t == null) {
            throw new ResourceNotFoundException("Handoff link expired or already used");
        }
        if (t.expiresAt.isBefore(Instant.now())) {
            tickets.remove(ticketId);
            throw new ResourceNotFoundException("Handoff link expired");
        }
        return t;
    }
}
