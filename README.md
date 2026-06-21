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

## 📡 API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check – returns service status. |
| `/chat` | POST | Chat inference – send `{"prompt":"your text"}` and receive model reply. |
| `/models` | GET | List locally available Ollama models. |
| `/models/pull` | POST | Pull a model from the registry. Payload: `{"model":"model_name","stream":false}`. |
| `/models/{name}` | DELETE | Delete a locally stored Ollama model. |

## Usage Examples

### curl

#### Health check
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:5000/health
```

#### Chat
```bash
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"prompt":"Hello"}' \
     http://localhost:5000/chat -w "\n%{http_code}\n"
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
