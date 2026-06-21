# PRD: Centralized AI Orchestrator API (Ollama Proxy)

## 1. Overview
The AI Orchestrator is a local REST API that acts as an abstraction layer and centralized gateway between home network applications (such as invest-track or future apps) and the Ollama service (which runs LLM models locally).

### Why an intermediate API instead of calling Ollama directly?
- **Prompt Management:** Centralizes "System Prompts" (model personality instructions).
- **History and Logs:** Allows saving in a local database how many tokens are spent and what questions are asked of the AI.
- **Security and Control:** If you decide to switch from Ollama to OpenAI or Anthropic in the future, your client applications won't have to change a single line of code; you'll only update this orchestrator.

## 2. System Objectives
- **Model Management:** List, download (pull), and delete Ollama models from any web client.
- **Unified Inference:** Provide simple endpoints for chat.
- **Streaming Support:** Allow word-by-word responses (ChatGPT style) via Server-Sent Events (SSE).

## 3. Data Flow Architecture
Network traffic will be structured as follows:

```
[ Mobile / Web Device ] 
           │ (http://apps.home/ollama/api/chat)
           ▼
   [ Nginx (WSL2) ] 
           │ (Proxy pass to the port of your new API, e.g., 5000)
           ▼
[ Your New AI API (ollama-proxy) ] 
           │ (Internal call via Ollama's native port: 11434)
           ▼
    [ Ollama Engine ] ──► [ Local Models (Llama3, Mistral, Phi3...) ]
```

## 4. Endpoint Design (API Specification)
Considering that Nginx will strip the `/ollama/api/` prefix, your application will internally listen to the following structured routes:

### 🧠 Block 1: Inference (AI Calls)
#### POST `/chat`
Sends a conversation flow (message history) to maintain context.

**Body (JSON):**
```json
{
  "model": "llama3.8b",
  "messages": [
    { "role": "system", "content": "You are an expert financial advisor." },
    { "role": "user", "content": "Is it a good time to buy index funds?" }
  ],
  "stream": false
}
```

**Response (200 OK):**
```json
{
  "model": "llama3.8b",
  "message": { "role": "assistant", "content": "It depends on your time horizon, but DCA into index funds..." },
  "done": true
}
```



### 🛠️ Block 2: Model Management (Maintenance)
#### GET `/models`
Returns the list of models already downloaded and ready to use.

**Response (200 OK):**
```json
{
  "models": [
    { "name": "llama3:latest", "size": "4.7GB", "family": "llama" },
    { "name": "phi3:mini", "size": "2.2GB", "family": "phi" }
  ]
}
```

#### POST `/models/pull`
Downloads a new model from the official Ollama registry. This should be asynchronous or stream progress.

**Body (JSON):** 
```json
{
  "model": "gemma2"
}
```

**Response (22 Accepted):** 
```json
{
  "status": "Downloading gemma2..."
}
```

#### DELETE `/models/{name}`
Deletes a model from disk to free up space.

### 🏥 Block 3: System
#### GET `/health`
Verifies if the API is operational and checks if the native Ollama process is responding.

## 5. Nginx Configuration (invest.conf)
To integrate this new API, add an extra location block within your Nginx server:

```nginx
server {
    listen 80;
    server_name apps.home;

    # ... Existing invest-track locations remain UNCHANGED ...

    # 4. NEW CENTRALIZED OLLAMA API
    location /ollama/api/ {
        proxy_pass http://127.0.0.1:5000/; # Trailing '/' strips '/ollama/api/'
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        # Ollama can take time to respond
        proxy_read_timeout 300s;
        proxy_connect_timeout 300s;

        # Required for streaming (SSE)
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        chunked_transfer_encoding off;
        proxy_buffering off;
    }
}
```

## 6. Implementation Next Steps
1. Install Ollama (Windows/WSL) and download a lightweight model (e.g., `phi3` or `llama3:8b`).
2. Create the microservice.
3. Decide on language/framework (e.g., Python with FastAPI or Node.js).
