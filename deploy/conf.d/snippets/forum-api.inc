# 与 forum-vue/front/vite.config.js 代理前缀保持一致
# 由 server { } 内 include；勿在此文件写 server/ upstream

# 同源代理下载阿里云 OSS 原图，绕过浏览器 CORS（仅 GET，且主机名须为 *.aliyuncs.com）
location ~ ^/oss-dl/(?<oss_dl_host>[^/]+)(?<oss_dl_uri>/.*)$ {
    limit_req zone=forum_public_read burst=20 nodelay;

    if ($request_method != GET) {
        return 405;
    }
    if ($oss_dl_host !~* \.aliyuncs\.com$) {
        return 403;
    }

    resolver 8.8.8.8 1.1.1.1 valid=300s ipv6=off;
    resolver_timeout 5s;
    proxy_http_version 1.1;
    proxy_ssl_server_name on;
    proxy_set_header Host $oss_dl_host;
    proxy_set_header Connection "";
    proxy_pass https://$oss_dl_host$oss_dl_uri$is_args$args;
    proxy_hide_header Set-Cookie;
    proxy_ignore_headers Set-Cookie;
    add_header Cache-Control "private, max-age=60";
}

# 生产环境屏蔽 OpenAPI / Swagger 文档泄露
location ~ ^/(v3/api-docs|v3/swagger-ui|swagger-ui|swagger)(/|$) {
    return 404;
}

# 登录 / 注册 / 验证码发送：更严限流
location = /user/login {
    limit_req zone=forum_auth burst=5 nodelay;
    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location = /user/register {
    limit_req zone=forum_auth burst=5 nodelay;
    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}


# 游客搜索、推荐和验证码校验均不需要登录，但必须分别限制资源消耗。
location ~ ^/captcha/(generate|check)$ {
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }

    limit_req zone=forum_captcha_challenge burst=3 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 64k;
}

location ~ ^/search/(article|user)$ {
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }

    limit_req zone=forum_public_search burst=5 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 16k;
}

location = /recommend/feed {
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }

    limit_req zone=forum_public_read burst=20 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 16k;
}

location /ws/ {
    proxy_pass http://forum_backend_ws;
    proxy_http_version 1.1;
    proxy_set_header Upgrade           $http_upgrade;
    proxy_set_header Connection        $connection_upgrade;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_buffering off;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
}

# 视频/大文件上传：压缩可能 10min+，单独拉长代理超时
location ~ ^/file/ {
    limit_req zone=forum_api burst=60 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 60s;
    proxy_send_timeout    3600s;
    proxy_read_timeout    3600s;
    client_max_body_size 350m;
}

# 歌曲上传：音频 ≤50MB，不走通用 /article 的 4m 限制
location = /article/music/upload {
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }

    limit_req zone=forum_api burst=20 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 60s;
    proxy_send_timeout    3600s;
    proxy_read_timeout    3600s;
    client_max_body_size 60m;
}

# 歌曲一键解析：同样可能上传 ≤50MB 音频读内嵌标签
location = /article/music/parse {
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }

    limit_req zone=forum_api burst=20 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 60s;
    proxy_send_timeout    3600s;
    proxy_read_timeout    3600s;
    client_max_body_size 60m;
}

# 歌曲裁剪：上传原文件 + 起止时间，经 FFmpeg 裁剪后返回
location = /article/music/trim {
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }

    limit_req zone=forum_api burst=20 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 60s;
    proxy_send_timeout    3600s;
    proxy_read_timeout    3600s;
    client_max_body_size 60m;
}

# 配乐 AI 推荐 / AI 搜索：LLM 推理可能较久
location = /article/music/recommend {
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }

    limit_req zone=forum_api burst=20 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 60s;
    proxy_send_timeout    3600s;
    proxy_read_timeout    3600s;
}

location = /article/music/ai-search {
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }

    limit_req zone=forum_api burst=20 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 60s;
    proxy_send_timeout    3600s;
    proxy_read_timeout    3600s;
}

# 看板娘流式对话：准备 MCP/RAG 可能较久，单独放宽读超时
location = /mascot/chat/stream {
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }

    limit_req zone=forum_api burst=60 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 30s;
    proxy_send_timeout    320s;
    proxy_read_timeout    320s;
    proxy_buffering off;
    client_max_body_size 2m;
}

location ~ ^/(user|board|article|articleQuestion|articleDanmaku|message|mail|sms|category|like|file|articleReply|articleSubReply|replyLike|checkin|shop|points|favorite|search|system-message|mascot|lottery|vip|game|profile|recommend|ai|notice|captcha|group-chat|gallery)(/|$) {
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }

    limit_req zone=forum_api burst=60 nodelay;

    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 30s;
    # AI 润色 / 标签推荐 / 配图可达约 300s，需略高于上游
    proxy_send_timeout    320s;
    proxy_read_timeout    320s;

    # 大文件上传仅走 file 接口；其余 API 不应在鉴权前接收大请求体。
    client_max_body_size 4m;
}
