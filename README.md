# Ollama Proxy

A centralized AI Orchestrator API that acts as a gateway between home network applications and the local [Ollama](https://ollama.com/) service.

## 🚀 Overview

`ollama-proxy` provides a unified REST interface for local Large Language Models (LLMs). It simplifies model management, centralizes prompt engineering, and provides a future-proof abstraction layer for your AI-powered applications.

## ✨ Key Features

- **Unified Inference:** Simple endpoints for `/chat`, `/generate`, and `/embeddings`.
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

## 🛠️ Tech Stack (Planned)

- **Language:** TBD (e.g., Python/FastAPI or Node.js)
- **Engine:** Ollama
- **Reverse Proxy:** Nginx
