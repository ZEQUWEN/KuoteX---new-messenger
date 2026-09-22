#!/bin/bash
set -e

# Скрипт развертывания Nginx в качестве Reverse Proxy для Bot API

DOMAIN="api.kuotex.msg"
BACKEND_PORT=8000

echo "Installing Nginx and Certbot..."
sudo apt-get update
sudo apt-get install -y nginx certbot python3-certbot-nginx

echo "Configuring Nginx..."
cat <<EOF | sudo tee /etc/nginx/sites-available/$DOMAIN
server {
    listen 80;
    server_name $DOMAIN;

    location / {
        proxy_pass http://127.0.0.1:$BACKEND_PORT;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

# Включение сайта
sudo ln -sf /etc/nginx/sites-available/$DOMAIN /etc/nginx/sites-enabled/

# Проверка синтаксиса Nginx
sudo nginx -t

# Перезапуск Nginx
sudo systemctl restart nginx

echo "Obtaining SSL certificate via Certbot (Let's Encrypt)..."
# Автоматическое получение и настройка SSL сертификатов
sudo certbot --nginx -d $DOMAIN --non-interactive --agree-tos -m admin@kuotex.msg

echo "Deployment complete! Reverse proxy is listening on https://$DOMAIN"
