package com.causa.rca.utils;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for validating application configuration.
 * <p>
 * This utility provides methods to validate configuration properties and ensure
 * the application is properly configured before starting. It helps catch
 * configuration errors early and provides clear error messages.
 * </p>
 * <p>
 * Validation includes:
 * <ul>
 *   <li>RCA mode validation (MONITORING or ALERT_DRIVEN)</li>
 *   <li>Label selector format validation</li>
 *   <li>URL format validation for external services</li>
 *   <li>Required property presence checks</li>
 * </ul>
 * </p>
 *
 * @see StartupService
 */
@ApplicationScoped
public class ConfigurationValidator {

    private static final Logger LOG = Logger.getLogger(ConfigurationValidator.class);

    @ConfigProperty(name = "rca.mode", defaultValue = "MONITORING")
    String rcaMode;

    @ConfigProperty(name = "rca.label")
    String rcaLabel;

    @ConfigProperty(name = "rca.scan.interval", defaultValue = "5m")
    String scanInterval;

    @ConfigProperty(name = "prometheus.url")
    String prometheusUrl;

    @ConfigProperty(name = "cryostat.url")
    String cryostatUrl;

    @ConfigProperty(name = "cryostat.enabled", defaultValue = "false")
    boolean cryostatEnabled;

    @ConfigProperty(name = "rag.enabled", defaultValue = "true")
    boolean ragEnabled;

    /**
     * Validates all critical configuration properties.
     * <p>
     * Performs comprehensive validation of all configuration properties and
     * returns a list of validation errors. If the list is empty, the configuration
     * is valid.
     * </p>
     *
     * @return a list of validation error messages (empty if configuration is valid)
     */
    public List<String> validateConfiguration() {
        List<String> errors = new ArrayList<>();

        // Validate RCA mode
        if (!isValidRcaMode(rcaMode)) {
            errors.add("Invalid rca.mode: '" + rcaMode + "'. Must be MONITORING or ALERT_DRIVEN");
        }

        // Validate label selector
        if (rcaLabel == null || rcaLabel.trim().isEmpty()) {
            errors.add("rca.label is required but not configured");
        } else if (!isValidLabelFormat(rcaLabel)) {
            errors.add("Invalid rca.label format: '" + rcaLabel + "'. Expected format: key=value");
        }

        // Validate scan interval format
        if (!isValidDurationFormat(scanInterval)) {
            errors.add("Invalid rca.scan.interval format: '" + scanInterval + "'. Expected format: 5m, 1h, etc.");
        }

        // Validate Prometheus URL
        if (prometheusUrl == null || prometheusUrl.trim().isEmpty()) {
            errors.add("prometheus.url is required but not configured");
        } else if (!isValidUrl(prometheusUrl)) {
            errors.add("Invalid prometheus.url format: '" + prometheusUrl + "'");
        }

        // Validate Cryostat configuration if enabled
        if (cryostatEnabled) {
            if (cryostatUrl == null || cryostatUrl.trim().isEmpty()) {
                errors.add("cryostat.url is required when cryostat.enabled=true");
            } else if (!isValidUrl(cryostatUrl)) {
                errors.add("Invalid cryostat.url format: '" + cryostatUrl + "'");
            }
        }

        return errors;
    }

