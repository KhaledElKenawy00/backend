# Room Service

Discord-style **voice/video rooms** for Virtual Office (port **8086**). The backend is a *coordinator*: it owns rooms, membership, and live presence; delegates each room's **text chat** to the Chat Service over RabbitMQ; and assigns the **Agora channel name** clients join. Media is handled by Agora directly from the frontend (App-ID-only in v1 — no server-side tokens yet).

## Docs

| Doc | For |
|---|---|
| [`backend.md`](./backend.md) | Backend summary — architecture, data model, features, chat integration, tests, how to run |
| [`frontend.md`](./frontend.md) | Frontend integration guide — REST API, WebSocket, Agora handoff, error responses |
| [`Docs/room-service-arc.md`](../../Docs/room-service-arc.md) | Full architecture design |

## Quick start

Room Service brings up **no infra of its own**. Start order: **user-service** (RabbitMQ) → **chat-service** (MongoDB + Redis) → **room-service**.

```bash
export JAVA_HOME=/path/to/jdk21       # Windows: $env:JAVA_HOME = "C:\path\to\jdk21"
./mvnw spring-boot:run                # Windows: mvnw.cmd spring-boot:run
```

Health: `GET http://localhost:8086/api/rooms/health` → `OK`.

## Tests

```bash
./mvnw test                       # 80 unit tests
./mvnw test -Dtest=AllTestsSuite  # run all from a single suite class
```
