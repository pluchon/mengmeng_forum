# Forum-Demo 论坛系统  

***

[toc]

***

一个基于 Spring Boot + MyBatis-Plus + MySQL 的论坛社区演示项目

> 本项目旨在展示一个完整的 Web 开发流程，从后端 API 设计到前端页面交互，涵盖了典型的论坛业务场景。

## 一、技术栈
### 1. 后端  
- **核心框架**: Spring Boot 3.x
- **ORM 框架**: MyBatis-Plus (集成分页插件)
- **身份校验**: JWT (JSON Web Token) 无状态鉴权
- **数据库**: MySQL 8.0
- **缓存/热帖**: Redis
- **消息中间件**: RabbitMQ (实现异步处理、负载均衡与伪集群)
- **实时推送**: WebSocket (全链路实时送达)
- **文件存储**: 阿里云 OSS

### 2. 前端 (Frontend)
- **UI 框架**: [Tabler](https://tabler.io/) (基于 Bootstrap 5)
- **动态交互**: jQuery, $.ajax
- **反馈插件**: jQuery Toast
- **底层动画**: Canvas 2D (用于高性能 3D 粒子海洋渲染)

## 二、项目核心功能 
### 1. 实时消息系统 (WebSocket + RabbitMQ)
项目已实现完整的实时消息闭环：
- **异步投递**: 私信回复后，后端通过 RabbitMQ 进行异步解耦处理，减轻主线程压力。
- **即时通知**: 集成 WebSocket 全双工通信。对方在线时，右下角会立即弹出即时消息提醒，顶部导航栏铃铛角标实时同步未读数。
- **聊天闭环**: 聊天对话框支持实时流式刷新，对方发送消息后无需刷新页面即可在气泡中看到内容，并自动触发已读状态更新。

### 2. 现代视觉特效 (UI/UX 升级)
为了提升用户体验，项目引入了极具视觉震撼力的特效方案：
- **首页全屏视差 Layer**: 首页 Banner 采用多层视差效果，背景元素会随鼠标移动或手机陀螺仪（重力感应）进行位移偏移。
- **3D 粒子海洋背景**: 登录/注册页采用基于 Canvas 的三维透视粒子海洋。点阵随多重正弦波干涉算法起伏，支持视角随鼠标位置/手机倾斜动态演变。
- **玻璃拟态风格**: 登录框体采用高通透度毛玻璃效果 (`backdrop-filter`)，搭配低饱和度淡彩色系，呈现商业级的清新审美。

### 3. 点赞与社交互动
- **双态切换**: 实现了帖子点赞/取消点赞的自动切换逻辑。
- **隐私保护**: 只有帖子作者本人可以查看“谁点赞了我的帖子”列表。
- **动态列表**: 支持查询用户个人点赞过的所有帖子，并提供分页加载支持。

### 4. 高效分页检索
- **物理分页**: 集成 MyBatis-Plus 分页拦截器，通过 `PageResult` 封装实现标准化的物理分页响应。
- **响应优化**: 提供了 `ArticleListByUserIdPageResponse` 等多维度 VO 模型，确保前端按需获取数据。

### 5. 嵌套回复系统 (楼中楼)
- **多级交互**: 告别单调的线性回复，实现了支持无限向下讨论的“楼中楼”回复结构。
- **目标艾特**: 支持点击某条子回复直接带入 `@目标用户` 标识进行精准互动。
- **动态懒加载**: 每个楼层的子回复独立进行分页控制，只有点击展开时才拉取数据，大幅优化详情页的加载性能。

### 6. 现代化帖子详情交互
- **响应式排版**: 重新划分了作者信息与帖子正文的网格比例，大屏幕下阅读视距更加聚焦，小屏设备自动优雅折叠。
- **沉浸式编辑器**: 帖子底部引入了支持“左侧编写、右侧所见即所得”实时预览模式的 Markdown 回复区。

### 7. 项目结构
> 后端目录结构
```text
├─common
│  ├─advice      (WebSocket、消息处理逻辑)
│  ├─config      (RabbitMQ、MyBatis-Plus 分页、线程池配置)
│  ├─constant
│  ├─enums
│  ├─exception
│  ├─interceptor (JWT 鉴权拦截器、缓存控制拦截器)
│  ├─result      (通用结果封装)
│  └─utils       (JWT 工具类等)
├─controller     (用户、帖子、点赞、消息控制器)
├─entry/entity
│  ├─db          (数据库映射实体 PO)
│  ├─dto         (业务传输对象)
│  └─vo          (前端响应展示对象)
├─mapper
└─service
```

## 三、项目运行环境
- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- **RabbitMQ 3.9+** (须开启延迟消息插件支持)

## 四、项目演示
### 1. 登录与注册界面 (3D 粒子交互)

> ![image-20260407005732603](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260407005732900.png)采用 3D 粒子海洋波浪，跟踪鼠标指针，会随鼠标移动！

### 2. 首页 (全屏视差)
> ![image-20260407005826714](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260407005826899.png)顶层 Banner 随动视差效果

### 3. 实时聊天
> 支持 WebSocket 实时响应与消息未读数更新
> ![image-20260407005913823](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260407005913918.png)
>
> ![image-20260407005944258](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260407005944389.png)
>
> ![image-20260407010019243](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260407010019401.png)
>
> ![image-20260407010045811](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260407010045899.png)

### 4. 个人信息界面

![image-20260312102058427](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260312102058739.png)

> 对于头像是自己单独修改
> 除开密码后都是自己原子化修改
> 密码是单独修改，因为涉及到后端的盐值和密码重置

### 5. 帖子详情页 (现代化排版)  

![image-20260312102435468](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260312102447365.png)

> ![image-20260418185939819](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260418185939950.png)
>
> ![image-20260418190015905](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260418190015968.png)
>
> 只有当前登录用户自己发布的帖子才能进行修改编辑操作
> 下面回复区域可以对指定用户进行私信等等
> ......

### 6. 用户信息界面  

![image-20260312102609575](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260312102609783.png)

> 会展示用户的详细信息，如果是我们自己会显示你自己

### 7. 私信列表

![image-20260312103714936](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260312103715009.png)

> 会显示你有几个消息未读，如果你点进了这个项目，会自动把对方发来的消息都设为已读

### 8. 聊天会话

![image-20260312103433390](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260312103433534.png)

### 9. 嵌套回复 (楼中楼) 与互动

> ![image-20260418190040200](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260418190040282.png)

## 五、开源协议

[MIT License](LICENSE)
