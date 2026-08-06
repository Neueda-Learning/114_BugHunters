# CD Deployment Guide

This document describes how continuous deployment is handled for this project.

## Pipeline Responsibility

- Build/test and image publishing can be handled by CI workflows.
- Deployment is executed by Jenkins using [../Jenkinsfile](../Jenkinsfile).
- Jenkins deploys the latest commit from main.

## Jenkins Credentials Required

Create these Jenkins Secret text credentials:

- mysql-root-password
- spring-mail-username
- spring-mail-password
- otp-recipient-email

## Runtime Variables Used by Docker Compose

The deployment stack in [../docker-compose.yml](../docker-compose.yml) expects:

- MYSQL_ROOT_PASSWORD
- MYSQL_DATABASE (defaults to paymentdb)
- SPRING_MAIL_USERNAME
- SPRING_MAIL_PASSWORD
- APP_OTP_RECIPIENT_EMAIL
- Optional: APP_OTP_EXPIRY_MINUTES, SPRING_MAIL_HOST, SPRING_MAIL_PORT

Jenkins provides these values via credential binding and environment variables.

## Deployment Sequence

1. Jenkins checks out source.
2. Jenkins binds credential values.
3. Jenkins runs docker-compose down (best effort) and removes old app containers.
4. Jenkins runs docker-compose up -d --build --remove-orphans.
5. Jenkins prunes dangling images after success.

## Verification

After deployment:

- Frontend is reachable on host port 8082.
- Backend is reachable on host port 8081.
- MySQL container is healthy.
- Payment creation, validation, send-otp, and process endpoints work.

## Troubleshooting

### Docker Hub or image pull issues

- Check Docker login configuration where CI builds/pushes images.
- Verify image tags and repository visibility.

### SMTP or OTP failures

- Verify spring-mail-username and spring-mail-password credential values.
- Verify otp-recipient-email credential value.
- Check backend logs for authentication or TLS errors.

### Database startup/authentication issues

- Verify mysql-root-password credential value.
- Confirm backend uses the same password at runtime.

## Related Docs

- [README.md](README.md)
- [operations-runbook.md](operations-runbook.md)
- [../README.md](../README.md)
