# 萌萌论坛 · 管理后台

基于 Vue 3 + Vite + Arco Design 的论坛管理端，对接 `forum-demo` 后端。

## 开发

```bash
npm install
npm run dev
```

默认端口 `9527`（见 `.env.development`）。接口通过 Vite 代理转发至 `http://localhost:10086` 的 `/admin`、`/file`。

## 构建

```bash
npm run build
```

生产环境变量见 `.env.production`。

## 说明

- 用户端登录：`VITE_FRONT_SIGN_IN_URL`
- 设计稿参考：`src/reference/`（仅本地对照，不参与构建）
