package com.causa.rca.utils;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for parsing and validating Kubernetes label selectors.
 * <p>
 * This utility provides methods to parse label selector strings in the format
 * "key=value" and extract individual components. It handles various edge cases
 * and provides validation to ensure label selectors are properly formatted.
 * </p>
 * <p>
 * Label selectors are used throughout the RCA system to identify pods for analysis.
 * This utility ensures consistent parsing and validation across all services.
 * </p>
 *
 * @see ScannerService
 */
@ApplicationScoped
public class LabelParser {

    private static final Logger LOG = Logger.getLogger(LabelParser.class);

    /**
     * Parses a label selector string into key and value components.
     * <p>
     * Accepts label selectors in the format "key=value". If no value is provided
     * (e.g., "key"), an empty string is used as the value.
     * </p>
     * <p>
     * Examples:
     * <ul>
     *   <li>"app=myapp" → {key: "app", value: "myapp"}</li>
     *   <li>"kruize/rca=enabled" → {key: "kruize/rca", value: "enabled"}</li>
     *   <li>"monitoring" → {key: "monitoring", value: ""}</li>
     * </ul>
     * </p>
     *
     * @param labelSelector the label selector string to parse
     * @return a Map containing "key" and "value" entries
     * @throws IllegalArgumentException if the label selector is null or empty
     */
    public Map<String, String> parse(String labelSelector) {
        if (labelSelector == null || labelSelector.trim().isEmpty()) {
            throw new IllegalArgumentException("Label selector cannot be null or empty");
        }

        String trimmed = labelSelector.trim();
        String[] parts = trimmed.split("=", 2);
        
        Map<String, String> result = new HashMap<>();
        result.put("key", parts[0].trim());
        result.put("value", parts.length > 1 ? parts[1].trim() : "");
        
        LOG.debug("Parsed label selector '" + labelSelector + "' into key='" + 
                 result.get("key") + "', value='" + result.get("value") + "'");
        
        return result;
    }

    /**
     * Extracts the key from a label selector string.
     * <p>
     * Convenience method that returns only the key portion of the label selector.
     * </p>
     *
     * @param labelSelector the label selector string
     * @return the key portion of the label selector
     * @throws IllegalArgumentException if the label selector is null or empty
     */
    public String extractKey(String labelSelector) {
        return parse(labelSelector).get("key");
    }

    /**
     * Extracts the value from a label selector string.
     * <p>
     * Convenience method that returns only the value portion of the label selector.
     * Returns an empty string if no value is specified.
     * </p>
     *
     * @param labelSelector the label selector string
     * @return the value portion of the label selector, or empty string if not specified
     * @throws IllegalArgumentException if the label selector is null or empty
     */
    public String extractValue(String labelSelector) {
        return parse(labelSelector).get("value");
    }

    /**
     * Validates a label selector string.
     * <p>
     * Checks if the label selector is properly formatted and contains valid
     * Kubernetes label characters. A valid label selector must:
     * <ul>
     *   <li>Not be null or empty</li>
     *   <li>Have a non-empty key</li>
     *   <li>Contain only valid Kubernetes label characters</li>
     * </ul>
     * </p>
     *
     * @param labelSelector the label selector string to validate
     * @return true if the label selector is valid, false otherwise
     */
    public boolean isValid(String labelSelector) {
        if (labelSelector == null || labelSelector.trim().isEmpty()) {
            return false;
        }

        try {
            Map<String, String> parsed = parse(labelSelector);
            String key = parsed.get("key");
            
            // Key must not be empty
            if (key.isEmpty()) {
                return false;
            }

            // Basic validation: key should contain valid characters
            // Kubernetes labels allow alphanumeric, '-', '_', '.', and '/'
            if (!key.matches("^[a-zA-Z0-9/_.-]+$")) {
                LOG.warn("Invalid label key format: " + key);
                return false;
            }

            return true;
        } catch (Exception e) {
            LOG.warn("Error validating label selector: " + labelSelector, e);
            return false;
        }
    }

    /**
     * Formats a label selector from separate key and value components.
     * <p>
     * Creates a properly formatted label selector string from individual components.
     * If the value is null or empty, returns just the key.
     * </p>
     *
     * @param key the label key
     * @param value the label value (can be null or empty)
     * @return a formatted label selector string
     * @throws IllegalArgumentException if the key is null or empty
     */
    public String format(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Label key cannot be null or empty");
        }

        String trimmedKey = key.trim();
        
        if (value == null || value.trim().isEmpty()) {
            return trimmedKey;
        }

        return trimmedKey + "=" + value.trim();
    }

    /**
     * Parses multiple label selectors from a comma-separated string.
     * <p>
     * Useful for parsing multiple label requirements. Each label selector
     * is parsed individually and returned in a list.
     * </p>
     * <p>
     * Example: "app=myapp,env=prod" → [{key:"app", value:"myapp"}, {key:"env", value:"prod"}]
     * </p>
     *
     * @param labelSelectors comma-separated label selectors
     * @return a Map where each entry represents a parsed label selector
     * @throws IllegalArgumentException if any label selector is invalid
     */
    public Map<String, String> parseMultiple(String labelSelectors) {
        if (labelSelectors == null || labelSelectors.trim().isEmpty()) {
            throw new IllegalArgumentException("Label selectors cannot be null or empty");
        }

        Map<String, String> result = new HashMap<>();
        String[] selectors = labelSelectors.split(",");
        
        for (String selector : selectors) {
            Map<String, String> parsed = parse(selector.trim());
            result.put(parsed.get("key"), parsed.get("value"));
        }
        
        return result;
    }
}

