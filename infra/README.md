# PlanMate Local Infra

Docker Compose와 Nginx 기반 로컬 실행 인프라 구성이다.

## Services

```text
postgres
redis
backend
nginx
```

## Usage

```bash
cp infra/.env.example infra/.env
docker compose --env-file infra/.env -f infra/compose.yaml up --build
```

Nginx는 `http://localhost`에서 backend API를 `/api` 경로로 프록시한다.
