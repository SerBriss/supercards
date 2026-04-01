# Helm Chart Implementation Plan — Supercards

## Project Baseline
- App image: `docker.id/serbriss/supercards:0.0.1-SNAPSHOT` (Jib)
- App port: 8199
- DB: PostgreSQL 12, all config from env vars (no hardcoded values in `application.properties`)

---

## Section 1: Manual App Chart

### Directory Structure
```
deploy/
  helm/
    supercards/                  # App chart
      Chart.yaml
      values.yaml
      templates/
        _helpers.tpl
        deployment.yaml
        service.yaml
        configmap.yaml
        secret.yaml
        ingress.yaml             # guarded by .Values.ingress.enabled
    supercards-stack/            # Umbrella chart (Section 2)
      Chart.yaml
      values.yaml
      charts/                    # populated by helm dependency update
```

### `deploy/helm/supercards/Chart.yaml`
```yaml
apiVersion: v2
name: supercards
description: Supercards Spring Boot application
type: application
version: 0.1.0
appVersion: "0.0.1-SNAPSHOT"
```

### `deploy/helm/supercards/values.yaml`
```yaml
replicaCount: 1

image:
  repository: docker.id/serbriss/supercards
  tag: "0.0.1-SNAPSHOT"
  pullPolicy: Never        # "Never" for local minikube; "IfNotPresent" otherwise

service:
  type: ClusterIP
  port: 8199

ingress:
  enabled: false
  className: ""
  host: supercards.local

resources: {}

db:
  jdbcUrl: ""              # set by caller or umbrella chart
  username: postgres
  password: postgres

dbSecret:
  name: supercards-db-secret

configMap:
  name: supercards-config
```

### `deploy/helm/supercards/templates/_helpers.tpl`
```
{{- define "supercards.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "supercards.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "supercards.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "supercards.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "supercards.selectorLabels" -}}
app.kubernetes.io/name: {{ include "supercards.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
```

### `deploy/helm/supercards/templates/configmap.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .Values.configMap.name }}
  labels:
    {{- include "supercards.labels" . | nindent 4 }}
data:
  SERVER_PORT: {{ .Values.service.port | quote }}
  SPRING_APPLICATION_NAME: "supercards"
```

### `deploy/helm/supercards/templates/secret.yaml`
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: {{ .Values.dbSecret.name }}
  labels:
    {{- include "supercards.labels" . | nindent 4 }}
type: Opaque
stringData:
  SPRING_DATASOURCE_URL: {{ .Values.db.jdbcUrl | quote }}
  SPRING_DATASOURCE_USERNAME: {{ .Values.db.username | quote }}
  SPRING_DATASOURCE_PASSWORD: {{ .Values.db.password | quote }}
```

### `deploy/helm/supercards/templates/deployment.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "supercards.fullname" . }}
  labels:
    {{- include "supercards.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      {{- include "supercards.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "supercards.selectorLabels" . | nindent 8 }}
    spec:
      containers:
        - name: supercards
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - containerPort: {{ .Values.service.port }}
          envFrom:
            - configMapRef:
                name: {{ .Values.configMap.name }}
            - secretRef:
                name: {{ .Values.dbSecret.name }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
```

> **Note on probes:** If you add `spring-boot-starter-actuator`, add readiness/liveness probes on path `/actuator/health`. Without it, skip probes or use `tcpSocket` on port 8199.

### `deploy/helm/supercards/templates/service.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ include "supercards.fullname" . }}
  labels:
    {{- include "supercards.labels" . | nindent 4 }}
spec:
  type: {{ .Values.service.type }}
  selector:
    {{- include "supercards.selectorLabels" . | nindent 4 }}
  ports:
    - protocol: TCP
      port: {{ .Values.service.port }}
      targetPort: {{ .Values.service.port }}
```

### `deploy/helm/supercards/templates/ingress.yaml`
```yaml
{{- if .Values.ingress.enabled }}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ include "supercards.fullname" . }}
  labels:
    {{- include "supercards.labels" . | nindent 4 }}
spec:
  ingressClassName: {{ .Values.ingress.className }}
  rules:
    - host: {{ .Values.ingress.host }}
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: {{ include "supercards.fullname" . }}
                port:
                  number: {{ .Values.service.port }}
{{- end }}
```

### Validation
```bash
helm lint deploy/helm/supercards

helm template supercards-dev deploy/helm/supercards \
  --set db.jdbcUrl="jdbc:postgresql://localhost:5432/postgres" \
  --set db.username="postgres" \
  --set db.password="postgres"
