# 前端镜像（配置经理拥有）
# 构建上下文 = 仓库根；依赖 UI 交付 web/package.json 与锁文件。
# 产物由 Nginx 提供静态文件并反代 /api（见 infra/nginx/default.conf）。

FROM node:22 AS build
WORKDIR /build
COPY web/package.json web/package-lock.json* ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY infra/nginx/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
