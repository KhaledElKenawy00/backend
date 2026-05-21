# Workspace Service — Design Document

> The workspace-service is the **core spatial management service**. It owns the concepts of
> Workspace (the virtual office), Desk (a user's seat inside that office), and the 2D Layout
> that ties everything together spatially.

---

## Entities

### 1. Workspace

A **Workspace** represents a single virtual office — one per company/team.

| # | Field | Description |
|---|-------|-------------|
| 1 | Name | Display name of the workspace (e.g. "Acme Corp HQ") |
| 2 | URL slug | Unique slug → `https://<slug>.virtual.office` |
| 3 | Owner ID | Reference to user-service |
| 4 | Description | Short description / tagline |
| 5 | Logo | Company logo URL |
| 6 | 2D Layout Map | JSON document defining the spatial floorplan (walls, zones, furniture). Intentional JSON — spatial map data is hierarchical and queried as a whole, never column-by-column. |
| 7 | Status | `WorkspaceStatus` enum: `ACTIVE`, `ARCHIVED`, `SUSPENDED` |
| 8 | Visibility | `INVITE_ONLY` — access is granted exclusively via invitation; non-members cannot discover or request entry |
| 9 | Invite Token | Shareable invite link token (UUID); rotatable by admin; used by `WorkspaceInvitation` to validate join links |
| 10 | Default Timezone | Workspace-wide default timezone |
| 11 | Created At | Timestamp |
| 12 | Updated At | Timestamp |

> **Relationships (not columns):** `Desks`, `Members`, and `Rooms` are derived via FK lookups in the `Desk` table and room-service — they are not stored columns on `Workspace`.

---

### 2. Desk (Workspace Membership)

A **Desk** represents a user's presence/seat inside a workspace.  
Think of it as a **membership record** that also carries the user's workspace-specific profile and spatial position.

> Mental model: `User` (from user-service) + `Desk` (from workspace-service) = a person sitting in a specific office.

| # | Field | Description |
|---|-------|-------------|
| 1 | User ID | Reference to user-service |
| 2 | Workspace ID | Which workspace this desk belongs to |
| 3 | Full Name | Display name in this workspace (defaults to user's name) |
| 4 | Nick Name | Short name (defaults to Full Name) |
| 5 | Title | Job role/title within this company (e.g. "Senior Engineer") |
| 6 | Work Email | Workspace-specific email (defaults to user's primary email) |
| 7 | Phone | Work phone number |
| 8 | Personal Image | Profile photo URL (stored in object storage; URL saved here) |
| 9 | Avatar Character | `AvatarCharacter` enum — character skin for the 2D sprite (e.g. `ADAM`, `ASH`, `LUCY`, `NANCY`). Sprites are bundled in the SkyOffice client; backend stores the key only. |
| 10 | Timezone | User's timezone (defaults to workspace default) |
| 11 | Status | `DeskStatus` enum + optional custom text: `ACTIVE`, `AWAY`, `DO_NOT_DISTURB`, `FOCUS_MODE`, `CUSTOM` |
| 12 | Status Emoji | Optional emoji for custom status |
| 13 | Position X | X coordinate on the 2D workspace map |
| 14 | Position Y | Y coordinate on the 2D workspace map |
| 15 | Is Online | Cached presence flag — synced back from Colyseus on connect/disconnect. Not a source of truth; always verify against Last Seen At. |
| 16 | Last Seen At | Timestamp of last activity |
| 17 | Permissions / Role | `WorkspaceRole` enum: `OWNER`, `ADMIN`, `MEMBER`, `GUEST` |
| 18 | Desk Customization | JSON document — personal widgets and quick-access links. Intentional JSON — structure is user-defined and variable; never queried by individual key. |
| 19 | Bio | Short personal description shown in the desk overlay panel |
| 20 | Links | `Set<URL>` — social/profile URLs (stored as a separate one-column child table: `desk_id`, `url`) |
| 21 | Team ID | FK → `Team.ID` within this workspace |
| 22 | Invite Status | `InviteStatus` enum: `PENDING`, `ACCEPTED` — desk record is created on invite, accepted on join |
| 23 | Invited By | User ID of the workspace member who sent the invitation |
| 24 | Is Active | Soft-delete flag — set to `false` when a member is removed; preserves history |
| 25 | Joined At | When the user accepted their invitation and joined the workspace |

> **Links** are stored as a `Set<URL>` in a minimal child table (`desk_id`, `url`) — no platform label, no JSON blob.

---

### 3. MapObject (Interactive Asset)

A **MapObject** is a persistent interactive element placed on the 2D map — computers (screen-sharing stations) and whiteboards (collaborative drawing surfaces). These are owned by workspace-service so the real-time layer (SkyOffice/Colyseus) can load the workspace's full object configuration on boot rather than hard-coding it.

> Mental model: MapObjects are the "furniture with a function" on the map. Their position and IDs are stored here; who is currently using them is tracked ephemerally by the Colyseus server.

| # | Field | Description |
|---|-------|-------------|
| 1 | ID | Stable unique identifier (used as the Colyseus key for this object) |
| 2 | Workspace ID | Which workspace this object belongs to |
| 3 | Type | `MapObjectType` enum — current values: `COMPUTER`, `WHITEBOARD` |
| 4 | Label | Human-readable name (e.g. "Whiteboard A", "Shared Screen 1") |
| 5 | Position X | X coordinate on the 2D map |
| 6 | Position Y | Y coordinate on the 2D map |
| 7 | Room ID | Stable Colyseus room ID for this object's session — used by both `COMPUTER` (screen-sharing room) and `WHITEBOARD` (collaborative drawing room). Must persist so users can rejoin an in-progress session. |
| 8 | Capacity | Max concurrent users allowed to connect to this object at once |
| 9 | Is Active | Soft-disable — hides the object from the map without removing it |
| 10 | Created At | Timestamp |
| 11 | Updated At | Timestamp |

> **Ephemeral state NOT stored here:** which user is currently connected to a computer/whiteboard is managed in-memory by Colyseus and is lost when the session ends. Only the object's identity and position are persistent.

---

### 4. WorkspaceInvitation

A **WorkspaceInvitation** tracks the lifecycle of an invitation sent to a user to join a workspace. A `Desk` record (with `Invite Status = PENDING`) is created alongside the invitation; on acceptance, the desk is activated and the invitation is marked `ACCEPTED`.

| # | Field | Description |
|---|-------|-------------|
| 1 | ID | — |
| 2 | Workspace ID | FK → `Workspace.ID` |
| 3 | Invited Email | Email address of the invitee (user may not exist yet in user-service) |
| 4 | Invited By | FK → User ID in user-service |
| 5 | Token | Unique UUID token embedded in the invite link; validated on join |
| 6 | Role | Role to assign on acceptance: `MEMBER`, `GUEST`, etc. |
| 7 | Status | `InviteStatus` enum: `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED` |
| 8 | Expires At | Timestamp after which the token is no longer valid |
| 9 | Created At | Timestamp |

---

### 5. Team

A **Team** is a named group within a workspace (e.g. "Engineering", "Design", "Marketing"). Desks reference a Team to enable directory filtering and org-level grouping.

| # | Field | Description |
|---|-------|-------------|
| 1 | ID | — |
| 2 | Workspace ID | FK → `Workspace.ID` — teams are scoped to a workspace |
| 3 | Name | Team display name (e.g. "Engineering") |
| 4 | Description | Optional short description |
| 5 | Created At | Timestamp |
| 6 | Updated At | Timestamp |

---


---

## User Stories

### Workspace Lifecycle

**As a User (workspace creator):**
- I want to **create a new workspace** by choosing a name and unique URL slug, so my team gets a dedicated virtual office.
- I want to **upload a logo and set branding** (colors, theme), so the workspace looks professional and feels like our company.
- I want to **set a description**, so new members know what this workspace is about.
- I want to **delete or archive a workspace** I own, so I can clean up workspaces that are no longer needed.

**As a Workspace Admin:**
- I want to **invite users** via email or link, so they can join the workspace and get a desk assigned.
- I want to **remove a member** from the workspace, revoking their access and freeing their desk.
- I want to **assign roles** (Admin, Member, Guest), so different people have different levels of control.
- I want to **set workspace-wide settings** (default timezone, feature toggles), so the office operates consistently.
- I want to **view a members directory** showing everyone's name, title, status, and role at a glance.

---

### 2D Layout & Spatial Map

**As a Workspace Admin:**
- I want to **design/edit the 2D layout** (add walls, define zones, place furniture/assets), so the virtual office mirrors our ideal physical layout.
- I want to **create zones** labeled as "Meeting Room", "Focus Area", "Social Lounge", or "Team Zone", so members understand the purpose of each area.
- I want to **place desks on the map** at specific coordinates, so each member has a designated spot.
- I want to **rearrange the layout** over time without breaking existing desk assignments, so the office can evolve.

**As a Team Member:**
- I want to **see the full 2D map when I enter the workspace**, so I can immediately visualize where everyone is.
- I want to **see other members' avatars on the map in real time**, so I know who is at their desk, in a meeting room, or in a social area.
- I want to **move my avatar freely** across the map, so I can approach colleagues or enter different zones.
- I want to **see zone boundaries and labels** on the map, so I know where meeting rooms, focus areas, and social spaces are.

---

### Desk & Personal Work Area

**As a Team Member:**
- I want to **see my personal desk** as a home base when I enter the workspace — with my name, title, status, and quick links visible.
- I want to **customize my desk area** with personal items, widgets, and quick-access links, so it reflects my workflow.
- I want to **update my display name, avatar, and title** for this specific workspace, independently from my global profile.
- I want to **see my assigned tasks** linked to my desk (via tasks-service), so my desk acts as a personal productivity hub.

---

### Presence & Status

**As a Team Member:**
- I want to **set my status** to Active, Away, Do Not Disturb, Focus Mode, or a custom text + emoji, so others know my availability.
- I want my **status to auto-update** based on my activity (e.g., automatically go to "Away" after idle time, "In a Meeting" when in a room).
- I want to **see the real-time status of every colleague** on the map and in the members directory, so I can decide whether to approach them.
- I want my **"Do Not Disturb" / "Focus Mode"** to suppress notifications (via notification-service) and signal to others not to interrupt.

**As a Workspace Admin:**
- I want to **see an occupancy overview** — who is online, who is away, who is in DND — so I can understand team engagement at a glance.

---

### Proximity & Spatial Interaction

**As a Team Member:**
- I want to **walk up to a colleague's avatar** on the map to initiate a quick voice/video conversation (proximity-based — handled by room-service / voice layer), just like stopping by someone's desk in a real office.
- I want **voice/video to get louder/clearer as I move closer** to someone, and fade as I move away, mimicking natural spatial audio.
- I want **rooms to act as private zones** — when I enter a meeting room on the map, only people inside can hear/see me.
- I want to **see who is in a room before entering**, so I know if a meeting is in progress.

> **Note:** The actual voice/video/spatial-audio engine is handled by **room-service** and the real-time layer, but **workspace-service** provides the spatial coordinates, zone definitions, and proximity data that drives these interactions.

---

### Real-Time Layer (SkyOffice / Colyseus) Integration

SkyOffice is a Node.js/Colyseus multiplayer server that manages the live session on top of the persistent data owned by workspace-service. The two layers are complementary: workspace-service is the source of truth for configuration; Colyseus is the source of truth for live state.

#### What Colyseus loads from workspace-service at room boot

When a Colyseus room is created for a workspace, it calls workspace-service to fetch:

| Data | workspace-service field |
|------|------------------------|
| Workspace name, status, invite token | `Workspace` entity |
| All MapObjects (computers, whiteboards) and their positions | `MapObject` entity |
| Member list and their desk positions + avatar characters | `Desk` entity |

#### What Colyseus manages ephemerally (NOT persisted)

These exist only in Colyseus memory and are lost when the session ends:

| Ephemeral state | Colyseus field |
|-----------------|----------------|
| Player's current x, y position | `Player.x / Player.y` |
| Avatar animation state | `Player.anim` (e.g. `idle_right`, `run_left`) |
| Proximity call readiness | `Player.readyToConnect` |
| Video stream connected | `Player.videoConnected` |
| Who is currently using a computer | `Computer.connectedUser` |
| Who is currently using a whiteboard | `Whiteboard.connectedUser` |
| Room chat messages (session only) | `ChatMessage[]` — forwarded to `chat-service` for persistence |

#### Sync-back events (Colyseus → workspace-service)

Colyseus should notify workspace-service on these events:

| Event | Data written to workspace-service |
|-------|-----------------------------------|
| User connects to workspace | `Desk.Is Online = true`, `Desk.Last Seen At` |
| User disconnects | `Desk.Is Online = false`, `Desk.Last Seen At` |
| User updates status in-session | `Desk.Status`, `Desk.Status Emoji` |
| User moves to a new desk position (if permanent) | `Desk.Position X`, `Desk.Position Y` |

---

### Cross-Service Interactions

| Interaction | Services Involved | Description |
|---|---|---|
| **User joins workspace** | `user-service` → `workspace-service` | Validate user exists, create a Desk record |
| **Real-time session boots** | `SkyOffice/Colyseus` → `workspace-service` | Load workspace config, layout, MapObjects, and member desks |
| **Session presence sync** | `SkyOffice/Colyseus` → `workspace-service` | Push online/offline and status changes back to workspace-service |
| **Chat messages** | `SkyOffice/Colyseus` → `chat-service` | Room chat forwarded to chat-service for persistence; workspace-service provides the workspace context |
| **Desk shows tasks** | `workspace-service` ↔ `tasks-service` | Desk pulls tasks assigned to this user in this workspace |
| **Status affects notifications** | `workspace-service` → `notification-service` | When status = DND/Focus, notification-service suppresses delivery |
| **Entering a room** | `workspace-service` → `room-service` | Avatar moves into a room zone, room-service handles voice/video join |
| **Scheduling a room** | `calendar-service` → `workspace-service` | Calendar reserves a room; workspace-service validates the room exists on the layout |
| **Gateway routing** | `gateway-api` → `workspace-service` | All client requests go through the gateway |

---

### What workspace-service does NOT own

To keep boundaries clear:

- **Authentication & user identity** → `user-service`
- **Voice/Video calls & spatial audio** → `room-service` (workspace-service only provides spatial coordinates)
- **Chat messages & threads** → `chat-service`
- **Task creation & tracking** → `tasks-service`
- **Meeting scheduling** → `calendar-service`
- **Push notifications & emails** → `notification-service`
- **Live player positions & animation state** → `SkyOffice/Colyseus` (ephemeral, too frequent to persist)
- **Who is currently using a computer or whiteboard** → `SkyOffice/Colyseus` (ephemeral session state)

Workspace-service is the **spatial backbone**: it knows *where* everything is, *who* is in the office, and *what state* they're in. Other services consume this context to do their jobs.
