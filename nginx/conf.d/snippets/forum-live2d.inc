# Live2D 模型静态资源（build-all 同步至 dist/user/live2d-assets）

location /live2d-assets/ {
    alias /usr/share/nginx/user/live2d-assets/;
    expires 30d;
    add_header Cache-Control "public, immutable";
    access_log off;
}
