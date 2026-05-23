# Stage 1: Build frontend
FROM node:20-alpine AS builder

WORKDIR /app
COPY package*.json ./

# 使用 npm 镜像加速构建
RUN npm config set registry https://registry.npmmirror.com && npm ci

# 复制前端源码并构建
COPY . .
RUN npm run build-only

# Stage 2: Nginx runtime
FROM nginx:alpine

# 替换默认的 nginx html 目录内容
COPY --from=builder /app/dist /usr/share/nginx/html

# 覆盖默认的 nginx 配置文件
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
