-- campus-sched 数据库首次初始化脚本
-- 由 PostgreSQL 容器首次启动时执行（挂载到 /docker-entrypoint-initdb.d/）。
-- 只运行一次：已有数据卷不会重复执行，避免覆盖用户已修改数据。

-- 业务账号：应用/测试连接使用，不直接使用超级用户。
-- 开发默认密码与 .env.example 中 DB_PASSWORD 一致（示例非秘密值）；正式环境请修改。
CREATE USER campus_app WITH PASSWORD 'campus_app_dev';

-- 开发库（后端 dev profile 连接）
CREATE DATABASE campus_dev OWNER campus_app;

-- 测试库（集成测试使用；测试支撑按 t_<uuid> 创建独立 schema，故需 OWNER 权限）
CREATE DATABASE campus_test OWNER campus_app;
