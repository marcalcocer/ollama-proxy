#!/bin/bash

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

echo -e "${BLUE}==================================================================${NC}"
echo -e "${BLUE} 🤖 Bootstrapping Ollama Proxy Environment...${NC}"
echo -e "${BLUE}==================================================================${NC}"

# 1. Mount Nginx Snippet Configuration
echo -e "${YELLOW}⚙️  Configuring Nginx Route Injection...${NC}"
NGINX_SNIPPET_DIR="/etc/nginx/apps.home.d"
sudo mkdir -p "$NGINX_SNIPPET_DIR"

if [ -f "$SCRIPT_DIR/nginx/ollama.conf" ]; then
    sudo cp "$SCRIPT_DIR/nginx/ollama.conf" "$NGINX_SNIPPET_DIR/ollama.conf"
    if sudo nginx -t &>/dev/null; then
        sudo systemctl reload nginx
        echo -e "${GREEN}✅ Nginx snippet registered and reloaded successfully.${NC}"
    else
        echo -e "${RED}❌ Nginx snippet syntax test failed!${NC}"
        sudo nginx -t
        exit 1
    fi
fi

# 2. Setup Gradle wrapper if not initialized
if [ ! -f "$SCRIPT_DIR/gradlew" ]; then
    echo -e "${YELLOW}☕ Initializing Gradle Environment Wrapper...${NC}"
    gradle wrapper --gradle-version 8.5
fi
chmod +x "$SCRIPT_DIR/gradlew"

# 3. Start Application
echo -e "${YELLOW}🟢 Starting Ollama Proxy Microservice (Spring Boot)...${NC}"
echo -e "${BLUE}   📝 Logs: logs/app.log${NC}"
./gradlew bootRun > "$LOG_DIR/app.log" 2>&1 &
APP_PID=$!

function cleanup {
    echo -e "\n${RED}🛑 Stopping Ollama Proxy service...${NC}"
    [ -n "$APP_PID" ] && kill $APP_PID 2>/dev/null
    exit
}
trap cleanup SIGINT SIGTERM

# 4. Wait for Upstream Health Check
echo -e "${YELLOW}⏳ Waiting for Spring Boot to bind port 5000...${NC}"
while true; do
    if ! kill -0 $APP_PID 2>/dev/null; then
        echo -e "\n${RED}❌ Application engine crashed during boot execution. Check logs/app.log${NC}"
        exit 1
    fi
    
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:5000/health)
    if [ "$STATUS" == "200" ]; then
        echo -e "\n${GREEN}✅ AI Gateway is UP and responding!${NC}"
        break
    fi
    echo -n "."
    sleep 2
done

echo -e "${BLUE}==================================================================${NC}"
echo -e "${GREEN} 🌍 AI Hub Entrypoint: http://apps.home/ollama/api/health${NC}"
echo -e "${BLUE}==================================================================${NC}"

wait $APP_PID
