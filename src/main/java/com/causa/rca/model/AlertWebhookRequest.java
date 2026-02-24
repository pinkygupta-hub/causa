package com.causa.rca.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Model representing an incoming webhook request from Prometheus Alertmanager.
 * <p>
 * This model captures the essential information from Prometheus alerts to trigger
 * RCA analysis for specific pods and namespaces. It follows the Alertmanager webhook
 * format and extracts the necessary labels to identify the target workload.
 * </p>
 * <p>
 * Example Prometheus alert labels expected:
 * <ul>
 *   <li>namespace: The Kubernetes namespace</li>
 *   <li>pod: The pod name (or pod_name)</li>
 *   <li>alertname: The name of the alert</li>
 * </ul>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertWebhookRequest {

    /**
     * List of alerts in the webhook payload.
     * Alertmanager can send multiple alerts in a single webhook call.
     */
    @JsonProperty("alerts")
    private List<Alert> alerts;

    /**
     * Status of the alert group (firing, resolved, etc.)
     */
    @JsonProperty("status")
    private String status;

    /**
     * Common labels shared across all alerts in this group
     */
    @JsonProperty("commonLabels")
    private Map<String, String> commonLabels;

    /**
     * Group labels used for alert grouping
     */
    @JsonProperty("groupLabels")
    private Map<String, String> groupLabels;

    public AlertWebhookRequest() {
    }

    public List<Alert> getAlerts() {
        return alerts;
    }

    public void setAlerts(List<Alert> alerts) {
        this.alerts = alerts;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, String> getCommonLabels() {
        return commonLabels;
    }

    public void setCommonLabels(Map<String, String> commonLabels) {
        this.commonLabels = commonLabels;
    }

    public Map<String, String> getGroupLabels() {
        return groupLabels;
    }

    public void setGroupLabels(Map<String, String> groupLabels) {
        this.groupLabels = groupLabels;
    }

    /**
     * Represents an individual alert within the webhook payload.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Alert {
        /**
         * Current status of the alert (firing, resolved)
         */
        @JsonProperty("status")
        private String status;

        /**
         * Labels associated with this specific alert.
         * Should contain namespace, pod/pod_name, and other identifying information.
         */
        @JsonProperty("labels")
        private Map<String, String> labels;

        /**
         * Annotations providing additional context about the alert
         */
        @JsonProperty("annotations")
        private Map<String, String> annotations;

        /**
         * Timestamp when the alert started firing
         */
        @JsonProperty("startsAt")
        private String startsAt;

        /**
         * Timestamp when the alert was resolved (if applicable)
         */
        @JsonProperty("endsAt")
        private String endsAt;

        public Alert() {
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }

        public Map<String, String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(Map<String, String> annotations) {
            this.annotations = annotations;
        }

        public String getStartsAt() {
            return startsAt;
        }

        public void setStartsAt(String startsAt) {
            this.startsAt = startsAt;
        }

        public String getEndsAt() {
            return endsAt;
        }

        public void setEndsAt(String endsAt) {
            this.endsAt = endsAt;
        }

        /**
         * Extracts the namespace from alert labels.
         * Tries common label names used in Kubernetes monitoring.
         *
         * @return the namespace, or null if not found
         */
        public String getNamespace() {
            if (labels == null) {
                return null;
            }
            return labels.getOrDefault("namespace", labels.get("exported_namespace"));
        }

        /**
         * Extracts the pod name from alert labels.
         * Tries common label names used in Kubernetes monitoring.
         *
         * @return the pod name, or null if not found
         */
        public String getPodName() {
            if (labels == null) {
                return null;
            }
            // Try different common label names for pod
            String pod = labels.get("pod");
            if (pod == null) {
                pod = labels.get("pod_name");
            }
            if (pod == null) {
                pod = labels.get("exported_pod");
            }
            return pod;
        }

        /**
         * Gets the alert name from labels.
         *
         * @return the alert name, or null if not found
         */
        public String getAlertName() {
            if (labels == null) {
                return null;
            }
            return labels.get("alertname");
        }
    }
}

// Made with Bob
