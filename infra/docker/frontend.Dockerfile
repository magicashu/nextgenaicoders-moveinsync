FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY frontend/package.json frontend/package.json
RUN sed -i 's#https://int.repositories.cloud.sap/artifactory/api/npm/build-milestones-npm/#https://registry.npmjs.org/#g' package-lock.json
RUN npm ci --workspace frontend
COPY frontend/ frontend/
RUN npm run build --workspace frontend

FROM nginx:alpine
COPY infra/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html
EXPOSE 80
