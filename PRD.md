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

## 7. Server‑Side Conversation History

**Goal** – Enable multi‑turn chats without the client having to resend the full message list. The Spring API will keep a per‑conversation message buffer identified by a `conversationId`.

### API changes
- **POST `/chat`** – optional field `conversationId`.
  - The client sends only the new user message.
  - If `conversationId` is omitted, the request behaves as before (stateless).
  - If `conversationId` is provided, the server loads the stored conversation, appends the new user message, forwards the *entire* history to Ollama, then stores the assistant reply too.
- **GET `/conversations/{conversationId}`** – returns the stored message history (useful for debugging or UI).
- **DELETE `/conversations/{conversationId}`** – clears a conversation, allowing a fresh start.

### Storage (simple implementation)
Create a Spring singleton bean `ConversationService` that holds a
```java
private final ConcurrentHashMap<String, List<Message>> store = new ConcurrentHashMap<>();
```
`Message` mirrors the Ollama chat schema (`role`, `content`). The bean offers `appendUserMessage(id, Message)`, `appendAssistantMessage(id, Message)`, `getMessages(id)`, and `clear(id)`.

### Configuration
- `app.conversations.max-history-messages` — configurable max buffer size per conversation. Default: `1000` messages.
- `app.conversations.ttl` — configurable expiration time for inactive conversations. Default: `24h`.

### Conversation flow
1. Client sends `POST /chat` with `conversationId="abc123"` and a new user message.
2. Service fetches existing list (or creates a new one), appends the user message, sends the full list to Ollama, receives assistant reply, appends it, and returns the assistant message to the client.
3. Subsequent calls with the same `conversationId` automatically carry the accumulated context.

### Security & Cleanup
- Validate `conversationId` (UUID format recommended).
- Add a TTL using a scheduled task that removes stale entries based on `app.conversations.ttl`.
- Optionally replace the in-memory map with an embedded H2 table for persistence across restarts.
- Keep the history bounded by `app.conversations.max-history-messages` so old messages are trimmed before sending to Ollama.

### Impact on existing clients
No breaking change: existing calls that omit `conversationId` continue to work statelessly. Clients that want stateful chats simply add the field.

### Documentation updates
- Extend the PRD `Endpoint Design` section with the new endpoints and request/response schemas.
- Add a diagram showing the conversation buffer between the API and Ollama.

---

## 8. Conversation Naming & Listing

**Goal** – Automatically name conversations based on their content so clients can display a friendly title in a sidebar or list. Also expose an endpoint to list all active conversations with their names.

### API changes

#### `GET /conversations` (NEW)
Returns all active conversations with their metadata (id, name, message count, last activity).

**Response (200 OK):**
```json
{
  "conversations": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Index fund investment advice",
      "messageCount": 4,
      "updatedAt": "2026-06-21T19:30:00Z"
    },
    {
      "id": "661e8400-e29b-41d4-a716-446655440001",
      "name": "Debugging Spring Boot configuration",
      "messageCount": 12,
      "updatedAt": "2026-06-21T20:15:00Z"
    }
  ]
}
```

#### `POST /chat` — auto-naming on first user message
When a conversation is **new** (no prior history), after returning the assistant reply to the client, the server sends a **background** request to Ollama to generate a short title:

```
System: "Generate a concise title (max 6 words) for this conversation based on the user's first message. Respond with only the title, no quotes or punctuation."
User:   "<first user prompt>"
```

The generated name is stored alongside the conversation metadata. This happens **asynchronously** so it does not add latency to the chat response.

Existing calls (stateless, no `conversationId`) are unaffected — no naming is triggered.

#### `POST /conversations/{conversationId}/rename` (NEW)
Regenerates the conversation name by sending the **full message history** to Ollama and asking for a more accurate summary title.

**Response (200 OK):**
```json
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Updated title based on full conversation"
}
```

#### `GET /conversations/{conversationId}` — add name to response
Current response is extended to include the conversation name:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Index fund investment advice",
  "messages": [ ... ]
}
```

#### `ChatResponse` — add name field
The `ChatResponse` now includes the conversation name (nullable for stateless chats):

```json
{
  "model": "llama3:latest",
  "message": { "role": "assistant", "content": "..." },
  "done": true,
  "conversationId": "550e8400-...",
  "conversationName": "Index fund investment advice",
  "response": "..."
}
```

### Storage changes

Extend `ConversationState` in `ConversationService` with a `name` field:

```java
private static final class ConversationState {
    private final List<OllamaMessage> messages = new ArrayList<>();
    private Instant updatedAt = Instant.now();
    private String name; // null until generated
    private boolean namingInProgress; // true while async naming is running
}
```

Add a `ConversationSummary` record for the list endpoint:

```java
public record ConversationSummary(
    String id,
    String name,
    int messageCount,
    Instant updatedAt
) {}
```

New methods on `ConversationService`:
- `setName(String conversationId, String name)` — sets the name
- `getName(String conversationId)` — returns the name or null
- `listConversations()` — returns `List<ConversationSummary>` of all non-expired conversations

### Naming prompt

The prompt used for title generation should be configurable via `application.yml`:

```yaml
app:
  conversations:
    naming-prompt: "Generate a concise title (max 6 words) for this conversation based on the user's first message. Respond with only the title, no quotes or punctuation."
    naming-model: "llama3.2:3b"  # lightweight model for naming
