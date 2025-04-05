# 使用官方的 OpenJDK 基础镜像
FROM openjdk:8-jdk-slim

# 设置工作目录
WORKDIR /app

# 将打包好的 JAR 文件复制到容器中
COPY target/dyUMB-1.0-SNAPSHOT.jar .

# 暴露 Spring Boot 应用的端口
EXPOSE 8071

# 启动命令
ENTRYPOINT ["java","-jar","/app/dyUMB-1.0-SNAPSHOT.jar","--spring.profiles.active=prod"]