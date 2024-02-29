### 常用命令
```
#查看进程
ps -ef | grep nginx

# 查看配置文件
cat /usr/local/nginx/conf/nginx.conf

# 检查配置
/usr/local/nginx/sbin/nginx -t
# /usr/local/nginx/sbin/nginx -t -c /usr/local/nginx/conf/nginx.conf

# nginx修改配置后重载
/usr/local/nginx/sbin/nginx -s reload

# 启动
/usr/local/nginx/sbin/nginx -c /usr/local/nginx/conf/nginx.conf

# 从容停止服务：进程完成当前工作后再停止。
/usr/local/nginx/sbin/nginx -s quit

# 立即停止服务：无论进程是否在工作都直接停止进程。
/usr/local/nginx/sbin/nginx -s stop

# nginx启动/重启/停止 （未配置-此命令暂无效）
# sudo /etc/init.d/nginx {start|restart|stop}
```


### 1、安装（基于CentOS7.6）
下载地址： https://nginx.org/download/nginx-1.22.1.tar.gz
```
# 解压
tar -zxvf nginx-1.22.1.tar.gz
cd nginx-1.22.1

# 配置 -- 需要SSL
./configure --prefix=/usr/local/nginx --with-http_stub_status_module --with-http_ssl_module

# 编译安装
make
make install

# 启动
/usr/local/nginx/sbin/nginx -c /usr/local/nginx/conf/nginx.conf

# 重载配置
/usr/local/nginx/sbin/nginx -s reload
```

### 2、反向代理

#### 2.1、将请求转发给指定服务器
```
# eg: `http://127.0.0.1:80/api/time` -> `http://127.0.0.1:8082/api/time`
# 将`/api/`后面的路径直接拼接到`http://127.0.0.1:8082/api/`后面
location /api/ {
    proxy_pass   http://127.0.0.1:8082/api/;
}
```

```
# eg: `http://127.0.0.1:80/api/time` -> `http://127.0.0.1:8082/time`
# `^~ /api/`表示匹配前缀是api的请求，会把`/api/`后面的路径直接拼接到`http://127.0.0.1:8082/`后面
location ^~ /api/ {
    proxy_pass   http://127.0.0.1:8082/;
}
```

#### 2.2 访问本地图片资源
```
# eg: 域名/file/test.png  -> /home/soft/file/test.png
location ^~ /file/ {
    alias  /home/soft/file/;
}
```

#### 2.3 请求头丢失问题
Nginx 默认会忽略掉带有下划线的请求头。eg: TENANT_ID 可以通过设置 underscores_in_headers 来解决。
通过设置相应的代理请求头参数，将客户端的请求转发到后端服务器，并同时传递一些关键的请求头信息。
```
http {
  underscores_in_headers on;     # 允许在HTTP请求头中使用下划线（underscore）字符
  server {
    proxy_set_header Host $host;  # 设置代理请求头中的主机名（Host）为客户端请求的主机名
    proxy_set_header X-Real-IP $remote_addr; # 将客户端的真实 IP 地址作为 X-Real-IP 请求头发送给后端服务器
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; # 在请求头中添加 X-Forwarded-For 字段，用于追踪经过的代理服务器地址
    proxy_set_header User-Agent $http_user_agent; # 将客户端的 User-Agent 请求头字段传递给后端服务器
  }
}
```

### 3、负载均衡
