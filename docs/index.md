---
layout: home
title: Causa - AI-Powered Root Cause Analysis Agent
nav_order: 1
---

# Causa RCA

**AI-Powered Root Cause Analysis for Kubernetes**

Causa is an intelligent system that automatically detects, analyzes, and diagnoses issues in your Kubernetes workloads using advanced AI models. Get instant insights into performance problems, memory leaks, and application failures.

---

## 🚀 Quick Start

Get started with Causa in minutes using our automated demo:

```bash
git clone https://github.com/causaai/causa-demos
cd causa-demos
./kind/demo.sh
```

This script automatically sets up a complete Causa environment with:
- Local Kubernetes cluster (Kind)
- Prometheus monitoring
- Ollama AI models
- Sample applications
- Pre-configured alerts

[View Demo Repository →](https://github.com/causaai/causa-demos)

---

## ✨ Key Features

### 🤖 Multi-Agent AI System
Three specialized AI agents work together to provide comprehensive analysis:
- **Anomaly Detector** - Identifies issues from metrics and logs
- **Root Cause Analyst** - Performs deep reasoning with RAG-enhanced knowledge
- **Validator** - Critiques findings and ensures accuracy

### 🎯 Dual Operation Modes
- **MONITORING** - Proactive scheduled scanning of labeled workloads
- **ALERT_DRIVEN** - Reactive analysis triggered by Prometheus alerts

### 📊 Comprehensive Data Collection
- Prometheus metrics (CPU, memory, network)
- Kubernetes events and pod status
- Container logs (current and previous)
- JFR profiling data via Cryostat (optional)


---

## 🎓 How It Works

1. **Detection** - Causa monitors your workloads either continuously (MONITORING mode) or on-demand (ALERT_DRIVEN mode)

2. **Data Collection** - When an issue is detected, Causa gathers:
   - Recent metrics from Prometheus
   - Kubernetes events and pod status
   - Application logs
   - JFR profiling data (if enabled)

3. **AI Analysis** - Three AI models collaborate:
   - First model identifies the anomaly type
   - Second model performs deep root cause analysis
   - Third model validates and formats the findings

4. **Results** - Analysis results are:
   - Stored in MongoDB for historical tracking
   - Accessible via REST API
   - Viewable in the web dashboard

---

## 🔌 Integration

Causa integrates seamlessly with your existing Kubernetes stack:

- **Prometheus** - Metrics collection and alerting
- **Alertmanager** - Alert routing to Causa webhook
- **Ollama** - Local AI model inference
- **Cryostat** - JFR profiling (optional)
- **MongoDB** - Analysis storage and history

---

## 🛠️ Technology Stack

- **Framework**: Quarkus
- **AI**: LangChain4j with Ollama
- **Storage**: MongoDB
- **Deployment**: Kubernetes/OpenShift
- **Monitoring**: Prometheus

---

## 📊 Dashboard

Causa includes a built-in web dashboard for:
- Viewing analysis history
- Filtering by status, namespace, or pod
- Examining detailed RCA reports
- Tracking trends over time

Access the dashboard at: `http://<causa-service>:9090/dashboard`

---

## 🤝 Community

- **GitHub**: [causaai/causa](https://github.com/causaai/causa)
- **Demos**: [causaai/causa-demos](https://github.com/causaai/causa-demos)
- **Issues**: [Report bugs or request features](https://github.com/causaai/causa/issues)

---

<style>
.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
  margin: 2rem 0;
}

.doc-grid > div {
  padding: 1.5rem;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  background: #f6f8fa;
}

.doc-grid h3 {
  margin-top: 0;
}
</style>