```

### Docs to read
- Chart best practices: https://helm.sh/docs/chart_best_practices/
- Built-in objects (`Release`, `Chart`): https://helm.sh/docs/chart_template_guide/builtin_objects/
- Template functions: https://helm.sh/docs/chart_template_guide/function_list/

**Pros:** Full control, easy to audit. **Cons:** Verbose boilerplate, you maintain every template.

---

## Section 2: Umbrella Chart (App + PostgreSQL)

### `deploy/helm/supercards-stack/Chart.yaml`
```yaml
apiVersion: v2
name: supercards-stack
description: Umbrella chart for Supercards app + PostgreSQL
type: application
version: 0.1.0
dependencies:
  - name: supercards
    version: "0.1.0"
    repository: "file://../supercards"         # local chart reference

  - name: postgresql
    version: "16.4.7"                           # pin this — Bitnami changes key layout between major versions
    repository: "https://charts.bitnami.com/bitnami"
    condition: postgresql.enabled
```

### `deploy/helm/supercards-stack/values.yaml`
```yaml
# ── Bitnami PostgreSQL subchart ───────────────────────────────────────────────
postgresql:
  enabled: true
  auth:
    username: postgres
    password: postgres
    database: postgres
  primary:
    persistence:
      enabled: true
      size: 1Gi

# ── Supercards app subchart ───────────────────────────────────────────────────
supercards:
  replicaCount: 1
  image:
    repository: docker.id/serbriss/supercards
    tag: "0.0.1-SNAPSHOT"
    pullPolicy: Never
  service:
    type: ClusterIP
    port: 8199
  db:
    # Bitnami names the service "<release>-postgresql"
    # With `helm install stack ...` → stack-postgresql
    jdbcUrl: "jdbc:postgresql://stack-postgresql:5432/postgres"
    username: postgres
    password: postgres
```

> **Critical:** Bitnami PostgreSQL service is named `<release-name>-postgresql`. If you change the release name from `stack`, update `jdbcUrl` to match.

### Commands
```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# Download subchart dependencies into deploy/helm/supercards-stack/charts/
helm dependency update deploy/helm/supercards-stack

helm lint deploy/helm/supercards-stack
helm template stack deploy/helm/supercards-stack
```

### Docs to read
- Helm subcharts: https://helm.sh/docs/chart_template_guide/subcharts_and_globals/
- Bitnami PostgreSQL chart: https://github.com/bitnami/charts/tree/main/bitnami/postgresql
- All available values: `helm show values bitnami/postgresql`

**Pros:** Single install deploys full stack; Bitnami chart is hardened. **Cons:** JDBC URL includes release name — renaming the release breaks connectivity.

---

## Section 3: Deploy Locally with Kubernetes

### Option A: minikube (recommended for Jib workflow)
```bash
brew install minikube
minikube start --driver=docker --cpus=2 --memory=4g

# Point Docker CLI at minikube's daemon, then build image directly into it
eval $(minikube docker-env)
./gradlew jibDockerBuild

# Install umbrella chart
helm install stack deploy/helm/supercards-stack

kubectl get pods -w

# Access the app
kubectl port-forward svc/stack-supercards 8199:8199
```

### Option B: kind
```bash
brew install kind
kind create cluster --name supercards

./gradlew jibDockerBuild
kind load docker-image docker.id/serbriss/supercards:0.0.1-SNAPSHOT --name supercards

helm install stack deploy/helm/supercards-stack --kube-context kind-supercards
kubectl port-forward svc/stack-supercards 8199:8199 --context kind-supercards
```

### Comparison

| Factor | minikube | kind |
|---|---|---|
| Image loading with Jib | Seamless via `eval $(minikube docker-env)` | Extra `kind load` step |
| Resource usage | Heavier | Lighter |
| Add-ons (Ingress, dashboard) | Rich built-in ecosystem | Minimal |
| CI friendliness | Moderate | Excellent |

### Docs to read
- minikube: https://minikube.sigs.k8s.io/docs/start/
- kind: https://kind.sigs.k8s.io/docs/user/quick-start/
- Jib Docker daemon build: https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#build-to-docker-daemon

---

## Section 4: Alternative — `com.citi.helm` Gradle Plugin

Integrates Helm into the Gradle build so `./gradlew helmPackage` produces a `.tgz` chart artifact alongside the image.

### `build.gradle.kts` changes
```kotlin
plugins {
    // ... existing plugins ...
    id("com.citi.helm") version "2.2.0"
    id("com.citi.helm-publish") version "2.2.0"  // optional, for publishing to chart repo
}

helm {
    charts {
        create("supercards") {
            chartName.set("supercards")
            chartVersion.set(project.version.toString())
            sourceDir.set(file("deploy/helm/supercards"))
        }
    }
}