```

Using a smaller/faster model for naming (e.g., `llama3.2:3b`) keeps costs low and avoids blocking the main chat model.

### Flow diagram

```
Client                          Server                          Ollama
  |                                |                               |
  |-- POST /chat (prompt="...") -->|                               |
  |                                |-- Check if new conversation   |
  |                                |-- Store user message          |
  |                                |-- /api/chat (full history) -->|
  |                                |<-- assistant reply ---------- |
  |                                |-- Store assistant reply       |
  |<-- ChatResponse (200) --------|                               |
  |                                |                               |
  |                                |  (async, if first message)    |
  |                                |-- /api/chat (naming prompt)->|
  |                                |<-- "Title" ------------------ |
  |                                |-- store conversation name     |
```

### Impact on existing clients

- **Non-breaking**: Existing clients that call `POST /chat` continue to work — the `conversationName` field is simply `null` until generated. The async naming does not add latency.
- `GET /conversations/{id}` response shape changes (wraps messages in an object with `id` and `name` fields). This is a **minor breaking change** if clients consume this endpoint.
- `GET /conversations` is new — no impact.
- `POST /conversations/{id}/rename` is new — no impact.

### Implementation steps
1. Add `name` field to `ConversationState` and create `ConversationSummary` record.
2. Add `setName`, `getName`, `listConversations` methods to `ConversationService`.
3. In `OllamaService.chat()`, after storing the assistant reply, detect if this is a new conversation and fire an async `@Async` method to generate the name.
4. Add `POST /conversations/{id}/rename` and `GET /conversations` endpoints to `OllamaController`.
5. Update `GET /conversations/{id}` response to include id/name wrapper.
6. Add `conversationName` field to `ChatResponse`.
7. Add `app.conversations.naming-prompt` and `app.conversations.naming-model` config.
8. Add tests for async naming, rename endpoint, and listing.

### Concurrency & race conditions

#### Async naming race

If a second `POST /chat` arrives **before** the async naming call completes, the following could happen:

1. **Duplicate naming calls**: Two separate async naming requests could be fired. The name should only be generated once.
   - **Mitigation**: Use an atomic `compareAndSet`-style flag (`namingInProgress`) on `ConversationState` to ensure only one async naming call is ever launched per conversation. The flag is set to `true` before firing the async call and reset to `false` after the name is stored. The `chat()` method checks `namingInProgress` before scheduling a new naming task.

2. **Stale name**: The auto-name is generated from the first message, but by the time the async call completes, several more messages may have been exchanged. The name might already feel outdated.
   - **Mitigation**: This is acceptable — the auto-name is meant as a quick initial label. The client (or user) can call `POST /conversations/{id}/rename` at any time to regenerate a more accurate name from the full history.

#### Rename vs auto-naming race

If the user triggers `POST /conversations/{id}/rename` while the background auto-naming is still in flight:

- The rename should take precedence and overwrite whatever the auto-naming eventually returns.
- **Mitigation**: Both operations call the same `setName()` method, which is synchronized on `ConversationState`. The rename simply wins by being last. The auto-naming checks `namingInProgress` before writing, but the rename endpoint does not set this flag, so it always succeeds. `namingInProgress` is only used to prevent duplicate **auto-naming** calls.

#### General multi-request concurrency on the same conversation

Multiple concurrent `POST /chat` requests for the same conversation must not interleave or corrupt message order.

**Current state**: `ConversationService` already uses `synchronized (state)` blocks around all read/write operations on the messages list, which guarantees thread safety at the message level.

**Remaining concern**: The "is this conversation new?" check in `OllamaService.chat()` is not inside the synchronized block, so two concurrent requests for a brand-new conversation could both see an empty history and both trigger auto-naming.
- **Mitigation**: The `namingInProgress` flag (see above) already addresses this — the first request sets it to `true`, the second sees it and skips scheduling. Both requests will produce the correct assistant reply independently; they just race on the name generation flag, which is harmless.

#### Summary of thread-safety guarantees
| Scenario | Safe? | Mechanism |
|---|---|---|
| Two simultaneous POST /chat, same conversation | ✅ | `synchronized` on `ConversationState` prevents message corruption |
| Two simultaneous POST /chat, brand-new conversation | ✅ | `namingInProgress` atomic flag prevents double auto-naming |
| Rename during auto-naming | ✅ | Synchronized `setName()`; rename always wins |
| Stateless chat (no conversationId) | ✅ | No shared state |
| Expired conversation cleanup during active chat | ✅ | Synchronized blocks and `ConcurrentHashMap` iteration safety |

### Implementation steps
1. Add `name` field to `ConversationState` and create `ConversationSummary` record.
2. Add `setName`, `getName`, `listConversations` methods to `ConversationService`.
3. In `OllamaService.chat()`, after storing the assistant reply, detect if this is a new conversation and fire an async `@Async` method to generate the name (guarded by `namingInProgress` flag).
4. Add `POST /conversations/{id}/rename` and `GET /conversations` endpoints to `OllamaController`.
5. Update `GET /conversations/{id}` response to include id/name wrapper.
6. Add `conversationName` field to `ChatResponse`.
7. Add `app.conversations.naming-prompt` and `app.conversations.naming-model` config.
8. Add tests for async naming, rename endpoint, concurrency, and listing.

*End of proposal*
