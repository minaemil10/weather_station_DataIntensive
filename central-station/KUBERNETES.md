# Central Station - Kubernetes Deployment Guide

This guide explains the Kubernetes-friendly setup for the Central Station weather data storage service.

## Overview

The Central Station service has been containerized with Kubernetes best practices:

- **Persistent Volume Support**: Data directory mounted at `/data/bitcask` for persistent storage
- **Health Checks**: Kubernetes liveness, readiness, and startup probes
- **Configuration Management**: Environment variables for easy configuration in different environments
- **Non-root User**: Runs as unprivileged user (UID 1000) for security
- **Graceful Shutdown**: Proper signal handling and shutdown hooks
- **Resource Management**: CPU and memory requests/limits defined

## Building the Docker Image

### Build locally:
```bash
# Navigate to the central-station directory
cd central-station

# Build the Docker image
docker build -t weather-system/central-station:latest .

# Tag for your registry (e.g., Docker Hub or ECR)
docker tag weather-system/central-station:latest <your-registry>/central-station:latest
docker push <your-registry>/central-station:latest
```

### Build with multi-stage optimization:
The Dockerfile uses a multi-stage build:
1. **Builder stage**: Compiles Maven project in a heavyweight Maven container
2. **Runtime stage**: Minimal JRE container with only the compiled JAR

This results in a significantly smaller final image (~300MB vs 500MB+).

## Running Locally with Docker

### Start with docker-compose:
```bash
# Before running, ensure Kafka is up
docker-compose up central-station
```

### Or run standalone with persistent volume:
```bash
# Create volume
docker volume create central-station-data

# Run the container
docker run -d \
  --name central-station \
  -p 8080:8080 \
  -v central-station-data:/data/bitcask \
  -e KAFKA_BROKERS=kafka:9092 \
  weather-system/central-station:latest

# Check health
curl http://localhost:8080/health
```

## Kubernetes Deployment

### Prerequisites
- Kubernetes cluster (v1.16+)
- StorageClass configured (e.g., `standard` for cloud providers)
- Kafka cluster running and accessible

### Deploy to Kubernetes:

```bash
# Create namespace
kubectl create namespace weather-system

# Deploy using the manifest
kubectl apply -f kubernetes-deployment.yaml

# Verify deployment
kubectl get pods -n weather-system -l app=central-station
kubectl logs -n weather-system -l app=central-station

# Port forward to test locally
kubectl port-forward -n weather-system svc/central-station 8080:8080

# Test health endpoint
curl http://localhost:8080/health
```

## Environment Variables

Configure the service using environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DATA_DIR` | `/data/bitcask` | Path for BitCask data storage |
| `KAFKA_BROKERS` | `kafka:9092` | Comma-separated Kafka broker addresses |
| `SERVER_PORT` | `8080` | HTTP server port |
| `JAVA_OPTS` | See Dockerfile | JVM options (heap size, GC, etc.) |

### Example for custom configuration:
```bash
docker run -e DATA_DIR=/var/data -e KAFKA_BROKERS=kafka1:9092,kafka2:9092 \
  weather-system/central-station:latest
```

## API Endpoints

- `GET /weather?stationId=<id>` - Get weather data for a specific station
- `GET /weather/all` - Get weather data for all stations
- `GET /health` - Health check (used by Kubernetes probes)

## Persistent Volume Details

### Storage Requirements
The deployment requests:
- **Initial storage**: 10Gi (configurable in `kubernetes-deployment.yaml`)
- **AccessMode**: ReadWriteOnce (single pod read-write access)

The PVC mounts at `/data/bitcask` inside the container where BitCask stores:
- **Active files**: `active/XXXX.data` - Current write log
- **Archive files**: `archive/merge_*.data` - Compacted data
- **Hint files**: `archive/merge_*.hint` - Index hints for fast startup

### Backup Strategy
To backup persistent data:
```bash
# Create snapshot of PVC
kubectl snapshot create central-station-backup -n weather-system \
  --source=pvc/central-station-data

# Restore from snapshot
kubectl restore central-station-backup -n weather-system \
  --destination=pvc/central-station-data-restored
```

## Health Checks Configuration

Kubernetes uses three types of probes:

### Liveness Probe
- Checks if service is alive
- Restarts pod if unhealthy
- Interval: 10s, Timeout: 5s, Threshold: 3 failures

### Readiness Probe  
- Checks if service is ready to serve traffic
- Removes from load balancer if not ready
- Interval: 5s, Timeout: 3s, Threshold: 2 failures

### Startup Probe
- Gives service time to start up
- Prevents killing new pods
- Interval: 5s, Timeout: varies, Threshold: 30 failures (150s total)

## Scaling Considerations

### Single-pod deployment
The current deployment uses `replicas: 1` because:
- BitCask is not designed for multi-writer scenarios
- Persistent storage is single-access (ReadWriteOnce)
- Kafka consumer group is exclusive to central-station-group

### For high availability (future):
1. Use ReadWriteMany storage (e.g., NFS, EFS)
2. Implement distributed locking for BitCask access
3. Use pod anti-affinity for multi-zone deployment

## Monitoring & Logging

### View logs:
```bash
# Real-time logs
kubectl logs -f -n weather-system deployment/central-station

# Last 100 lines
kubectl logs -n weather-system deployment/central-station --tail=100

# With timestamps
kubectl logs -n weather-system deployment/central-station --timestamps=true
```

### Check resource usage:
```bash
kubectl top pod -n weather-system -l app=central-station
```

## Troubleshooting

### Pod won't start
```bash
kubectl describe pod -n weather-system -l app=central-station
kubectl logs -n weather-system -l app=central-station --previous
```

### Persistent volume mount issues
```bash
# Check PVC status
kubectl get pvc -n weather-system

# Describe PVC for details
kubectl describe pvc central-station-data -n weather-system
```

### Kafka connectivity issues
```bash
# Test from pod
kubectl exec -it -n weather-system deployment/central-station \
  -- nc -zv kafka 9092

# Check logs for errors
kubectl logs -n weather-system deployment/central-station | grep -i kafka
```

## Security Considerations

✅ **Implemented**:
- Non-root user (UID 1000)
- Read-only filesystem where possible
- SecurityContext with dropped capabilities
- Resource limits (CPU, memory)
- NetworkPolicy can be added

⚠️ **To add**:
- Ingress with TLS/SSL
- NetworkPolicy for pod communication
- RBAC rules for service account
- Secrets for sensitive configuration

## Cleanup

```bash
# Remove deployment
kubectl delete -f kubernetes-deployment.yaml

# Remove namespace (deletes all resources in it)
kubectl delete namespace weather-system

# Remove PVC (delete PersistentVolumeClaim manually to keep data)
kubectl delete pvc central-station-data -n weather-system
```

## References

- [Kubernetes Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)
- [BitCask Design](https://en.wikipedia.org/wiki/Bitcask)
- [Docker Multi-stage Builds](https://docs.docker.com/build/building/multi-stage/)