// Optional: tie chart packaging to image push
tasks.named("helmPackage") {
    dependsOn("jib")
}
```

### Tasks
```bash
./gradlew helmLint        # validate chart templates
./gradlew helmPackage     # produces build/helm/charts/supercards-0.0.1-SNAPSHOT.tgz
./gradlew helmInstall     # install to current kubectl context
./gradlew helmUninstall
```

### Docs to read
- Plugin GitHub: https://github.com/citi/helm-gradle-plugin
- Gradle Plugin Portal: https://plugins.gradle.org/plugin/com.citi.helm

**Pros:** Chart version auto-synced with Gradle project version; chart `.tgz` is a Gradle artifact uploadable to Nexus/Artifactory; `helmLint` runs in CI automatically. **Cons:** Helm CLI must still be installed locally; plugin is community-maintained with limited docs; `helmInstall` is less flexible than raw `helm install` for complex value overrides.

---

## Section 5: Alternative — bjw-s `app-template`

A generic library chart that eliminates all boilerplate. You write only a `values.yaml` — no Deployment/Service/Ingress templates.

### `deploy/helm/supercards-bjws/Chart.yaml`
```yaml
apiVersion: v2
name: supercards-bjws
type: application
version: 0.1.0
dependencies:
  - name: app-template
    repository: "https://bjw-s-labs.github.io/helm-charts"
    version: "3.7.3"    # check https://bjw-s-labs.github.io/helm-charts for latest
```

### `deploy/helm/supercards-bjws/values.yaml`
```yaml
app-template:
  controllers:
    supercards:
      containers:
        app:
          image:
            repository: docker.id/serbriss/supercards
            tag: "0.0.1-SNAPSHOT"
          env:
            SERVER_PORT: "8199"
            SPRING_DATASOURCE_URL:
              valueFrom:
                secretKeyRef:
                  name: supercards-db-secret
                  key: SPRING_DATASOURCE_URL
            SPRING_DATASOURCE_USERNAME:
              valueFrom:
                secretKeyRef:
                  name: supercards-db-secret
                  key: SPRING_DATASOURCE_USERNAME
            SPRING_DATASOURCE_PASSWORD:
              valueFrom:
                secretKeyRef:
                  name: supercards-db-secret
                  key: SPRING_DATASOURCE_PASSWORD

  service:
    supercards:
      controller: supercards
      ports:
        http:
          port: 8199

  ingress:
    supercards:
      enabled: false
      hosts:
        - host: supercards.local
          paths:
            - path: /
              service:
                identifier: supercards
                port: http
```

The secret must be created separately (app-template does not manage secrets):
```bash
kubectl create secret generic supercards-db-secret \
  --from-literal=SPRING_DATASOURCE_URL="jdbc:postgresql://stack-postgresql:5432/postgres" \
  --from-literal=SPRING_DATASOURCE_USERNAME="postgres" \
  --from-literal=SPRING_DATASOURCE_PASSWORD="postgres"
```

### Commands
```bash
helm repo add bjw-s https://bjw-s-labs.github.io/helm-charts
helm repo update
helm dependency update deploy/helm/supercards-bjws
helm lint deploy/helm/supercards-bjws
helm install supercards deploy/helm/supercards-bjws
```

### Docs to read
- Official docs: https://bjw-s-labs.github.io/helm-charts/docs/
- Values schema: https://bjw-s-labs.github.io/helm-charts/docs/common-library/introduction/
- GitHub: https://github.com/bjw-s-labs/helm-charts

**Pros:** Minimal YAML; built-in support for init containers, persistence, ingress, networkPolicy; schema-validated values. **Cons:** Proprietary values structure (vendor lock-in to this chart); secrets managed externally; `3.x` broke `2.x` API — always pin version; less familiar to Kubernetes engineers.

---

## Recommended Path

| Phase | Action |
|---|---|
| **Now (local dev)** | Section 2 umbrella chart + minikube (Section 3A) — mirrors Docker Compose in one `helm install` |
| **CI/CD** | Add `com.citi.helm` plugin (Section 4) to version and package charts as Gradle artifacts |
| **Maintenance reduction** | Migrate app chart to bjw-s `app-template` (Section 5) if template boilerplate grows |

---

## Spring Boot Env Var Mapping

| Env Variable | Spring Property | Value |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:postgresql://stack-postgresql:5432/postgres` |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | from Secret |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | from Secret |
| `SERVER_PORT` | `server.port` | `8199` |

No Spring Boot code changes needed — `application.properties` is already clean with only `spring.application.name`.
