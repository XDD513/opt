# HTTPS 证书（Nginx）

前端容器内 Nginx 从 **`/etc/nginx/ssl/`** 读取：

| 文件 | 说明 |
|------|------|
| `fullchain.pem` | 完整证书链（公钥证书 + 中间 CA） |
| `privkey.pem` | 私钥 |

对应宿主机目录：`deploy/nginx/ssl/`（由 `docker-compose.yml` 挂载）。

**未放置有效 PEM 时，Nginx 将无法启动（443 配置依赖这两个文件）。**

## 1. 正式环境（Let’s Encrypt 等）

在已有域名且 **80/443 可从公网访问** 的前提下，用 certbot 等申请证书后，将：

- `fullchain.pem` ← 服务商提供的 fullchain / fullchain.pem  
- `privkey.pem` ← privkey.pem  

放到本目录，并修改 `deploy/nginx/conf.d/yggaame.cn.conf` 里的 **`server_name`** 为你的域名，与证书 CN/SAN 一致。

## 2. 内网 / 仅有 IP：自签名（浏览器会提示不安全，仅测试）

在 **`deploy/nginx/ssl/`** 下执行（需安装 OpenSSL；Windows 可用 Git Bash / WSL）：

```bash
# 将 121.43.140.75 换成你的服务器 IP 或内网穿透域名
openssl req -x509 -nodes -days 825 -newkey rsa:2048 \
  -keyout privkey.pem -out fullchain.pem \
  -subj "/CN=121.43.140.75" \
  -addext "subjectAltName=IP:121.43.140.75,DNS:localhost,IP:127.0.0.1"
```

或用仓库脚本：

```bash
chmod +x gen-selfsigned.sh
./gen-selfsigned.sh 121.43.140.75
```

然后在浏览器用 **`https://121.43.140.75`** 访问（需手动信任或忽略警告）。

## 3. 仅想继续用 HTTP

暂时不要用带 443 的配置：可将 `conf.d/yggaame.cn.conf` 中 **`server { listen 443 ... }` 整段删除**，并在 `docker-compose.yml` 的 `frontend` 服务里去掉 **`443:443`** 与 **`./nginx/ssl:/etc/nginx/ssl:ro`** 挂载。

## 4. HTTP 强制跳转 HTTPS（可选）

在 `yggaame.cn.conf` 中，把原有的 **`listen 80` 的 server** 替换为仅重定向（注意先确保证书可用）：

```nginx
server {
    listen 80;
    server_name 121.43.140.75;
    return 301 https://$host$request_uri;
}
```

并将 `location` 等配置只保留在 **`listen 443`** 的 server 中（可继续通过 `include /etc/nginx/snippets/locations.conf;` 引用）。
