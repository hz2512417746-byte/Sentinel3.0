# Sentinel 3.0 · DPI 实时反诈预警系统

融合**增量特征 + 规则引擎 + 机器学习**的 DPI（深度报文检测）实时反诈预警系统。系统实时接收银行交易日志流，经 Flink 增量特征计算、规则引擎与逻辑回归模型双重评分，输出三态决策（阻断 / 预警 / 放行），并推送到 Web 仪表盘实时展示。

## 技术栈

| 层级 | 技术 |
|------|------|
| 数据采集 | 日志模拟器（80 条/s，200 用户，3% 欺诈率） |
| 消息队列 | Apache Kafka 3.9.0 |
| 流处理 | Apache Flink 1.18（ZengKuai 增量特征，17 维） |
| 应用服务 | Spring Boot 3.2（Java 17） |
| 决策引擎 | 22 条业务规则 + SMILE 逻辑回归（ML） |
| 存储 | MySQL 8.0（持久化）+ Redis（封号/拦截名单缓存） |
| 前端 | Vue 3 + Pinia + Element Plus + ECharts |
| 推送 | 原生 WebSocket + REST API |

## 系统架构

```
模拟器(80条/s) → Kafka(dpi_logs, 4分区)
       ↓
Flink 1.18 (LocalEnv)
  └─ ZengKuaiEnrichFunction：时间分桶(15m/60m/1440m) → 17 维特征
       ↓
Kafka(dpi_enriched, 4分区)
       ↓
Spring Boot 3.2
  ├─ Kafka Consumer
  ├─ 规则引擎（22 条）+ ML 评分（SMILE 逻辑回归）
  ├─ 决策融合：riskScore = 0.4×规则 + 0.6×ML
  ├─ 三态决策：block(≥0.65) / warn(≥0.45) / allow(<0.45)
  └─ 写入 MySQL + Redis，WebSocket(/ws/logs) 实时推送
       ↓
Vue 3 前端（8 页仪表盘，实时刷新）
```

## 目录结构

| 模块 | 路径 | 说明 |
|------|------|------|
| 模拟器 | `simulator/` | 银行 DPI 日志生成器，主类 `com.antifraud.simulator.Main` |
| Flink 作业 | `flink-job/` | 增量特征富化，主类 `com.antifraud.flink.DpiFraudDetectionJob` |
| 后端 | `backend/` | Spring Boot 服务，主类 `com.antifraud.Application` |
| 前端 | `frontend/` | Vue 3 仪表盘（8 页） |
| 原生引擎（可选） | `zengkuai/` | C++ 增量特征原生库，默认用纯 Java 实现，无需编译 |
| Kafka 配置 | `kafka-config/` | Topic 创建脚本 |
| 文档 | `docs/` | 架构设计、迁移 SQL |
| 启动脚本 | `*.bat` | 一键启动 / 停止（Windows） |

## 环境要求

- **JDK 17**
- **Maven 3.8+**
- **MySQL 8.0**（Windows 服务名 `MySQL80`）
- **Redis**（Windows 服务名 `Redis`，可用 Memurai 或 tporadowski/redis 安装）
- **Apache Kafka 3.9.0**（需解压到 `C:\kafka_2.13-3.9.0`，见下方「Kafka 路径」）
- **Node.js 18+**

> 本项目主要在 Windows 上开发与运行（启动脚本为 `.bat`）。Flink 以本地模式（LocalEnv）运行，无需独立 Flink 集群。

## 前置准备（环境安装，首次运行必读）

以下依赖需在首次运行前逐一安装配置，已具备某项可跳过。

### 1. JDK 17

- 下载 Eclipse Temurin（推荐，免费）：<https://adoptium.net/temurin/releases/?version=17>
- 配置环境变量：新建 `JAVA_HOME` 指向 JDK 安装目录，`PATH` 追加 `%JAVA_HOME%\bin`
- 验证：`java -version` 显示 `17`

### 2. Maven 3.8+

- 下载：<https://maven.apache.org/download.cgi>（Binary zip）
- 解压后 `PATH` 追加其 `bin` 目录
- 验证：`mvn -version`

### 3. Node.js 18+

- 下载：<https://nodejs.org/>（LTS 版本）
- 验证：`node -v`、`npm -v`

### 4. MySQL 8.0

- 下载安装包：<https://dev.mysql.com/downloads/installer/>
- 安装时设置 **root 密码**（记下，稍后填入 `MYSQL_PASSWORD`）
- 服务名保持默认 `MySQL80`（`start_core.bat` 依赖该服务名）
- 创建数据库（MySQL 命令行或 Navicat 等工具执行）：
  ```sql
  CREATE DATABASE dpi_fraud CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```

### 5. Redis（Windows）

Redis 官方不支持 Windows，二选一：

- **tporadowski/redis**（GitHub 高星 Windows 移植版）：<https://github.com/tporadowski/redis>
  ```bat
  redis-server.exe --service-install --service-name Redis
  net start Redis
  ```
- **Memurai**（兼容 Redis 的商业实现）：<https://www.memurai.com/>

> 服务名必须为 `Redis`（`start_core.bat` 通过 `net start Redis` 启动）。

### 6. Apache Kafka 3.9.0

- 下载：<https://kafka.apache.org/downloads> → 选择 **`kafka_2.13-3.9.0.tgz`**（Scala 2.13 版）
- 解压到 **`C:\kafka_2.13-3.9.0`**（`start_core.bat` 硬编码此路径，放别处需同步改脚本）
- 使用 Kafka 自带 ZooKeeper 模式，无需单独安装 ZooKeeper

