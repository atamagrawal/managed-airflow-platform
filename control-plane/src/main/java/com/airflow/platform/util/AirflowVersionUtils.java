package com.airflow.platform.util;

/**
 * Parses deployment {@code airflowVersion} strings for API compatibility (e.g. 2.x vs 3+).
 */
public final class AirflowVersionUtils {

    private AirflowVersionUtils() {
    }

    /**
     * @return true when the major version is 3 or higher, or when the version cannot be parsed (prefer newer API).
     */
    public static boolean isAirflow3OrLater(String airflowVersion) {
        if (airflowVersion == null || airflowVersion.isBlank()) {
            return true;
        }
        int end = 0;
        while (end < airflowVersion.length() && Character.isDigit(airflowVersion.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return true;
        }
        try {
            return Integer.parseInt(airflowVersion.substring(0, end)) >= 3;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
