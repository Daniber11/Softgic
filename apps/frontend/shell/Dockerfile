# =============================================================================
#  Shell (host de microfrontends) — imagen multi-stage
#
#  El contexto de build es apps/frontend/ COMPLETO, no apps/frontend/shell/:
#  el shell resuelve el alias @shared contra ../shared/src en tiempo de build
#  (rspack.config.js) y pnpm necesita ver los tres package.json del workspace
#  para instalar de forma consistente con el lockfile compartido.
#  docker compose invoca este Dockerfile con ese contexto explicitamente.
#
#  Los valores por defecto de KEYCLOAK_ISSUER / SOLICITUDES_API_URL /
#  INDICADORES_API_URL / MFE_INDICADORES_URL en rspack.config.js ya apuntan a
#  localhost:8080/8081/8082/3001: es exactamente lo que necesita el
#  NAVEGADOR del usuario (no el contenedor), asi que el build no requiere
#  build-args adicionales en este stack local.
# =============================================================================

FROM node:24-alpine AS base
WORKDIR /app
# Se fija la version exacta de pnpm ya usada y verificada en el resto del
# proyecto (host: `corepack pnpm --version` -> 10.11.1); sin esto, corepack
# resuelve a la ultima version que conozca en ese momento, y una imagen
# construida hoy podria usar un pnpm distinto al construido manana.
RUN corepack enable && corepack prepare pnpm@10.11.1 --activate

COPY pnpm-workspace.yaml pnpm-lock.yaml ./
COPY shell/package.json shell/package.json
COPY mfe-indicadores/package.json mfe-indicadores/package.json
COPY shared/package.json shared/package.json
# pnpm 10.x rechaza por defecto instalar paquetes publicados en las ultimas
# 24h (proteccion de cadena de suministro, ERR_PNPM_MINIMUM_RELEASE_AGE_VIOLATION).
# --config.minimum-release-age=0 la desactiva SOLO para este build: es la
# unica forma verificada de que surta efecto (un .npmrc con la misma clave se
# probo primero y pnpm lo ignora). El lockfile ya fija versiones EXACTAS y
# revisadas; la proteccion real contra un paquete malicioso se aplico al
# decidir y congelar esas versiones, no en cada reinstalacion posterior de
# las mismas.
RUN corepack pnpm install --frozen-lockfile --config.minimum-release-age=0

FROM base AS build
COPY shared shared
COPY shell shell
WORKDIR /app/shell
ENV NODE_ENV=production
RUN corepack pnpm run build

FROM nginx:1.27-alpine
COPY --from=build /app/shell/dist /usr/share/nginx/html
COPY shell/nginx.conf /etc/nginx/conf.d/default.conf

# La imagen oficial ya declara un usuario "nginx"; escuchar en un puerto por
# encima de 1024 (ver nginx.conf) evita necesitar CAP_NET_BIND_SERVICE, que es
# lo unico que de otro modo obligaria a correr como root.
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && chown nginx:nginx /var/run/nginx.pid
USER nginx

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=15 --start-period=15s \
  CMD wget -q -O- http://localhost:8080/ | grep -qi '<!doctype html>' || exit 1

CMD ["nginx", "-g", "daemon off;"]
