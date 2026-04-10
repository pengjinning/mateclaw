<div align="center">

<p align="center">
  <img src="mateclaw-ui/public/logo/mateclaw_logo_s.png" alt="MateClaw Logo" width="120">
</p>

# MateClaw

<p align="center"><b>让 AI 真正去思考、行动、记忆，并把结果交付出来。</b></p>

[![GitHub 仓库](https://img.shields.io/badge/GitHub-仓库-black.svg?logo=github)](https://github.com/matevip/mateclaw)
[![文档](https://img.shields.io/badge/文档-在线-green.svg?logo=readthedocs&label=Docs)](https://claw.mate.vip/docs)
[![在线演示](https://img.shields.io/badge/演示-在线-orange.svg?logo=vercel&label=Demo)](https://claw-demo.mate.vip)
[![官网](https://img.shields.io/badge/官网-claw.mate.vip-blue.svg?logo=googlechrome&label=Site)](https://claw.mate.vip)
[![Java 版本](https://img.shields.io/badge/Java-17+-blue.svg?logo=openjdk&label=Java)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D.svg?logo=vuedotjs)](https://vuejs.org/)
[![最后提交](https://img.shields.io/github/last-commit/matevip/mateclaw)](https://github.com/matevip/mateclaw)
[![许可证](https://img.shields.io/badge/license-Apache--2.0-red.svg?logo=opensourceinitiative&label=License)](LICENSE)

[[官网](https://claw.mate.vip)] [[在线演示](https://claw-demo.mate.vip)] [[文档](https://claw.mate.vip/docs)] [[English](README.md)]

</div>

<p align="center">
  <img src="assets/images/preview.png" alt="MateClaw 预览" width="800">
</p>

---

MateClaw 是一个基于 **Java + Vue 3** 构建的个人 AI 操作系统，由 [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba) 驱动。

不是又一个聊天框。这是一个完整的 AI 工作系统——智能体能推理、用工具、构建记忆、把原始资料消化成 Wiki、创作多模态内容，还能出现在每一个真正发生工作的渠道里。

三件事让它与众不同：

1. **智能体做事，不只聊天** — ReAct 循环推理 + 计划执行，完成真正的任务
2. **知识被塑造，而非仅仅被存储** — LLM 驱动的 Wiki 把原始资料消化成结构化的链接页面
3. **端到端整合** — 一个团队、一次部署，从桌面端到 IM 渠道的完整体验控制

---

## 架构全景

<p align="center">
  <img src="assets/architecture-biz-zh.svg" alt="业务架构" width="800">
</p>

<details>
<summary><b>技术架构</b></summary>
<p align="center">
  <img src="assets/architecture-tech-zh.svg" alt="技术架构" width="800">
</p>
</details>

---

## 核心能力

### 智能体引擎

- **ReAct 智能体** — 思考、行动、观察、循环。迭代推理直到完成任务
- **计划-执行智能体** — 将复杂工作分解为有序步骤，逐一执行
- **动态配置** — 运行时从数据库加载智能体的人格、工具和约束
- **运行时韧性** — 上下文裁剪、智能截断、僵死流清理、异常恢复

### 知识与记忆

- **LLM Wiki 知识库** — AI 驱动的知识库，将原始资料消化为结构化、有链接的页面
- **工作区记忆** — `AGENTS.md`、`SOUL.md`、`PROFILE.md`、`MEMORY.md`、每日笔记
- **记忆生命周期** — 对话后自动提取、定时整理、记忆涌现工作流
- **记忆应该积累** — 理解随时间加深，而非每次查询都从零开始

### 工具、技能与 MCP

- **内置工具** — 联网搜索、文件操作、记忆访问、日期时间等
- **MCP 集成** — 支持 stdio、SSE、Streamable HTTP 三种传输
- **技能系统** — 可安装的 `SKILL.md` 技能包 + ClawHub 市场
- **工具防护** — 审批流、文件路径保护、运行时过滤

### 多模态创作

语音合成 · 语音识别 · 图片生成 · 音乐生成 · 视频生成

### 模型灵活性

14+ 供应商支持，包括 DashScope、OpenAI、Anthropic、Gemini、DeepSeek、Kimi、Ollama、LM Studio、MLX 等。在 Web 界面中配置一切。

### 用户触点

- **Web 控制台** — 对话、智能体、工具、技能、知识、模型、安全、设置
- **桌面端** — Electron + 内嵌 JRE 21，无需安装 Java
- **多渠道** — 钉钉、飞书、企业微信、Telegram、Discord、QQ

---

## 快速开始

### 环境要求

- Java 17+ · Node.js 18+ · pnpm · Maven 3.9+

### 本地开发

```bash
# 后端
cd mateclaw-server
mvn spring-boot:run          # http://localhost:18088

# 前端
cd mateclaw-ui
pnpm install && pnpm dev     # http://localhost:5173
```

默认登录：`admin` / `admin123`

### Docker 部署

```bash
cp .env.example .env
docker compose up -d          # http://localhost:18080
```

### 桌面端

从 [GitHub Releases](https://github.com/matevip/mateclaw/releases) 下载安装包。内嵌 JRE 21，无需额外安装 Java。

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端 | Spring Boot 3.5 · Spring AI Alibaba 1.1 |
| 智能体 | StateGraph 运行时 |
| 数据库 | H2（开发）/ MySQL 8.0+（生产）|
| ORM | MyBatis Plus 3.5 |
| 认证 | Spring Security + JWT |
| 前端 | Vue 3 · TypeScript · Vite |
| UI | Element Plus · TailwindCSS 4 |
| 桌面端 | Electron · electron-updater |

---

## 项目结构

```
mateclaw/
├── mateclaw-server/     Spring Boot 后端
├── mateclaw-ui/         Vue 3 SPA 前端
├── mateclaw-desktop/    Electron 桌面端
├── docker-compose.yml
└── .env.example
```

---

## 文档

完整文档请访问 **[claw.mate.vip/docs](https://claw.mate.vip/docs)**

---

## 路线图

- 更丰富的多智能体协作
- 更智能的模型路由
- 更深度的多模态理解
- 更强的长期记忆
- 更丰富的 ClawHub 生态

---

## 参与贡献

```bash
git clone https://github.com/matevip/mateclaw.git
cd mateclaw
cd mateclaw-server && mvn clean compile
cd ../mateclaw-ui && pnpm install && pnpm dev
```

---

## 为什么叫这个名字

**Mate** 是陪伴。**Claw** 是能力。

一个陪在你身边的系统，一个能真正抓住工作、推动它前进的系统。

---

## 许可证

[Apache License 2.0](LICENSE)
