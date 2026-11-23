FROM node:26-bookworm-slim AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@12.15.0 --activate
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY index.html biome.json tsconfig.json tsconfig.app.json tsconfig.node.json vite.config.ts ./
COPY scripts ./scripts
COPY src ./src
RUN pnpm run build

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /workspace/dist /srv
EXPOSE 80
