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
| 3 | Owner ID | The user who created the workspace (super-admin) |
| 4 | Description | Short description / tagline |
| 5 | Logo | Company logo URL |
| 6 | 2D Layout Map | JSON or structured data defining the spatial floorplan (walls, zones, furniture) |
| 7 | Rooms | List of configured rooms (meeting rooms, focus areas, social spaces) — managed by **room-service** but referenced here |
| 8 | Desks | Collection of desks placed on the 2D map |
| 9 | Members | List of users who have access (each represented by a Desk) |
| 10 | Theme & Branding | Colors, floor textures, wall styles, custom assets |
| 11 | Max Capacity | Maximum number of members allowed |
| 12 | Default Timezone | Workspace-wide default timezone |
| 13 | Global Settings | Default notification rules, workspace-wide permissions, feature toggles |
| 14 | Created At | Timestamp |
| 15 | Updated At | Timestamp |

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
| 8 | Personal Image | Profile photo URL |
| 9 | Avatar | 2D avatar used on the spatial map |
| 10 | Timezone | User's timezone (defaults to workspace default) |
| 11 | Status | Current state: `Active`, `Away`, `Do Not Disturb`, `Focus Mode`, or a custom status text |
| 12 | Status Emoji | Optional emoji for custom status |
| 13 | Spatial Position | `{ x, y }` coordinates on the 2D workspace map |
| 14 | Is Online | Whether the user is currently connected |
| 15 | Last Seen At | Timestamp of last activity |
| 16 | Permissions / Role | Role within this workspace: `OWNER`, `ADMIN`, `MEMBER`, `GUEST` |
| 17 | Desk Customization | Personal items, widgets, quick-access links displayed on their desk area |
| 18 | Joined At | When the user joined this workspace |

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
- I want to **set workspace-wide settings** (max capacity, feature toggles, default timezone), so the office operates consistently.
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

### Cross-Service Interactions

| Interaction | Services Involved | Description |
|---|---|---|
| **User joins workspace** | `user-service` → `workspace-service` | Validate user exists, create a Desk record |
| **Desk shows tasks** | `workspace-service` ↔ `tasks-service` | Desk pulls tasks assigned to this user in this workspace |
| **Status affects notifications** | `workspace-service` → `notification-service` | When status = DND/Focus, notification-service suppresses delivery |
| **Entering a room** | `workspace-service` → `room-service` | Avatar moves into a room zone, room-service handles voice/video join |
| **Scheduling a room** | `calendar-service` → `workspace-service` | Calendar reserves a room; workspace-service validates the room exists on the layout |
| **Chat context** | `chat-service` ↔ `workspace-service` | Chat channels may be scoped to a workspace; desk provides member context |
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

Workspace-service is the **spatial backbone**: it knows *where* everything is, *who* is in the office, and *what state* they're in. Other services consume this context to do their jobs.
