# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# Public API origin for native builds only; empty = same origin (web)
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run i18n:check && npm run test && npm run build

FROM nginx:1.31-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# TLS cert/key are bind-mounted read-only into /etc/nginx/certs at runtime (docker-compose.yml)
RUN mkdir -p /etc/nginx/certs \
    && chown -R nginx:nginx /var/cache/nginx /usr/share/nginx/html \
    && touch /var/run/nginx.pid && chown nginx:nginx /var/run/nginx.pid
USER nginx
EXPOSE 60003
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1:8081/healthz >/dev/null || exit 1
