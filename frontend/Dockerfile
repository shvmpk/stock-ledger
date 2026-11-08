# syntax=docker/dockerfile:1.4

# =============================================================
# Stage 1: Compile Vite SPA with Bun
# =============================================================
FROM oven/bun:1-alpine AS builder
WORKDIR /app

# Copy dependency specifications for Docker layer caching
COPY package.json bun.lock* ./
RUN bun install --frozen-lockfile

# Copy frontend source tree and build production bundle
COPY . .
RUN bun run build

# =============================================================
# Stage 2: Hardened Runtime via Unprivileged Alpine Nginx
# =============================================================
FROM nginxinc/nginx-unprivileged:alpine-slim AS runtime

# Install custom non-root Nginx reverse-proxy configuration
COPY --chown=nginx:nginx nginx.conf /etc/nginx/nginx.conf

# Copy production SPA assets
COPY --from=builder --chown=nginx:nginx /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1

ENTRYPOINT ["nginx", "-g", "daemon off;"]
