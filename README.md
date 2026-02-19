# Media-Publish-API

自媒体平台一键代发布系统 — 支持 B站 / 抖音 / 小红书

## 项目概述

本项目提供统一的后端 API，实现自媒体平台的一键视频/图文代发布功能。用户通过 Web 应用登录各平台账号、编辑内容后，调用 API 即可完成投稿。

**当前迭代：Bilibili（B站）视频发布**

## 系统架构

```
┌──────────┐     HTTP      ┌──────────────────┐     HTTP      ┌─────────────────┐
│          │  ──────────►  │                  │  ──────────►  │                 │
│  前端 /  │               │  Java API 服务    │               │  biliup 容器     │
│  Postman │  ◄──────────  │  (Spring Boot)   │  ◄──────────  │  (port 19159)   │
│          │               │  port 8080       │               │                 │
└──────────┘               └────────┬─────────┘               └────────┬────────┘
                                    │                                  │
                                    │ JPA                              │ Volume
                                    ▼                                  ▼
                           ┌──────────────────┐               ┌─────────────────┐
                           │  MySQL 8.0       │               │  共享视频存储     │
                           │  port 3306       │               │  /data/videos    │
                           └──────────────────┘               └─────────────────┘
```

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2.x |
| 构建工具 | Maven | 3.9+ |
| 数据库 | MySQL | 8.0 |
| ORM | Spring Data JPA | - |
| HTTP 客户端 | RestTemplate | - |
| 加密 | AES-256-GCM | - |
| 容器化 | Docker + Docker Compose | - |
| 视频上传引擎 | biliup-rs (官方) | latest |

## 项目结构

```
Hui/
├── README.md                          # 项目文档
├── prompt.txt                         # 需求文档
├── bilibili/                          # B站发布模块（Spring Boot 微服务）
│   ├── pom.xml
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── src/
│       ├── main/
│       │   ├── java/com/hui/bilibili/
│       │   │   ├── BilibiliApplication.java
│       │   │   ├── config/
│       │   │   │   ├── BiliupConfig.java
│       │   │   │   ├── EncryptionConfig.java
│       │   │   │   └── RestTemplateConfig.java
│       │   │   ├── controller/
│       │   │   │   └── BilibiliController.java
│       │   │   ├── service/
│       │   │   │   ├── QrCodeLoginService.java
│       │   │   │   ├── VideoUploadService.java
│       │   │   │   └── CookieStorageService.java
│       │   │   ├── client/
│       │   │   │   └── BiliupClient.java
│       │   │   ├── model/
│       │   │   │   ├── dto/
│       │   │   │   │   ├── ApiResult.java
│       │   │   │   │   ├── QrCodeResponse.java
│       │   │   │   │   ├── LoginStatusResponse.java
│       │   │   │   │   ├── UploadRequest.java
│       │   │   │   │   ├── UploadResponse.java
│       │   │   │   │   ├── PublishRequest.java
│       │   │   │   │   └── UserInfoResponse.java
│       │   │   │   └── entity/
│       │   │   │       └── BiliCookie.java
│       │   │   ├── repository/
│       │   │   │   └── BiliCookieRepository.java
│       │   │   └── util/
│       │   │       └── AesEncryptUtil.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/
│       │           └── schema.sql
│       └── test/
│           └── java/com/hui/bilibili/
│               └── BilibiliApplicationTests.java
├── douyin/                            # 抖音模块（待开发）
├── rednote/                           # 小红书模块（待开发）
└── others/                            # 其他平台（待开发）
```

---

## Bilibili API 设计

### 基础信息

- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`（除文件上传外）
- **统一响应格式**:

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

### 错误码

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录 / Cookie 过期 |
| 500 | 服务器内部错误 |
| 502 | biliup 服务不可用 |

---

### 1. 获取登录二维码

**`GET /api/bilibili/qrcode`**

获取 B站登录二维码，前端展示供用户扫码。

**请求参数**: 无

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "qrcodeUrl": "https://passport.bilibili.com/x/passport-login/web/qrcode/generate?...",
    "qrcodeKey": "a1b2c3d4e5f6..."
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| qrcodeUrl | String | 二维码图片 URL，前端生成二维码展示 |
| qrcodeKey | String | 登录密钥，用于后续轮询登录状态 |

---

### 2. 轮询登录状态

**`POST /api/bilibili/qrcode/poll`**

轮询检查用户是否已扫码登录。建议前端每 2 秒调用一次。

**请求体**:

```json
{
  "qrcodeKey": "a1b2c3d4e5f6..."
}
```

**响应示例（未扫码）**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "WAITING",
    "message": "等待扫码"
  }
}
```

**响应示例（已扫码，待确认）**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "SCANNED",
    "message": "已扫码，等待确认"
  }
}
```

**响应示例（登录成功）**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "CONFIRMED",
    "message": "登录成功",
    "userId": "12345678",
    "username": "用户名"
  }
}
```

**响应示例（二维码过期）**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "EXPIRED",
    "message": "二维码已过期，请重新获取"
  }
}
```

| status | 说明 |
|--------|------|
| WAITING | 等待扫码 |
| SCANNED | 已扫码，未确认 |
| CONFIRMED | 登录成功 |
| EXPIRED | 二维码已过期 |

---

### 3. 获取用户信息

**`GET /api/bilibili/user/info`**

获取已登录 B站用户的基本信息。

**请求参数**:

| 参数 | 位置 | 必填 | 说明 |
|------|------|------|------|
| userId | Query | 是 | B站用户ID |

**请求示例**:

```
GET /api/bilibili/user/info?userId=12345678
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "mid": 12345678,
    "name": "用户名",
    "face": "https://i0.hdslb.com/bfs/face/xxx.jpg",
    "level": 5,
    "vipStatus": 1
  }
}
```

---

### 4. 上传视频文件

**`POST /api/bilibili/upload`**

上传视频文件到服务器（存储到共享 volume），返回服务端文件路径供后续发布使用。

**Content-Type**: `multipart/form-data`

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 视频文件 |
| cover | MultipartFile | 否 | 封面图片 |

**请求示例（Postman）**:

```
POST /api/bilibili/upload
Content-Type: multipart/form-data