### 7. 配置数据库密码环境变量

源码已移除明文密码，运行时从环境变量 `MYSQL_PASSWORD` 读取：

```bat
setx MYSQL_PASSWORD "你的MySQL root密码"
```

- 设置后需**重新打开终端**（或重启）才生效
- 验证：`echo %MYSQL_PASSWORD%`

### 8. 安装自检

| 检查项 | 命令 | 期望 |
|--------|------|------|
| Java | `java -version` | 17 |
| Maven | `mvn -version` | 3.8+ |
| Node | `node -v` | 18+ |
| MySQL 服务 | `sc query MySQL80` | STATE: RUNNING |
| Redis 服务 | `sc query Redis` | STATE: RUNNING |
| Kafka 目录 | `C:\kafka_2.13-3.9.0` 存在 | — |
| 密码变量 | `echo %MYSQL_PASSWORD%` | 显示密码 |

## 快速开始（Windows 一键启动）

> 前提：已完成上方「前置准备」。

1. **构建各模块**（首次运行前）：
   ```bat
   cd backend    && mvn clean package -DskipTests
   cd flink-job  && mvn clean package
   cd simulator  && mvn clean package
   cd frontend   && npm install
   ```

2. **创建提权计划任务**（只需一次，之后启停不再弹 UAC）：
   双击 `setup_tasks.bat`

3. **启动 / 停止全栈**：
   - 双击 `start.bat` —— 依次拉起 MySQL、Redis、ZooKeeper、Kafka、Flink、后端、前端、模拟器
   - 双击 `stop.bat` —— 依次关闭所有组件与数据库服务

4. 访问前端：<http://localhost:5173>

## 手动分步启动（排查问题用）

按顺序启动以下组件（每个都在独立终端窗口运行）：

```bat
:: 1. 启动 MySQL / Redis 服务
net start MySQL80
net start Redis

:: 2. 启动 ZooKeeper（Kafka 3.9 需在 C:\kafka_2.13-3.9.0 目录下执行）
bin\windows\zookeeper-server-start.bat config\zookeeper.properties

:: 3. 启动 Kafka
bin\windows\kafka-server-start.bat config\server.properties

:: 4. （可选）创建 4 分区 Topic
bash kafka-config\topic-config.sh

:: 5. 启动 Flink 作业（flink-job 目录下）
java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED ^
     -cp target\dpi-flink-job-1.0.0-shaded.jar com.antifraud.flink.DpiFraudDetectionJob

:: 6. 启动后端（backend 目录下）
java -jar target\dpi-fraud-backend-1.0.0.jar

:: 7. 启动前端（frontend 目录下）
npm run dev

:: 8. 启动模拟器（simulator 目录下）
java -cp target\dpi-simulator-1.0.0.jar com.antifraud.simulator.Main
```

## 配置说明

### 数据库密码（重要）

密码已从源码中移除，改为**环境变量 `MYSQL_PASSWORD`** 读取：

- `backend/src/main/resources/application.yml` 中：`password: ${MYSQL_PASSWORD:}`
- `clear.bat` 中：`mysql -uroot -p%MYSQL_PASSWORD%`

运行前请务必设置 `MYSQL_PASSWORD` 环境变量为你的 MySQL root 密码，否则后端将无法连接数据库。

### Kafka 路径

`start_core.bat` 中硬编码了 Kafka 安装路径 `C:\kafka_2.13-3.9.0`。请将 Kafka 解压到该路径，或修改 `start_core.bat` 中的对应路径。

### 端口一览

| 组件 | 端口 |
|------|------|
| MySQL | 3306 |
| Redis | 6379 |
| ZooKeeper | 2181 |
| Kafka | 9092 |
| 后端 | 8080 |
| 前端 | 5173 |

## ML 模型说明

逻辑回归模型序列化文件 `ml_model.ser` 未纳入版本库（体积较大）。若缺失，后端启动时会**自动回退到手写加权评分**，不影响系统运行；如需复现完整 ML 评分，请先运行训练生成该模型。

## 常用脚本

| 脚本 | 作用 |
|------|------|
| `setup_tasks.bat` | 创建 `SentinelStart` / `SentinelStop` 两个最高权限计划任务（首次运行一次） |
| `start.bat` | 启动全栈（非管理员时经计划任务提权，不弹 UAC） |
| `stop.bat` | 关闭全栈 |
| `clear.bat` | 清空日志/告警/拦截数据 + Redis 缓存（`FLUSHALL`） |

## 常见问题

- **后端启动后连不上数据库**：检查 `MYSQL_PASSWORD` 环境变量是否已设置且正确，数据库 `dpi_fraud` 是否已创建。
- **Kafka 启动报 `NodeExistsException`**：上次 Kafka 被强杀导致 ZooKeeper 残留 `/brokers/ids` 节点，`start_core.bat` 已内置自动清理；手动启动时可用 zookeeper-shell 执行 `deleteall /brokers/ids`。
- **双击 `start.bat` 无反应**：先运行一次 `setup_tasks.bat` 创建计划任务。
- **前端页面能开但数据为空**：确认 Kafka（9092）、Flink 作业、后端（8080）、模拟器依次正常启动。

## 项目文档

更详细的设计说明见 `docs/架构设计.md`，完整演示文稿见 `Sentinel3.0-反诈预警PPT-单文件版.html`。
