# TLS 证书（nuonuoya.cn）

生产环境当前使用：

| 文件 | 说明 |
|------|------|
| `www.nuonuoya.cn.pem` | 证书（或完整链） |
| `www.nuonuoya.cn.key` | 私钥 |

Nginx 配置见 `conf.d/20-prod-https.conf`。

**管理端** `admin.nuonuoya.cn` 使用同一组证书。若浏览器提示证书不匹配，请申请 **泛域名** `*.nuonuoya.cn` 或为 `admin` 单独签发后更新路径。
