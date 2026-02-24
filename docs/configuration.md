---
layout: page
title: Configuration
nav_order: 7
---

# Configuration

Causa RCA is fully configurable via environment variables, making it easy to deploy across different environments without code changes.

---

## Quick Reference

All configuration is done through environment variables. Set them in your Kubernetes deployment, Docker run command, or local `.env` file.

### Essential Configuration

```bash
# Operation Mode
RCA_MODE=ALERT_DRIVEN              # or MONITORING

# Service URLs
OLLAMA_BASE_URL=http://ollama:11434
PROMETHEUS_URL=http://prometheus:9090
MONGODB_CONNECTION_STRING=mongodb://mongodb:27017

# Credentials
MONGODB_USER=admin
MONGODB_PASSWORD=secret
```

---

## Complete Environment Variables Reference

### Application Settings

#### HTTP Server

| Variable | Default | Description |
|----------|---------|-------------|
| `QUARKUS_HTTP_PORT` | `9090` | HTTP port for the application |

**Example:**
```bash
QUARKUS_HTTP_PORT=8080
```

---

### Ollama Configuration

#### Base Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_BASE_URL` | `http://localhost:9034` | Ollama service URL |
| `OLLAMA_DEVSERVICES_ENABLED` | `false` | Enable Quarkus dev services |
| `OLLAMA_TIMEOUT` | `300s` | Global timeout for requests |

**Cluster Example:**
```bash
OLLAMA_BASE_URL=http://ollama.default.svc.cluster.local:11434
OLLAMA_TIMEOUT=300s
```

#### AI Model Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_DETECTOR_MODEL` | `qwen2.5:3b-instruct` | Anomaly detection model |
| `OLLAMA_DETECTOR_TIMEOUT` | `300s` | Detector timeout |
| `OLLAMA_RCA_MODEL` | `qwen2.5:3b-instruct` | Root cause analysis model |
| `OLLAMA_RCA_TIMEOUT` | `300s` | RCA timeout |
| `OLLAMA_VALIDATOR_MODEL` | `qwen2.5:3b-instruct` | Validation model |
| `OLLAMA_VALIDATOR_TIMEOUT` | `300s` | Validator timeout |
| `OLLAMA_EMBEDDING_MODEL` | `qwen2.5:3b-instruct` | Embedding model for RAG |

**Example with Different Models:**
```bash
OLLAMA_DETECTOR_MODEL=llama2:7b
OLLAMA_RCA_MODEL=mixtral:8x7b
OLLAMA_VALIDATOR_MODEL=qwen2.5:3b-instruct
```

---

### External Services

#### Prometheus

| Variable | Default | Description |
|----------|---------|-------------|
| `PROMETHEUS_URL` | `http://localhost:9035` | Prometheus server URL |

**Examples:**
```bash
# Local development
PROMETHEUS_URL=http://localhost:9035

# Kubernetes cluster
PROMETHEUS_URL=http://prometheus-k8s.monitoring.svc.cluster.local:9090

# External Prometheus
PROMETHEUS_URL=https://prometheus.example.com
```

#### Cryostat (Optional)

| Variable | Default | Description |
|----------|---------|-------------|
| `CRYOSTAT_ENABLED` | `false` | Enable JFR profiling via Cryostat |
| `CRYOSTAT_URL` | `https://cryostat.openshift-operators.svc.cluster.local:4180` | Cryostat server URL |

**Example:**
```bash
CRYOSTAT_ENABLED=true
CRYOSTAT_URL=https://cryostat.my-namespace.svc.cluster.local:4180
```

---

### TLS Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `QUARKUS_TLS_TRUST_ALL` | `true` | Trust all TLS certificates |

**Production Recommendation:**
```bash
# Use proper certificates in production
QUARKUS_TLS_TRUST_ALL=false
```

---

### RAG (Retrieval-Augmented Generation)

| Variable | Default | Description |
|----------|---------|-------------|
| `RAG_ENABLED` | `false` | Enable RAG with knowledge base |
| `RAG_KNOWLEDGE_BASE_PATH` | `knowledge_base/` | Path to knowledge base files |

**Example:**
```bash
RAG_ENABLED=true
RAG_KNOWLEDGE_BASE_PATH=/app/knowledge_base/
```

---

### RCA Operation

#### Mode Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `RCA_MODE` | `ALERT_DRIVEN` | Operation mode: `MONITORING` or `ALERT_DRIVEN` |

**Examples:**
```bash
# For development/testing
RCA_MODE=MONITORING

# For production
RCA_MODE=ALERT_DRIVEN
```

#### Scanning Configuration (MONITORING Mode)

| Variable | Default | Description |
|----------|---------|-------------|
| `RCA_SCAN_INTERVAL` | `5m` | Scan interval (e.g., 1m, 5m, 10m, 1h) |
| `RCA_LABEL` | `kruize/rca=enabled` | Label selector for pods to monitor |

**Examples:**
```bash
# Scan every 10 minutes
RCA_SCAN_INTERVAL=10m

# Custom label
RCA_LABEL=monitoring/rca=true

# Multiple labels (comma-separated)
RCA_LABEL=tier=critical,env=prod
```
---

### MongoDB Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `MONGODB_CONNECTION_STRING` | `mongodb://localhost:9017?retryWrites=false` | MongoDB connection string |
| `MONGODB_DATABASE` | `causa_rca` | Database name |
| `MONGODB_USER` | `admin` | Username |
| `MONGODB_PASSWORD` | `password` | Password |
| `MONGODB_AUTH_SOURCE` | `admin` | Authentication database |

**Examples:**

```bash
# Local development
MONGODB_CONNECTION_STRING=mongodb://localhost:9017?retryWrites=false
MONGODB_USER=admin
MONGODB_PASSWORD=password

# Kubernetes cluster
MONGODB_CONNECTION_STRING=mongodb://mongodb.default.svc.cluster.local:27017
MONGODB_USER=causa_user
MONGODB_PASSWORD=secure_password

# MongoDB Atlas
MONGODB_CONNECTION_STRING=mongodb+srv://cluster.mongodb.net/?retryWrites=true&w=majority
MONGODB_USER=atlas_user
MONGODB_PASSWORD=atlas_password
```

---

### Dashboard Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `RCA_DASHBOARD_SHOW_PROPOSED_SOLUTION` | `false` | Show/hide proposed solutions |

**Example:**
```bash
RCA_DASHBOARD_SHOW_PROPOSED_SOLUTION=true
```

---

### Cleanup Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `RCA_CLEANUP_ENABLED` | `true` | Enable automatic cleanup |
| `RCA_CLEANUP_RETENTION_DAYS` | `30` | Days to retain data |
| `RCA_CLEANUP_SCHEDULE` | `0 0 2 * * ?` | Cron expression for cleanup |

**Examples:**

```bash
# Disable cleanup
RCA_CLEANUP_ENABLED=false

# Keep data for 90 days
RCA_CLEANUP_RETENTION_DAYS=90

# Run cleanup at 3 AM daily
RCA_CLEANUP_SCHEDULE=0 0 3 * * ?

# Run cleanup weekly (Sunday at 2 AM)
RCA_CLEANUP_SCHEDULE=0 0 2 ? * SUN
```

**Cron Format:** `second minute hour day month weekday`

---

[← Back to API Reference](api-reference.html) | [Home](index.html)