file: [选择视频文件]
cover: [选择封面图片]（可选）
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "videoPath": "/data/videos/abc123_video.mp4",
    "coverPath": "/data/videos/abc123_cover.jpg",
    "fileName": "my_video.mp4",
    "fileSize": 104857600
  }
}
```

---

### 5. 发布/投稿视频

**`POST /api/bilibili/publish`**

调用 biliup 将已上传的视频发布到 B站。

**请求体**:

```json
{
  "userId": "12345678",
  "videoPath": "/data/videos/abc123_video.mp4",
  "coverPath": "/data/videos/abc123_cover.jpg",
  "title": "我的视频标题",
  "desc": "视频简介描述",
  "tag": "标签1,标签2,标签3",
  "tid": 17,
  "copyright": 1,
  "source": "",
  "dynamic": "动态内容",
  "dtime": 0,
  "dolby": 0,
  "openSubtitle": false,
  "upSelectionReply": false,
  "upCloseReply": false,
  "upCloseDanmu": false
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | String | 是 | B站用户ID（用于读取Cookie） |
| videoPath | String | 是 | 服务端视频文件路径 |
| coverPath | String | 否 | 服务端封面图片路径 |
| title | String | 是 | 视频标题（≤80字符） |
| desc | String | 是 | 视频简介 |
| tag | String | 是 | 标签，逗号分隔（≤12个） |
| tid | Integer | 是 | 分区 ID（如 17=单机游戏） |
| copyright | Integer | 是 | 1=自制，2=转载 |
| source | String | 转载必填 | 转载来源 URL |
| dynamic | String | 否 | 粉丝动态内容 |
| dtime | Long | 否 | 定时发布时间戳（10位，需>当前时间4h） |
| dolby | Integer | 否 | 杜比音效（0=关，1=开） |
| openSubtitle | Boolean | 否 | 是否开启字幕 |
| upSelectionReply | Boolean | 否 | 精选评论 |
| upCloseReply | Boolean | 否 | 关闭评论 |
| upCloseDanmu | Boolean | 否 | 关闭弹幕 |

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "cc36e47c-af5d-40e1-b149-d304a1c55d90",
    "status": "PROCESSING"
  }
}
```

---

### 6. 查询上传/发布状态

**`GET /api/bilibili/upload/status/{taskId}`**

查询视频上传任务的处理状态。

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| taskId | String | 发布接口返回的任务ID |

**响应示例（进行中）**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "cc36e47c-af5d-40e1-b149-d304a1c55d90",
    "status": "PROCESSING",
    "message": "视频上传中"
  }
}
```

**响应示例（已完成）**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "cc36e47c-af5d-40e1-b149-d304a1c55d90",
    "status": "COMPLETED",
    "message": "发布成功"
  }
}
```

**状态说明**:

| status | 说明 |
|--------|------|
| PROCESSING | 上传/处理中 |
| COMPLETED | 发布成功 |
| FAILED | 发布失败 |
| COOKIE_MISSING | Cookie 文件不存在 |
| COOKIE_EXPIRED | Cookie 已过期 |
| VIDEO_NOT_FOUND | 视频文件不存在 |
| TASK_NOT_FOUND | 任务不存在 |

---

## B站常用分区 ID

| tid | 分区名称 |
|-----|----------|
| 17 | 单机游戏 |
| 171 | 电子竞技 |
| 172 | 手机游戏 |
| 65 | 网络游戏 |
| 136 | 音乐综合 |
| 21 | 日常 |
| 138 | 搞笑 |
| 250 | 出行 |
| 160 | 生活经验 |
| 230 | 其他 |
| 95 | 数码 |
| 188 | 科普 |
| 36 | 知识分享 |

---

## Docker 部署

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+

### 快速启动

```bash
cd bilibili
docker-compose up -d
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| MYSQL_ROOT_PASSWORD | MySQL root 密码 | root123456 |
| MYSQL_DATABASE | 数据库名 | hui_bilibili |
| AES_SECRET_KEY | AES-256 加密密钥（32字节 Base64） | - |
| BILIUP_BASE_URL | biliup 服务地址 | http://biliup:19159 |
| VIDEO_STORAGE_PATH | 视频文件存储路径 | /data/videos |

### 服务端口

| 服务 | 端口 |
|------|------|
| bilibili-api | 8080 |
| biliup | 19159 |
| mysql | 3306 |

---

## Postman 测试流程

1. **获取二维码**: `GET http://localhost:8080/api/bilibili/qrcode`
2. **用手机 B站 App 扫码**
3. **轮询登录状态**: `POST http://localhost:8080/api/bilibili/qrcode/poll`（body: `{"qrcodeKey": "xxx"}`）
4. **获取用户信息**: `GET http://localhost:8080/api/bilibili/user/info?userId=xxx`
5. **上传视频**: `POST http://localhost:8080/api/bilibili/upload`（form-data: file）
6. **发布视频**: `POST http://localhost:8080/api/bilibili/publish`（JSON body）
7. **查询状态**: `GET http://localhost:8080/api/bilibili/upload/status/{taskId}`

---

## 开发计划

- [x] Bilibili 视频发布 API
- [ ] 抖音视频发布 API
- [ ] 小红书图文/视频发布 API
- [ ] 前端 Web 应用
- [ ] 统一账号管理
- [ ] 定时发布功能

## License

MIT
