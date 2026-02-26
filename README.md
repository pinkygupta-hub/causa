# RCA Agent Analysis Tool (Quarkus)

Causa RCA agent is a Quarkus-based Root Cause Analysis (RCA) agent for Kubernetes. It uses LangChain4j to orchestrate multiple AI agents for anomaly detection, RCA, and report validation.

## Operational Modes

The RCA agent supports two operational modes:

### 1. MONITORING Mode (Default)
- **Scheduled scanning** of Kubernetes pods with specific labels
- Proactive, continuous monitoring at configurable intervals
- Ideal for development, testing, and continuous health checks

### 2. ALERT_DRIVEN Mode
- **Event-driven analysis** triggered by Prometheus/Alertmanager alerts
- Reactive troubleshooting on-demand
- Ideal for production environments with established alerting
- Reduces resource usage when no issues are present

**Configuration:**
```properties
# Set in application.properties
rca.mode=MONITORING  # or ALERT_DRIVEN
```

See [MODE_CONFIGURATION.md](MODE_CONFIGURATION.md) for detailed configuration guide.

## Architecture

The application follows a pipeline-based approach:
1. **Data Collection**: Fetches metrics (Prometheus), logs, and JFR analysis.
2. **Anomaly Detection (Model A)**: Classifies the incident.
3. **Root Cause Analysis (Model B)**: Performs deep reasoning with RAG (Retrieval Augmented Generation) using knowledge base runbooks.
4. **Validation (Model C)**: Critiques the findings and formats the output into a structured JSON report.

