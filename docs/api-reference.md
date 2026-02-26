---
layout: page
title: API Reference
nav_order: 6
---

# API Reference

Causa RCA provides a comprehensive REST API for triggering analyses, querying results, and integrating with external systems.

---

## Base URL

```
http://<causa-service>:9090
```

For local development with port forwarding:
```
http://localhost:9090
```

---

## Authentication

Currently, the API does not require authentication. For production deployments, consider:
- Using Kubernetes NetworkPolicies to restrict access
- Implementing an API gateway with authentication
- Using service mesh features (Istio, Linkerd)

---

## Analysis Endpoints

### Trigger Manual Analysis

Starts an asynchronous RCA analysis for a specific pod.

**Endpoint:** `GET /rca/analyze`

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `namespace` | string | No | `default` | Kubernetes namespace |
| `pod` | string | Yes | - | Pod name to analyze |

**Example Request:**

```bash
curl "http://localhost:9090/rca/analyze?namespace=production&pod=my-app-7d8f9c-xyz"
```

**Example Response:**

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "namespace": "production",
  "podName": "my-app-7d8f9c-xyz",
  "status": "IN_PROGRESS",
  "startTime": "2026-02-24T10:00:00Z",
  "message": "Analysis started"
}
```

**Response Codes:**

- `200 OK` - Analysis started successfully
- `400 Bad Request` - Missing or invalid pod parameter
- `500 Internal Server Error` - Analysis failed to start

---

### Webhook Endpoint

Receives Prometheus Alertmanager webhooks and triggers RCA analysis.

**Endpoint:** `POST /rca/webhook`

**Content-Type:** `application/json`

**Request Body:**

Standard Prometheus Alertmanager webhook payload. Alerts must include `namespace` and `pod` labels.

**Example Request:**

```bash
curl -X POST http://localhost:9090/rca/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "status": "firing",
    "alerts": [
      {
        "status": "firing",
        "labels": {
          "alertname": "PodMemoryHigh",
          "namespace": "production",
          "pod": "my-app-7d8f9c-xyz",
          "severity": "warning"
        },
        "annotations": {
          "summary": "Pod memory usage is high",
          "description": "Memory usage at 85%"
        },
        "startsAt": "2026-02-24T10:00:00Z"
      }
    ]
  }'
```

**Example Response:**

```json
{
  "message": "Webhook processed",
  "totalAlerts": 1,
  "processed": 1,
  "skipped": 0,
  "errors": 0,
  "results": [
    {
      "alert": "PodMemoryHigh",
      "namespace": "production",
      "pod": "my-app-7d8f9c-xyz",
      "status": "analyzed",
      "sessionId": "550e8400-e29b-41d4-a716-446655440000"
    }
  ]
}
```

**Response Codes:**

- `200 OK` - Webhook processed (even if some alerts skipped)
- `400 Bad Request` - Invalid webhook payload
- `500 Internal Server Error` - Processing failed

**Alert Requirements:**

Alerts must include these labels:
- `namespace` - Kubernetes namespace
- `pod` or `pod_name` - Pod name

Alternative label names also supported:
- `exported_namespace` (for namespace)
- `exported_pod` (for pod)

---


## Dashboard Endpoints

### View Dashboard

Web-based UI for viewing analyses.

**Endpoint:** `GET /dashboard`

**Example:**

```bash
# Open in browser
open http://localhost:9090/dashboard
```

---

### View Analysis Details

Detailed view of a specific analysis.

**Endpoint:** `GET /dashboard/analysis/{sessionId}`

**Example:**

```bash
open http://localhost:9090/dashboard/analysis/550e8400-e29b-41d4-a716-446655440000
```
---


[← Back to Getting Started](getting-started.html) | [Next: Configuration →](configuration.html)