# 与 forum-vue/front/vite.config.js 代理前缀保持一致（用户端 + 管理端共用）
# 由 server { } 内 include；勿在此文件写 server/ upstream

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

location ~ ^/(user|board|article|message|mail|sms|category|like|file|articleReply|articleSubReply|checkin|shop|points|favorite|search|system-message|mascot|lottery|vip|ai|notice|captcha|admin)(/|$) {
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
    client_max_body_size 30m;
}