    /**
     * Validates the RCA mode configuration.
     * <p>
     * Checks if the mode is one of the supported values: MONITORING or ALERT_DRIVEN.
     * </p>
     *
     * @param mode the RCA mode to validate
     * @return true if the mode is valid, false otherwise
     */
    public boolean isValidRcaMode(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return false;
        }
        String normalized = mode.trim().toUpperCase();
        return "MONITORING".equals(normalized) || "ALERT_DRIVEN".equals(normalized);
    }

    /**
     * Validates a label selector format.
     * <p>
     * Checks if the label selector follows the expected "key=value" format
     * and contains valid Kubernetes label characters.
     * </p>
     *
     * @param label the label selector to validate
     * @return true if the label format is valid, false otherwise
     */
    public boolean isValidLabelFormat(String label) {
        if (label == null || label.trim().isEmpty()) {
            return false;
        }

        // Basic format check: should contain at least a key
        String[] parts = label.split("=");
        if (parts.length == 0 || parts[0].trim().isEmpty()) {
            return false;
        }

        // Validate key format (alphanumeric, '-', '_', '.', '/')
        String key = parts[0].trim();
        return key.matches("^[a-zA-Z0-9/_.-]+$");
    }

    /**
     * Validates a duration format string.
     * <p>
     * Checks if the duration follows Quarkus duration format (e.g., "5m", "1h", "30s").
     * </p>
     *
     * @param duration the duration string to validate
     * @return true if the duration format is valid, false otherwise
     */
    public boolean isValidDurationFormat(String duration) {
        if (duration == null || duration.trim().isEmpty()) {
            return false;
        }

        // Quarkus duration format: number followed by unit (s, m, h, d)
        return duration.matches("^\\d+[smhd]$");
    }

    /**
     * Validates a URL format.
     * <p>
     * Checks if the URL is properly formatted and uses http or https protocol.
     * </p>
     *
     * @param url the URL to validate
     * @return true if the URL format is valid, false otherwise
     */
    public boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        String trimmed = url.trim();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    /**
     * Logs all configuration validation errors.
     * <p>
     * Convenience method that validates configuration and logs any errors found.
     * Returns true if configuration is valid, false otherwise.
     * </p>
     *
     * @return true if configuration is valid, false if errors were found
     */
    public boolean validateAndLog() {
        List<String> errors = validateConfiguration();
        
        if (errors.isEmpty()) {
            LOG.info("✓ Configuration validation passed");
            return true;
        }

        LOG.error("✗ Configuration validation failed with " + errors.size() + " error(s):");
        for (String error : errors) {
            LOG.error("  - " + error);
        }
        
        return false;
    }

    /**
     * Gets the current RCA mode.
     *
     * @return the configured RCA mode
     */
    public String getRcaMode() {
        return rcaMode;
    }

    /**
     * Gets the configured label selector.
     *
     * @return the configured label selector
     */
    public String getRcaLabel() {
        return rcaLabel;
    }

    /**
     * Checks if the configuration is valid for MONITORING mode.
     * <p>
     * MONITORING mode requires:
     * <ul>
     *   <li>Valid label selector</li>
     *   <li>Valid scan interval</li>
     *   <li>Prometheus URL</li>
     * </ul>
     * </p>
     *
     * @return true if configuration is valid for MONITORING mode
     */
    public boolean isValidForMonitoringMode() {
        return isValidLabelFormat(rcaLabel) &&
               isValidDurationFormat(scanInterval) &&
               isValidUrl(prometheusUrl);
    }

    /**
     * Checks if the configuration is valid for ALERT_DRIVEN mode.
     * <p>
     * ALERT_DRIVEN mode requires:
     * <ul>
     *   <li>Prometheus URL (for data collection)</li>
     *   <li>Valid RCA mode setting</li>
     * </ul>
     * </p>
     *
     * @return true if configuration is valid for ALERT_DRIVEN mode
     */
    public boolean isValidForAlertDrivenMode() {
        return isValidUrl(prometheusUrl) &&
               "ALERT_DRIVEN".equalsIgnoreCase(rcaMode);
    }

    /**
     * Provides configuration recommendations based on current settings.
     * <p>
     * Analyzes the configuration and suggests improvements or highlights
     * potential issues.
     * </p>
     *
     * @return a list of configuration recommendations
     */
    public List<String> getRecommendations() {
        List<String> recommendations = new ArrayList<>();

        // Check if Cryostat is disabled
        if (!cryostatEnabled) {
            recommendations.add("Consider enabling Cryostat for JFR analysis (cryostat.enabled=true)");
        }

        // Check if RAG is disabled
        if (!ragEnabled) {
            recommendations.add("Consider enabling RAG for enhanced AI analysis (rag.enabled=true)");
        }

        // Check scan interval for MONITORING mode
        if ("MONITORING".equalsIgnoreCase(rcaMode)) {
            if (scanInterval.matches("^[1-2]m$")) {
                recommendations.add("Scan interval is very short (" + scanInterval + "). Consider increasing to reduce load");
            }
        }

        return recommendations;
    }
}

// Made with Bob
