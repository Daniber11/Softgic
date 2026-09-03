# =============================================================================
#  MFE Indicadores (remoto federado) — imagen multi-stage
#
#  Mismo patron que shell/Dockerfile: contexto apps/frontend/ completo, porque
#  este paquete tambien resuelve @shared contra ../shared/src en build.
# =============================================================================

FROM node:24-alpine AS base
WORKDIR /app
# Ver el comentario identico en shell/Dockerfile: version de pnpm fijada por
# reproducibilidad, y minimum-release-age desactivado solo via flag CLI
# porque un .npmrc con la misma clave se probo primero y pnpm lo ignora.
RUN corepack enable && corepack prepare pnpm@10.11.1 --activate

COPY pnpm-workspace.yaml pnpm-lock.yaml ./
COPY shell/package.json shell/package.json
COPY mfe-indicadores/package.json mfe-indicadores/package.json
COPY shared/package.json shared/package.json
RUN corepack pnpm install --frozen-lockfile --config.minimum-release-age=0

FROM base AS build
COPY shared shared
COPY mfe-indicadores mfe-indicadores
WORKDIR /app/mfe-indicadores
ENV NODE_ENV=production
RUN corepack pnpm run build

FROM nginx:1.27-alpine
COPY --from=build /app/mfe-indicadores/dist /usr/share/nginx/html
COPY mfe-indicadores/nginx.conf /etc/nginx/conf.d/default.conf

RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && chown nginx:nginx /var/run/nginx.pid
USER nginx

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=15 --start-period=15s \
  CMD wget -q -O- http://localhost:8080/ | grep -qi '<!doctype html>' || exit 1

CMD ["nginx", "-g", "daemon off;"]
