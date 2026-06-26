# 与 forum-vue/front/vite.config.js 代理前缀保持一致
# 由 server { } 内 include；勿在此文件写 server/ upstream

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


location ~ ^/mail/send {
    limit_req zone=forum_captcha_send burst=3 nodelay;
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }
    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location ~ ^/sms/send {
    limit_req zone=forum_captcha_send burst=3 nodelay;
    error_page 418 = @forum_spa_fallback;
    if ($proxy_forum_api = 0) {
        return 418;
    }
    proxy_pass http://forum_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection        "";
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
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
    proxy_send_timeout    200s;
    proxy_read_timeout    200s;
    proxy_buffering off;
    client_max_body_size 350m;
}

location ~ ^/(user|board|article|articleDanmaku|message|mail|sms|category|like|file|articleReply|articleSubReply|replyLike|checkin|shop|points|favorite|search|system-message|mascot|lottery|vip|game|ai|notice|captcha)(/|$) {
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
    proxy_send_timeout    120s;
    proxy_read_timeout    120s;

    # 大文件上传（封面 / 聊天图）走 file 接口
    # 视频帖上传可能超过100MB，放开到 350MB（后端 multipart 配置需同步）
    client_max_body_size 350m;
}
