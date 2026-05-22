# Central Station - Kubernetes-Friendly Dockerization Summary

## What Was Changed

### 1. **Java Code Updates**

#### CentralStationApp.java
- ✅ Added environment variable configuration:
  - `DATA_DIR` - Data storage path (default: `/data/bitcask`)
  - `KAFKA_BROKERS` - Kafka connection string (default: `localhost:9092`)
  - `SERVER_PORT` - HTTP server port (default: `8080`)
- ✅ Changed threads from daemon to non-daemon (proper graceful shutdown)
- ✅ Added shutdown hook for clean container termination
- ✅ Added `/health` endpoint for Kubernetes probes
- ✅ Fixed server to listen on `0.0.0.0` instead of `localhost`

#### BitCaskEngine.java
- ✅ Made data directory configurable via constructor
- ✅ Added two constructors: default and custom path
- ✅ Updated archive directory path to use configurable base

#### KafkaConsumerService.java
- ✅ Added Kafka brokers as constructor parameter
- ✅ Backward compatible with default constructor
- ✅ Added graceful shutdown support
- ✅ Added proper error handling for interruption
- ✅ Fixed session timeout configuration

### 2. **Build Configuration**

#### pom.xml
- ✅ Added Maven Shade plugin for fat JAR creation
- ✅ Configured manifest with main class
- ✅ Enables single-file deployment in containers

### 3. **Docker Containerization**

#### Dockerfile
- ✅ Multi-stage build (builder + runtime)
- ✅ Non-root user (appuser, UID 1000)
- ✅ Health check endpoint configured
- ✅ Persistent volume mount point at `/data/bitcask`
- ✅ Environment variables with sensible defaults
- ✅ JVM optimization flags for containerized environment
- ✅ Minimal runtime image using eclipse-temurin:11-jre-jammy

#### .dockerignore
- ✅ Optimizes build context (excludes unnecessary files)

### 4. **Kubernetes Manifests**

#### kubernetes-deployment.yaml
Complete production-ready deployment with:
- ✅ ConfigMap for shared configuration
- ✅ PersistentVolumeClaim (10Gi) for data storage
- ✅ Deployment with single replica (BitCask limitation)
- ✅ Health checks: Liveness, Readiness, Startup probes
- ✅ Resource requests and limits
- ✅ Security context (non-root, no privilege escalation)
- ✅ Pod anti-affinity for high availability
- ✅ Graceful shutdown with termination grace period
- ✅ Service for DNS and load balancing

### 5. **Documentation**

#### KUBERNETES.md
- Complete deployment guide
- Local testing instructions
- Environment variable reference
- Health check details
- Storage and backup strategy
- Troubleshooting guide
- Security considerations

#### docker-compose.central-station.yml
- Override configuration for Docker Compose
- Volumes defined with local driver
- Health checks configured
- Environment variables aligned with Kubernetes setup

## Key Kubernetes Features Implemented

### Persistent Volume Support
```
/data/bitcask (in container)
    ↓ (mounted via PVC)
Kubernetes PersistentVolume (10Gi)
```

### Health Checking
- **Liveness**: Restarts unhealthy pods
- **Readiness**: Removes from load balancer if not ready
- **Startup**: Gives new pods time to initialize

### Graceful Shutdown
1. Kubernetes sends SIGTERM to pod
2. JVM shutdown hook interrupts threads
3. Kafka consumer closes connection
4. HTTP server stops accepting requests
5. 30-second grace period for cleanup

### Security Context
- Runs as non-root user (UID 1000)
- No privilege escalation
- Dropped dangerous capabilities
- Read-only filesystem where applicable

## Building and Testing

### 1. Build the JAR
```bash
cd central-station
mvn clean package
```

### 2. Build Docker Image
```bash
docker build -t central-station:latest .
```

### 3. Test Locally with Docker
```bash
# Create volume
docker volume create central-station-data

# Run container
docker run -d \
  -p 8080:8080 \
  -v central-station-data:/data/bitcask \
  -e KAFKA_BROKERS=localhost:9092 \
  central-station:latest

# Test health
curl http://localhost:8080/health
```

### 4. Deploy to Kubernetes
```bash
# Create namespace
kubectl create namespace weather-system

# Apply manifests
kubectl apply -f central-station/kubernetes-deployment.yaml

# Verify
kubectl get all -n weather-system
```

## Configuration for Different Environments

### Local Development
```
DATA_DIR=/data/bitcask (local volume)
KAFKA_BROKERS=localhost:9092
SERVER_PORT=8080
```

### Docker Compose
```
DATA_DIR=/data/bitcask (named volume)
KAFKA_BROKERS=kafka:9092
SERVER_PORT=8080
```

### Kubernetes (Production)
```
DATA_DIR=/data/bitcask (PersistentVolume)
KAFKA_BROKERS=kafka-0.kafka-headless:9092,...
SERVER_PORT=8080
```

## Storage Architecture

### Before (hardcoded paths):
```
central-station/data/
├── active/
│   └── XXXX.data
└── archive/
    ├── merge_*.data
    └── merge_*.hint
```

### After (configurable):
```
/data/bitcask/
├── active/
│   └── XXXX.data
└── archive/
    ├── merge_*.data
    └── merge_*.hint
```

The configurable path allows:
- ✅ Easy persistent volume mounting in Kubernetes
- ✅ Different storage backends (local, NFS, EBS, etc.)
- ✅ Multiple instances with separate volumes

## Migration from Old Setup

If you have existing data:

```bash
# Backup old data
tar -czf central-station-backup.tar.gz central-station/data/

# Copy to new persistent volume location
docker cp central-station-backup.tar.gz <volume-id>:/backup/
docker exec <container-id> tar -xzf /backup/central-station-backup.tar.gz -C /data/

# Or in Kubernetes
kubectl cp central-station-backup.tar.gz weather-system/<pod-name>:/tmp/
kubectl exec -it -n weather-system <pod-name> \
  -- tar -xzf /tmp/central-station-backup.tar.gz -C /data/
```

## Next Steps

1. **Build and push image** to your container registry
2. **Update image reference** in kubernetes-deployment.yaml
3. **Adjust storage size** based on your data needs
4. **Configure Kafka brokers** for your environment
5. **Set resource limits** based on your cluster capacity
6. **Add Ingress** for external access if needed
7. **Configure monitoring** (Prometheus, ELK, etc.)

## Rollback Plan

```bash
# Revert to previous version
kubectl rollout undo deployment/central-station -n weather-system

# Check rollout history
kubectl rollout history deployment/central-station -n weather-system

# Manual rollback to specific revision
kubectl rollout undo deployment/central-station -n weather-system --to-revision=1
```

## Performance Tuning

### JVM Options (adjustable):
- `-XX:+UseG1GC` - G1 garbage collector for low latency
- `-Xmx512m` - Maximum heap size (adjust based on data volume)
- `-Xms256m` - Initial heap size
- `-XX:+UnlockExperimentalVMOptions` - Enable experimental optimizations

### Storage Optimization:
- Adjust `maxFileSize` in BitCaskEngine for different workloads
- Monitor compaction frequency and adjust sleep interval
- Consider memory-mapped I/O for larger datasets

---

**Status**: ✅ Ready for Kubernetes Deployment
