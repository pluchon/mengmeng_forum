# 静态 SPA：$forum_spa_root 由 server 块 set 后 include

# API 前缀与前端路由冲突时（如 GET /article/create + Accept: text/html），由 forum-api.inc return 418 落入此处
location @forum_spa_fallback {
    root $forum_spa_root;
    try_files /index.html =404;
}

location = /index.html {
    root $forum_spa_root;
    add_header Cache-Control "no-cache, no-store, must-revalidate";
    add_header Pragma "no-cache";
    add_header Expires "0";
}

location ~ ^/(sign-in|sign-up|forgot-password|settings|message)(/|$) {
    root $forum_spa_root;
    add_header X-Robots-Tag "noindex, nofollow";
    try_files /index.html =404;
}

location / {
    root $forum_spa_root;
    index index.html;
    try_files $uri $uri/ /index.html;
}

location ~* \.(?:js|css|png|jpg|jpeg|gif|ico|woff2?|svg|webp|map)$ {
    root $forum_spa_root;
    expires 30d;
    add_header Cache-Control "public, immutable";
    access_log off;
}
