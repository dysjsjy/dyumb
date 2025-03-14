# dyUMB

## 一、项目概述
dyUMB 是一个基于 Spring Boot 开发的后端项目，集成了 MySQL 数据库、Redis 缓存、MyBatis-Plus 等技术，用于构建一个具备用户管理、团队管理等功能的应用。项目使用 Docker Compose 进行容器化部署，方便在不同环境中快速搭建和运行。

## 二、项目结构
```
dyumb/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── dy/
│   │   │           └── umb/
│   │   │               ├── config/         # 配置类
│   │   │               ├── controller/     # 控制器类
│   │   │               ├── exception/      # 异常处理类
│   │   │               ├── job/            # 定时任务类
│   │   │               ├── model/          # 模型类
│   │   │               ├── service/        # 服务类
│   │   │               └── utils/          # 工具类
│   │   └── resources/
│   │       ├── application.yml             # 主配置文件
│   │       ├── application-dev.yml         # 开发环境配置文件
│   │       └── docker-compose.yml          # Docker 容器编排文件
├── pom.xml                                 # Maven 项目配置文件
```

## 三、技术栈
- **后端框架**：Spring Boot 2.6.4
- **数据库**：MySQL 8.0
- **缓存**：Redis 6.2
- **持久层框架**：MyBatis-Plus 3.5.1
- **序列化工具**：Gson 2.8.9
- **API 文档**：Knife4j 2.0.7

## 四、环境配置
### 1. 安装 Java 和 Maven
确保你已经安装了 Java 8 及以上版本和 Maven 3.x 版本。

### 2. 安装 Docker 和 Docker Compose
如果你想使用 Docker 进行容器化部署，需要安装 Docker 和 Docker Compose。

### 3. 配置文件
- `application.yml`：主配置文件，包含数据库、Redis、服务器等基本配置。
- `application-dev.yml`：开发环境配置文件，可根据需要修改日志级别等信息。

### 4. `docker-compose.yml`
该文件定义了 MySQL、Redis、Spring Boot 应用和 Nginx 服务的容器化配置。
- MySQL 服务：使用 `mysql:8.0` 镜像，端口映射为 `3303:3306`。
- Redis 服务：使用 `redis:6.2` 镜像，端口映射为 `6377:6379`。
- Spring Boot 应用服务：构建当前项目，端口映射为 `8071:8071`。
- Nginx 服务：使用 `nginx:1.21` 镜像，端口映射为 `82:80`。

## 五、运行项目
### 1. 使用 Docker Compose 部署
在项目根目录下执行以下命令启动所有服务：
```bash
docker-compose up -d
```
该命令会自动下载所需的镜像并启动 MySQL、Redis、Spring Boot 应用和 Nginx 服务。
注意docker-compose仅帮你创建了基本的容器，还需要手动去容器中配置。

### 2. 本地运行
如果你不想使用 Docker，可以直接在本地运行项目。在项目根目录下执行以下命令：
```bash
mvn spring-boot:run
```

## 六、主要功能模块
### 1. 用户推荐
- `UserController` 中的 `recommendUsers` 方法实现了用户推荐功能，使用 Redis 缓存提高性能。如果缓存中存在推荐用户信息，则直接返回缓存数据；否则，从数据库中查询并将结果存入缓存。
- `PreCacheJob` 类中的 `doCacheRecommendUser` 方法是一个定时任务，每天 00:31 执行，用于预热推荐用户缓存。

### 2. 团队状态枚举
`TeamStatusEnum` 定义了团队的三种状态：公开、私有和加密，并提供了根据值获取枚举实例的方法。

### 3. 异常处理
`GlobalExceptionHandler` 类用于处理运行时异常，将异常信息记录日志并返回统一的错误响应。

### 4. Redis 配置
- `RedissonConfig` 类配置了 Redisson 客户端，用于连接 Redis 服务器。
- `RedisTemplateConfig` 类配置了 RedisTemplate，用于操作 Redis 缓存。

## 七、注意事项
- 在 `PreCacheJob` 类中，`mainUserList` 可以设置为前一天活跃的用户，以减少不必要的缓存操作。
- 项目中使用了逻辑删除，`mybatis-plus` 配置了全局逻辑删除字段 `isDelete`，已删除值为 1，未删除值为 0。