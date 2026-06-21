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

# 2. Setup Ollama (Local WSL2 or Windows Host fallback)
OLLAMA_API_URL="http://localhost:11434"
INSTALL_LOG="$LOG_DIR/ollama-install.log"

if grep -q "microsoft" /proc/version; then
    # Check if ollama is installed in WSL2
    if ! command -v ollama &>/dev/null; then
        echo -e "${YELLOW}📦 Ollama is not installed in WSL2. Starting installation...${NC}"
        echo -e "${BLUE}   📝 Installation Logs: logs/ollama-install.log${NC}"
        
        # Check and install zstd if missing
        if ! command -v zstd &>/dev/null; then
            echo -e "${YELLOW}⚙️  zstd is required for extraction. Installing zstd...${NC}"
            sudo apt-get update >> "$INSTALL_LOG" 2>&1
            sudo apt-get install -y zstd >> "$INSTALL_LOG" 2>&1
        fi
        
        # Run official installer
        curl -fsSL https://ollama.com/install.sh 2>> "$INSTALL_LOG" | sh >> "$INSTALL_LOG" 2>&1
    fi

    # Start the local Ollama service in WSL2
    echo -e "${YELLOW}🟢 Starting Ollama service in WSL2...${NC}"
    sudo systemctl start ollama
    
    # Verify if WSL2 Ollama is responding (wait up to 10 seconds)
    echo -e "${YELLOW}⏳ Waiting for WSL2 Ollama service to bind to port 11434...${NC}"
    LOCAL_UP=false
    for i in {1..10}; do
        if curl -s -m 2 http://localhost:11434 &>/dev/null; then
            LOCAL_UP=true
            break
        fi
        echo -n "."
        sleep 1
    done
    echo ""
    
    if [ "$LOCAL_UP" = true ]; then
        echo -e "${GREEN}✅ Local WSL2 Ollama is running.${NC}"
        OLLAMA_API_URL="http://localhost:11434"
    else
        echo -e "${YELLOW}⚠️  Local WSL2 Ollama did not respond. Trying Windows host fallback...${NC}"
        # Set environment variable permanently for the user on Windows host
        powershell.exe -Command "[System.Environment]::SetEnvironmentVariable('OLLAMA_HOST', '0.0.0.0', 'User')"
        
        # Restart Ollama on Windows host
        powershell.exe -Command "
            Stop-Process -Name 'ollama*' -Force -ErrorAction SilentlyContinue;
            \$appPath = \"\$env:LOCALAPPDATA\Programs\Ollama\ollama app.exe\";
            if (Test-Path \$appPath) {
                Start-Process -FilePath \$appPath
            } elseif (Get-Command ollama -ErrorAction SilentlyContinue) {
                Start-Process -FilePath 'ollama' -ArgumentList 'serve' -WindowStyle Hidden
            } else {
                Write-Host 'WARNING: Ollama was not found on the Windows host either.' -ForegroundColor Yellow
            }
        "
        OLLAMA_API_URL="http://172.21.144.1:11434"
    fi
else
    # Non-WSL Linux: Just start the local service
    if ! command -v ollama &>/dev/null; then
        echo -e "${YELLOW}📦 Ollama is not installed. Installing...${NC}"
        echo -e "${BLUE}   📝 Installation Logs: logs/ollama-install.log${NC}"
        if ! command -v zstd &>/dev/null; then
            sudo apt-get update >> "$INSTALL_LOG" 2>&1
            sudo apt-get install -y zstd >> "$INSTALL_LOG" 2>&1
        fi
        curl -fsSL https://ollama.com/install.sh 2>> "$INSTALL_LOG" | sh >> "$INSTALL_LOG" 2>&1
    fi
    sudo systemctl start ollama
    OLLAMA_API_URL="http://localhost:11434"
fi

# Export environment variable for Spring Boot
export SPRING_AI_OLLAMA_BASE_URL="$OLLAMA_API_URL"
echo -e "${BLUE}🔗 Connecting proxy to Ollama engine at: $OLLAMA_API_URL${NC}"

# 3. Setup Gradle wrapper if not initialized
if [ ! -f "$SCRIPT_DIR/gradlew" ]; then
    echo -e "${YELLOW}☕ Initializing Gradle Environment Wrapper...${NC}"
    gradle wrapper --gradle-version 8.5
fi
chmod +x "$SCRIPT_DIR/gradlew"
 
# 4. Start Application
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
 
# 5. Wait for Upstream Health Check
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
