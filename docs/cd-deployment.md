# CD Deployment Guide

## Required GitHub Secrets

Add these repository secrets in GitHub:

- `DOCKER_USERNAME`: Docker Hub username.
- `DOCKER_PASSWORD`: Docker Hub password or access token.

The workflow in `.github/workflows/cd.yml` uses these to authenticate and push Docker images.

## Required Runtime Environment Variables

When running `docker compose`, provide these variables:

- `DOCKER_USERNAME`: Docker Hub username that owns the images.
- `MYSQL_ROOT_PASSWORD`: MySQL root password for the database container and backend datasource.
- `MYSQL_DATABASE` (optional): defaults to `paymentdb`.
- `IMAGE_TAG` (optional): defaults to `latest`.

Example PowerShell session:

```powershell
$env:DOCKER_USERNAME = "your-dockerhub-username"
$env:MYSQL_ROOT_PASSWORD = "your-strong-password"
$env:MYSQL_DATABASE = "paymentdb"
$env:IMAGE_TAG = "latest"
```

## How Deployment Works with Docker Compose

1. `docker compose pull` pulls prebuilt backend and frontend images from Docker Hub.
2. `docker compose up -d` starts:
   - `mysql` with persistent volume `mysql_data`
   - `backend` (depends on healthy MySQL)
   - `frontend` served by Nginx
3. Frontend API calls to `/api/*` are reverse-proxied by Nginx to `backend:8080`.

## Complete CI/CD Flow

1. Developers push code to `main`.
2. Existing CI workflow (`.github/workflows/ci.yml`) runs backend tests and frontend build.
3. On CI success, CD workflow (`.github/workflows/cd.yml`) is triggered.
4. CD logs in to Docker Hub.
5. CD builds and pushes:
   - `DOCKER_USERNAME/payment-backend:latest` and sha tag
   - `DOCKER_USERNAME/payment-frontend:latest` and sha tag
6. Jenkins (or any deployment host) runs `docker compose pull` and `docker compose up -d` to deploy latest images.

## Local Deployment Commands

Build images manually:

```powershell
docker build -t your-dockerhub-username/payment-backend:latest .
docker build -t your-dockerhub-username/payment-frontend:latest ./client
```

Run full stack with compose:

```powershell
$env:DOCKER_USERNAME = "your-dockerhub-username"
$env:MYSQL_ROOT_PASSWORD = "your-strong-password"
docker compose pull
docker compose up -d
```

Inspect running environment:

```powershell
docker images
docker ps
```

## Security Notes

- Do not hardcode passwords in `docker-compose.yml`.
- Use GitHub Secrets for CI/CD credentials.
- Prefer Docker Hub access tokens over account password.
- Updated deployment verification steps.