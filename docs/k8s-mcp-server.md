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

Causa connects to the MCP server at `${KUBERNETES_MCP_URL}/mcp` using the `quarkus-langchain4j-mcp` extension with Streamable HTTP transport. If the MCP server is unavailable, Causa automatically falls back to direct Fabric8 Kubernetes client access.

## Configure Causa

```bash
kubectl set env deployment/rca-agent \
  KUBERNETES_MCP_ENABLED=true \
  KUBERNETES_MCP_URL=http://kubernetes-mcp-server.default.svc.cluster.local:3000/mcp
```

| Variable | Default | Description |
|----------|---------|-------------|
| `KUBERNETES_MCP_ENABLED` | `false` | Enable MCP integration |
| `KUBERNETES_MCP_URL` | `http://kubernetes-mcp-server.default.svc.cluster.local:3000` | MCP server base URL |
| `KUBERNETES_MCP_CONNECT_TIMEOUT` | `3000` | Connection timeout (ms) |
| `KUBERNETES_MCP_READ_TIMEOUT` | `10000` | Read timeout (ms) |
| `KUBERNETES_MCP_AUTH_ENABLED` | `false` | Enable bearer token auth |
| `KUBERNETES_MCP_AUTH_TOKEN` | `` | Bearer token |

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