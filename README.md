# Sentinel 3.0 - 反诈预警系统

## 📖 项目简介
本项目是一个基于多技术栈的分布式反诈预警系统，包含实时日志采集、流处理、机器学习模型推理以及前端可视化展示。

## 🛠️ 技术栈
- 前端：Vue 3 + Vite + ECharts
- 后端：Spring Boot + WebSocket
- 大数据：Apache Flink + Kafka
- 底层库：C++ (JNI Bridge)
- 机器学习：Java ML 模型推理

## 🚀 快速启动指南

### 1. 环境准备
- 预先启动 **Kafka**（参考 `kafka-config` 目录创建 Topic）
- 预先启动 **Redis**（用于缓存与会话）
- 预先启动 **MySQL 8.0**，并创建数据库 `dpi_fraud`
- 设置环境变量 `MYSQL_PASSWORD`（MySQL root 密码）

### 2. 前端启动
```bash
cd frontend
npm install
npm run dev
```

### 3. 后端启动
```bash
cd backend && mvn clean install     # 主类 com.antifraud.Application
cd simulator && mvn clean install   # 主类 com.antifraud.simulator.Main
cd flink-job && mvn clean install   # 主类 com.antifraud.flink.DpiFraudDetectionJob
```

### 4. 一键启动脚本
可直接运行根目录下的 `start.bat`（首次请先运行一次 `setup_tasks.bat` 创建提权计划任务）。

## ⚠️ 大模型文件说明 (LFS)
本项目的机器学习模型文件（`ml_model.ser`、`training_data.ser`）体积较大，已通过 **Git LFS** 进行版本管理。
拉取代码时请确保本地已安装 Git LFS（`git lfs install`），`git clone` 后模型会自动下载到对应目录（`backend/` 和 `backend/src/main/resources/`）。

## 📂 目录结构
- `backend/` 主后端服务
- `frontend/` Vue 前端
- `flink-job/` 实时流处理任务
- `producer/` 模拟日志生产者
- `simulator/` 仿真数据生成器
- `zengkuai/` C++ 底层 JNI 库
- `docs/` 项目文档与迁移脚本
