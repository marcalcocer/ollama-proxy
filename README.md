# Ollama Proxy

A centralized AI Orchestrator API that acts as a gateway between home network applications and the local [Ollama](https://ollama.com/) service.

## 🚀 Overview

`ollama-proxy` provides a unified REST interface for local Large Language Models (LLMs). It simplifies model management, centralizes prompt engineering, and provides a future-proof abstraction layer for your AI-powered applications.

## ✨ Key Features

- **Unified Inference:** Simple endpoint for `/chat`.
- **Model Management:** List, download (pull), and delete models via API.
- **Streaming Support:** Real-time responses using Server-Sent Events (SSE).
- **Architecture Ready:** Designed to sit behind Nginx and handle long-running LLM requests.
- **Abstraction Layer:** Decouples client applications from the underlying AI engine.

## 🏗️ Architecture

```
[ Clients ] -> [ Nginx ] -> [ ollama-proxy (this app) ] -> [ Ollama Engine ]
```

## 📖 Documentation

For detailed requirements and API specifications, see the [PRD.md](./PRD.md).

## 🚦 Getting Started

### Prerequisites

- **Java 17+** installed
- **Ollama** running locally on port `11434` (default). Pull at least one model, e.g. `ollama pull llama3`

### Quick Start

```bash
./start-dev.sh
```

This script will automatically:

1. Install Ollama (if missing) and start the service
2. Set up Nginx route injection (if Nginx is installed)
3. Initialize the Gradle wrapper (if missing)
4. Build and start the server on port `5000`
5. Wait for the health check to confirm the app is ready

Once you see `✅ AI Gateway is UP and responding!`, the server is ready.

### Manual Start

```bash
# Build the project
./gradlew build

# Start the server (port 5000)
./gradlew bootRun
```

### Verify it's running

```bash
curl http://localhost:5000/health
# → {"status":"UP","service":"ollama-proxy"}
```

### Configuration

All settings are in `src/main/resources/application.yml`:

| Key | Default | Description |
|-----|---------|-------------|
| `server.port` | `5000` | HTTP port |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Ollama endpoint |
| `spring.ai.ollama.chat.options.model` | `llama3` | Default chat model |
| `app.conversations.max-history-messages` | `1000` | Max messages kept per conversation |
| `app.conversations.ttl` | `24h` | How long inactive conversations are retained |
| `app.conversations.naming-model` | `llama3.2:3b` | Lightweight model used for auto-naming |
| `app.conversations.naming-prompt` | *see yml* | Prompt template for title generation |

## 📡 API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check – returns service status. |
| `/chat` | POST | Chat inference – send `{"prompt":"your text", "model":"..."}`. If `conversationId` is omitted, the server auto-generates one and returns it. Reuse that ID on subsequent calls to continue the conversation. |
| `/conversations/{conversationId}` | GET | Fetch the stored message history for a conversation. |
| `/conversations/{conversationId}` | DELETE | Clear a stored conversation. |
| `/models` | GET | List locally available Ollama models. |
| `/models/pull` | POST | Pull a model from the registry. Payload: `{"model":"model_name","stream":false}`. |
| `/models/{name}` | DELETE | Delete a locally stored Ollama model. |

## Usage Examples

### Multi-turn conversation flow

The server manages conversation IDs for you. Start a conversation without an ID, grab it from the response, then reuse it for follow-ups.

#### 1. Start a conversation (server generates the ID)

```bash
RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" \
     -d '{"prompt":"What is 2+2?","model":"llama3:latest"}' \
     http://localhost:5000/chat)
echo "$RESPONSE"

# Extract the conversationId from the response
CONV_ID=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['conversationId'])")
```

Example response — note the `conversationId` is always returned:

```json
{"model":"llama3:latest","message":{"role":"assistant","content":"4"},"done":true,"conversationId":"a1b2c3d4-...","response":"4"}
```

#### 2. Continue the conversation (reuse the same conversationId)

```bash
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"prompt":"What was my first question?","model":"llama3:latest","conversationId":"'$CONV_ID'"}' \
     http://localhost:5000/chat -w "\n%{http_code}\n"
```

The model refers back to previous messages because the full history is forwarded to Ollama.

#### 3. Inspect stored history

```bash
curl -s http://localhost:5000/conversations/$CONV_ID -w "\n%{http_code}\n"
```

#### 4. Clear conversation when done

```bash
curl -s -X DELETE http://localhost:5000/conversations/$CONV_ID -w "\n%{http_code}\n"
```

> **Note:** You can also provide your own UUID if you prefer — just include it in the request as `"conversationId":"your-uuid"`. The server uses whatever you pass.

### Stateless chat (single turn)

Omit `conversationId` entirely and ignore it in the response if you don't need history:

```bash
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"prompt":"Hello in one word","model":"llama3:latest"}' \
     http://localhost:5000/chat -w "\n%{http_code}\n"
```

### Other endpoints

#### Health check
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:5000/health
```

#### List models
```bash
curl -s -X GET http://localhost:5000/models -w "\n%{http_code}\n"
```

#### Pull a model
```bash
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"model":"llama3:latest","stream":false}' \
     http://localhost:5000/models/pull -w "\n%{http_code}\n"
```

#### Delete a model
```bash
curl -s -X DELETE http://localhost:5000/models/llama3:latest -w "\n%{http_code}\n"
```

### Insomnia (GUI)

1. **Create a new request** → set method & URL as shown above.
2. **Add a JSON body** for POST endpoints (`/chat`, `/models/pull`).
3. **Send** and inspect the 200 response with the JSON payload.

## 🛠️ Tech Stack (Planned)

- **Language:** TBD (e.g., Python/FastAPI or Node.js)
- **Engine:** Ollama
- **Reverse Proxy:** Nginx
