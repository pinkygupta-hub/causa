---
layout: page
title: Getting Started
nav_order: 2
---

# Getting Started

Get Causa RCA up and running in minutes with our automated demo setup.

---

## Quick Start with Demo

The fastest way to try Causa is using our automated demo script:

### Prerequisites

- Docker installed
- Kind installed
- `kubectl` installed
- 16GB RAM available
- 4 vcpus avialbale
- 3GB+ space available
- Internet connection

### Run the Demo

```bash
# Clone the demo repository
git clone https://github.com/causaai/causa-demos
cd causa-demos

# Run the automated setup script
./kind/demo.sh
```

### What the Demo Includes

The demo script automatically sets up:

✅ **Local Kubernetes Cluster** (Kind)  
✅ **Prometheus** - Metrics collection and alerting  
✅ **Ollama** - AI model serving  
✅ **MongoDB** - Analysis storage  
✅ **Causa RCA Agent** - The main application  
✅ **Sample Applications** - Demo workloads with issues  
✅ **Pre-configured Alerts** - Ready-to-fire alerts  

### Access the Dashboard

Once the demo is running:

```bash
# Get the dashboard URL
kubectl get svc -n default rca-agent

# Port forward to access locally
kubectl port-forward svc/rca-agent 9090:9090

# Open in browser
open http://localhost:9090/dashboard
```

---

## Getting Help

### Community

- **GitHub**: [causaai/causa](https://github.com/causaai/causa)
- **Issues**: [Report bugs](https://github.com/causaai/causa/issues)
- **Demos**: [causa-demos](https://github.com/causaai/causa-demos)

---

[← Back to Home](index.html) | [Next: API Reference →](api-reference.html)