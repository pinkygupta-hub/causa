---
layout: page
title: Features
nav_order: 3
---

# Features

Causa RCA provides comprehensive root cause analysis capabilities for Kubernetes workloads through AI-powered automation and intelligent data collection.

---

## 🤖 AI-Powered Analysis

### Multi-Agent System

Causa uses three specialized AI agents that work together to provide accurate, actionable insights:

**1. Anomaly Detector**
- Analyzes metrics, logs, and events
- Identifies issue patterns and signatures
- Classifies anomaly types (OOM, CPU throttling, crashes, etc.)
- Fast initial triage

**2. Root Cause Analyst**
- Performs deep reasoning on collected data
- Enhanced with RAG (Retrieval-Augmented Generation)
- Accesses knowledge base of runbooks and best practices
- Generates detailed analysis and fix recommendations

**3. Validation Agent**
- Critiques the proposed analysis
- Validates recommendations for accuracy
- Formats output into structured JSON
- Ensures consistency and quality

---

## 🎯 Dual Operation Modes

### MONITORING Mode

**Proactive Continuous Monitoring**

- Scheduled scanning of labeled workloads
- Configurable scan intervals
- Label-based pod selection
- Ideal for development and testing environments
- Continuous health checks

**Configuration:**
```bash
RCA_MODE=MONITORING
RCA_SCAN_INTERVAL=5m
RCA_LABEL=kruize/rca=enabled
```

**Use Cases:**
- Development environment monitoring
- Continuous integration testing
- Baseline performance tracking
- Proactive issue detection

### ALERT_DRIVEN Mode

**Reactive Event-Based Analysis**

- Triggered by Prometheus/Alertmanager webhooks
- On-demand analysis only when needed
- Minimal resource usage when idle
- Ideal for production environments
- Integrates with existing alerting

**Configuration:**
```bash
RCA_MODE=ALERT_DRIVEN
```

**Use Cases:**
- Production incident response
- Cost-sensitive deployments
- Integration with existing monitoring
- Reactive troubleshooting

[Learn more about modes →](modes.html)

---

## 📊 Comprehensive Data Collection

### Prometheus Metrics

- **CPU Usage** - Current and historical CPU consumption
- **Memory Usage** - Working set, limits, and trends

### Kubernetes Data

- **Pod Status** - Current state and conditions
- **Events** - Recent Kubernetes events
- **Container Logs** - Current and previous container logs
- **Resource Limits** - Configured requests and limits

---

## 💾 Data Management

### MongoDB Storage

- **Analysis History** - Complete analysis records

### Data Retention

- **Configurable Retention** - Set retention period (default: 30 days)
- **Automatic Cleanup** - Scheduled old data removal

**Configuration:**
```bash
RCA_CLEANUP_ENABLED=true
RCA_CLEANUP_RETENTION_DAYS=30
RCA_CLEANUP_SCHEDULE=0 0 2 * * ?
```

---

## 🖥️ Web Dashboard

### Analysis Visualization

- **List View** - All analyses with key information
- **Detail View** - Complete analysis reports
- **Status Indicators** - Visual status representation
- **Timestamp Tracking** - Creation and update times

---

## 🔧 Configuration Flexibility

### Environment Variables

All configuration via environment variables:
- **Service URLs** - Prometheus, Ollama, MongoDB, Cryostat
- **AI Models** - Configurable model selection per agent
- **Timeouts** - Adjustable request timeouts


[View all configuration options →](configuration.html)

---

[← Back to Home](index.html) | [Next: Operation Modes →](modes.html)