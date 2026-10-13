# 后端镜像（配置经理拥有）
# 构建上下文 = 仓库根（04 协作规程 §4）；依赖 PM 交付 server/pom.xml 与 Maven Wrapper。
# 种子文件 test-data/demo.json 按需复制（04 §4，不含密码），供 DemoDataSeeder 读取。

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 先复制 pom 以复用依赖缓存；server/ 目前仅有 db/migration 与测试源码，无 pom（待 PM M0 交付）
COPY server/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline
COPY server/src ./src
# 假设 pom 产出单一可执行 jar（Spring Boot repackage）
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
COPY test-data/demo.json ./test-data/demo.json
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
