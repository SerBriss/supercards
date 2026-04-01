# Supercards

A Spring Boot REST API for user management, built with Kotlin and PostgreSQL.

## Tech Stack

- **Language:** Kotlin (JVM 21)
- **Framework:** Spring Boot 4.0.0
- **Database:** PostgreSQL 12
- **Build:** Gradle 9.2.1 with Kotlin DSL
- **Containerization:** Jib (Docker), Helm (Kubernetes)

## Project Structure

```
supercards/
├── src/main/kotlin/com/serbriss/supercards/
│   ├── SupercardsApplication.kt
│   ├── api/
│   │   ├── SuperCardUserController.kt
│   │   ├── dto/                          # Request/response DTOs
│   │   └── handler/                      # Global exception handler
│   ├── repository/
│   │   ├── SuperCardUserRepository.kt
│   │   └── entity/SuperCardUser.kt       # JPA entity
│   ├── exception/
│   └── util/
└── deploy/
    ├── docker-compose.yml
    ├── .env
    └── helm/                             # Kubernetes Helm charts
```

## API Endpoints

Base path: `/api/user` — Port: `8199`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/user` | List all users |
| `POST` | `/api/user` | Create a user |
| `GET` | `/api/user/{id}` | Get user by ID |
| `PUT` | `/api/user/{id}` | Update user by ID |
| `DELETE` | `/api/user/{id}` | Delete user by ID |

**Request body (POST/PUT):**
```json
{
  "username": "john_doe",
  "email": "john@example.com"
}
```

**Response:**
```json
{
  "id": 1,
  "username": "john_doe",
  "email": "john@example.com"
}
```

## Running Locally

### Prerequisites

- JDK 21
- Docker and Docker Compose

### With Docker Compose

```bash
cd deploy/
docker-compose up -d
```

The API will be available at `http://localhost:8199`.

### Without Docker

Set the required environment variables, then:

```bash
./gradlew bootRun
```

## Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | JDBC connection URL | — |
| `PG_USER` | PostgreSQL username | `postgres` |
| `PG_PASSWORD` | PostgreSQL password | `postgres` |

See `deploy/.env` for the Docker Compose defaults.

## Build

```bash
# Build and run tests
./gradlew build

# Build Docker image (via Jib)
./gradlew jibDockerBuild
```

## Kubernetes Deployment

Helm charts are in `deploy/helm/`. The umbrella chart (`supercards-stack`) deploys the app and a bundled PostgreSQL instance together.

### Prerequisites

- [minikube](https://minikube.sigs.k8s.io/docs/start/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [helm](https://helm.sh/docs/intro/install/)

```bash
brew install minikube kubectl helm
```

### Deploy locally with minikube

```bash
# 1. Start the cluster
minikube start --driver=docker --cpus=2 --memory=4g

# 2. Point Docker at minikube's daemon and build the image into it
eval $(minikube docker-env)
chmod +x gradlew
./gradlew jibDockerBuild

# 3. Add Bitnami repo and fetch chart dependencies
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
helm dependency update deploy/helm/supercards-stack

# 4. Deploy
helm install stack deploy/helm/supercards-stack

# 5. Watch pods come up (both should reach Running)
kubectl get pods -w

# 6. Forward the port and test
kubectl port-forward svc/stack-supercards 8199:8199
curl http://localhost:8199/api/user
```

### Redeploy after a code change

```bash
eval $(minikube docker-env)
./gradlew jibDockerBuild
kubectl rollout restart deployment/stack-supercards
```

### Tear down

```bash
helm uninstall stack
minikube stop
```

### Notes

- `image.pullPolicy: Never` — the image is loaded directly into minikube; no registry needed.
- The JDBC URL `jdbc:postgresql://stack-postgresql:5432/postgres` is derived from the Helm release name `stack`. If you change the release name, update `supercards-stack/values.yaml` accordingly.
- After editing files under `deploy/helm/supercards/`, re-run `helm dependency update deploy/helm/supercards-stack` before upgrading — the umbrella chart bundles the app chart as a `.tgz`.

## Testing

```bash
./gradlew test
```
