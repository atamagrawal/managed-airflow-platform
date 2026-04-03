package com.airflow.platform.util;

/**
 * Normalizes URLs used to call Airflow HTTP APIs from the control plane.
 */
public final class AirflowApiUrlUtils {

    private AirflowApiUrlUtils() {
    }

    /**
     * Strips a trailing slash and rewrites {@code localhost} to {@code 127.0.0.1}.
     * <p>
     * The JVM often resolves {@code localhost} to IPv6 ({@code ::1}), while Docker Desktop typically
     * publishes host ports on IPv4 only. Clients then hit {@code ::1:port} with nothing listening,
     * which commonly surfaces as "Connection reset" on POSTs such as {@code /auth/token}.
     */
    public static String normalizeAirflowBaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String trimmed = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return trimmed.replaceFirst("(?i)://localhost(?=:|/|\\?|#|$)", "://127.0.0.1");
    }
}
