# MCP Server Deployment Guide

Deployment configurations for the Kubernetes MCP server used by Causa for Kubernetes operations.

## Files

| File | Description |
|------|-------------|
| [`kubernetes-mcp-server-deployment.yaml`](./kubernetes-mcp-server-deployment.yaml) | Standard Kubernetes deployment |
| [`openshift-mcp-server-deployment.yaml`](./openshift-mcp-server-deployment.yaml) | OpenShift deployment (2 replicas, Route with TLS) |

## Deploy

**Kubernetes:**
```bash
kubectl apply -f deployment/mcp-server/kubernetes-mcp-server-deployment.yaml
```

**OpenShift:**
```bash
oc apply -f deployment/mcp-server/openshift-mcp-server-deployment.yaml
```

## How It Works

The MCP server uses a **zero-build strategy** — no custom Docker image needed. It runs `npx kubernetes-mcp-server` on a standard `node:20-slim` image, fetching the latest version at startup.

Causa connects to the MCP server using the `quarkus-langchain4j-mcp` extension with Streamable HTTP transport. The value of `QUARKUS_LANGCHAIN4J_MCP__K8S__URL` is used verbatim as the full MCP endpoint URL (the `/mcp` path must be included in the value). If the MCP server is unavailable, Causa automatically falls back to direct Fabric8 Kubernetes client access.

## Configure Causa

```bash
kubectl set env deployment/rca-agent \
  KUBERNETES_MCP_ENABLED=true \
  QUARKUS_LANGCHAIN4J_MCP__K8S__URL=http://kubernetes-mcp-server.default.svc.cluster.local:3000/mcp
```

| Variable | Default | Description |
|----------|---------|-------------|
| `KUBERNETES_MCP_ENABLED` | `false` | Enable MCP integration |
| `QUARKUS_LANGCHAIN4J_MCP__K8S__URL` | `http://kubernetes-mcp-server:3000/mcp` | Full MCP endpoint URL (must include `/mcp` path) |
| `QUARKUS_LANGCHAIN4J_MCP__K8S__TOOL_EXECUTION_TIMEOUT` | `60s` | Tool execution timeout |
| `QUARKUS_LANGCHAIN4J_MCP__K8S__LOG_REQUESTS` | `false` | Log raw MCP request wire traffic |
| `QUARKUS_LANGCHAIN4J_MCP__K8S__LOG_RESPONSES` | `false` | Log raw MCP response wire traffic |
| `MCP_CIRCUIT_OPEN_DURATION_MS` | `60000` | Circuit-breaker cooldown (ms) — how long MCP calls are suppressed after a connectivity failure before a probe is attempted |

## Verify

```bash
# Check pod status
kubectl get pods -l app=kubernetes-mcp-server

# View logs
kubectl logs -l app=kubernetes-mcp-server

# Test health endpoint
kubectl port-forward svc/kubernetes-mcp-server 3000:3000
curl http://localhost:3000/healthz
```

## Troubleshooting

**Connection timeout / Causa always falls back to Fabric8:**
```bash
# Is the pod running?
kubectl get pods -l app=kubernetes-mcp-server

# Check logs for npx/npm errors (requires internet access on first run)
kubectl logs -l app=kubernetes-mcp-server

# Test connectivity from Causa pod
kubectl exec -it deployment/rca-agent -- \
  curl http://kubernetes-mcp-server.default.svc.cluster.local:3000/healthz

# Disable MCP if not needed (Fabric8 fallback works fine)
kubectl set env deployment/rca-agent KUBERNETES_MCP_ENABLED=false
```

**RBAC permission errors:**
```bash
kubectl auth can-i get pods --as=system:serviceaccount:default:kubernetes-mcp-server
kubectl get clusterrolebinding kubernetes-mcp-server-binding
```

**Increase timeouts if MCP server is slow:**
```bash
kubectl set env deployment/rca-agent \
  KUBERNETES_MCP_CONNECT_TIMEOUT=10000 \
  KUBERNETES_MCP_READ_TIMEOUT=30000
```