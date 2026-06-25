# workspace-service — Database Schema (Normalized to BCNF)

> Authoritative relational schema. The whole schema is normalized to **3NF/BCNF**.
> `jsonb` survives in **exactly three** places, each a justified exception documented
> in §3 — never to hide a repeating group of entities.
>
> This reconciles with decision **D2** ("DB is source of truth for the full map"):
> the map is still fully owned by the DB, but its *entities* (zones, spawn points,
> tilesets, layers) are now **tables**, not keys inside one JSON blob. The
> `GET /api/workspace/{wid}/layout` endpoint reassembles them into the client document.

---

## 1. Normalization analysis

| Entity (old design) | NF issue found | Resolution |
|---|---|---|
| `Workspace.layoutMap` (jsonb) | **1NF** — `zones[]`, `spawnPoints[]`, `tilesets[]`, `layers[]` are repeating groups of real entities packed in one column | Decomposed into `zone`, `spawn_point`, `tileset`, `map_layer` tables. Only the raw tile **matrix** stays serialized (§3.1). |
| `Desk.deskCustomization` (jsonb) | **1NF** — multiple widgets/quick-links = a repeating group | Decomposed into `desk_widget` (one row per widget). Per-widget type-specific settings stay as `config jsonb` (§3.3). |
| `Desk.links` (`Set<URL>`) | already correct | Stays as child table `desk_link`. |
| `WorkspaceInvitation.role` vs `Desk.role` | **redundancy** — role lived in two places | `desk.role` is authoritative once accepted; `invitation.role` is the *requested* role, valid only while `PENDING`. Different facts, no transitive dependency. Kept, documented. |
| `Desk.is_online` | **3NF-ish** — derivable from session/`last_seen_at` | Kept as an intentional **cache** (Colyseus syncs it). Flagged as denormalization for read performance; `last_seen_at` is the source of truth. |
| `Desk.full_name`, `work_email` | look like copies of user-service data | **Not** a violation here — user-service is a separate DB; these are per-workspace overrides with their own lifecycle. No `user` table exists in this schema to FK against. |
| every other column | atomic, single-valued | — |

**Verdicts after decomposition:**
- **1NF** — every column atomic except the 3 documented exceptions (§3). All entity arrays are tables. ✓
- **2NF** — all tables use a surrogate `id` PK; no attribute depends on part of a key. The natural key `(user_id, workspace_id)` on `desk` is enforced as a UNIQUE constraint, and no non-key column depends on only one of its parts. ✓
- **3NF** — no non-key → non-key transitive dependencies (the only one, `is_online`, is a deliberate documented cache). ✓
- **BCNF** — every determinant is a candidate key: surrogate `id` plus the UNIQUE constraints (`slug`, `invite_token`, `token`, `room_id`, `voice_room_id`, `(user_id, workspace_id)`, `(workspace_id, name)`…). ✓

> **Cross-service references** (`owner_id`, `user_id`, `invited_by`) point at user-service and
> carry **no FK** (separate database, microservice boundary). That is unenforced-by-design, not
> a normalization defect.

---

## 2. Tables

> PostgreSQL. Enums shown as the app's `WorkspaceRole`/etc. — implement as native PG enums or
> `varchar` + `CHECK`. Timestamps are `timestamptz`. All FKs below are within this DB.

### workspace
| Column | Type | Notes |
|---|---|---|
| id | bigserial PK | |
| name | text NOT NULL | |
| slug | text **UNIQUE** NOT NULL | |
| owner_id | bigint NOT NULL | user-service ref (no FK) |
| description | text | |
| logo_url | text | |
| status | enum NOT NULL | `ACTIVE/ARCHIVED/SUSPENDED` |
| visibility | enum NOT NULL | `INVITE_ONLY` |
| invite_token | uuid **UNIQUE** | rotatable |
| default_timezone | text NOT NULL | |
| tile_size | int NOT NULL | pulled out of old JSON (atomic) |
| map_width | int NOT NULL | in tiles |
| map_height | int NOT NULL | in tiles |
| map_geometry | jsonb | **exception §3.1** — see note |
| layout_version | bigint NOT NULL | `@Version` optimistic lock |
| created_at / updated_at | timestamptz | |

### tileset  *(was `layoutMap.tilesets[]`)*
| id PK · workspace_id FK→workspace · name · image_url · first_gid · tile_width · tile_height · columns · tile_count | **UNIQUE(workspace_id, first_gid)** |

### map_layer  *(was `layoutMap.layers[]`)*
| id PK · workspace_id FK · name · layer_index · collides bool · data jsonb (**exception §3.2** — tile gid matrix) | **UNIQUE(workspace_id, layer_index)** |

### zone  *(was `layoutMap.zones[]`; also read by room-service)*
| id PK · workspace_id FK · type (`ZoneType`) · name · x · y · width · height · voice_room_id text **UNIQUE** NULL · proximity_radius int NULL · created_at · updated_at |

### spawn_point  *(was `layoutMap.spawnPoints[]`)*
| id PK · workspace_id FK · x · y · label NULL · is_default bool |

### team
| id PK · workspace_id FK · name · description · created_at · updated_at | **UNIQUE(workspace_id, name)** |

### desk
| Column | Type | Notes |
|---|---|---|
| id | bigserial PK | |
| user_id | bigint NOT NULL | user-service ref |
| workspace_id | bigint NOT NULL FK→workspace | |
| full_name / nick_name / title | text | per-workspace |
| work_email / phone | text | |
| personal_image_url | text | |
| avatar_character | enum NOT NULL | `ADAM/ASH/LUCY/NANCY` |
| timezone | text | |
| status | enum NOT NULL | `DeskStatus` |
| status_emoji | text NULL | |
| status_custom_text | text NULL | for `CUSTOM` |
| position_x / position_y | int | last desk position |
| is_online | bool NOT NULL | **cache** (§1) |
| last_seen_at | timestamptz | source of truth for presence |
| role | enum NOT NULL | `WorkspaceRole` |
| bio | text | |
| team_id | bigint NULL FK→team | **ON DELETE SET NULL** |
| invite_status | enum NOT NULL | `PENDING/ACCEPTED` |
| invited_by | bigint NULL | user ref |
| is_active | bool NOT NULL | soft-delete |
| joined_at | timestamptz NULL | |
| created_at / updated_at | timestamptz | |
| | | **UNIQUE(user_id, workspace_id)** |

### desk_link  *(`Desk.links`)*
| id PK · desk_id FK→desk (ON DELETE CASCADE) · url | **UNIQUE(desk_id, url)** |

### desk_widget  *(was `deskCustomization` jsonb)*
| id PK · desk_id FK (CASCADE) · type · label · position int · config jsonb (**exception §3.3**) | **UNIQUE(desk_id, position)** |

### map_object
| id PK · workspace_id FK · type (`COMPUTER/WHITEBOARD`) · label · position_x · position_y · room_id text **UNIQUE** NOT NULL · capacity int · is_active bool · created_at · updated_at |

### workspace_invitation
| id PK · workspace_id FK · invited_email · invited_by · token uuid **UNIQUE** · role enum · status enum · expires_at · created_at | **partial UNIQUE(workspace_id, invited_email) WHERE status='PENDING'** (no duplicate live invites) |

---

## 3. Justified `jsonb` exceptions (everything else is relational)

These are **not** normalization failures — they hold values that are atomic-as-a-unit and gain
nothing from relational decomposition.

**3.1 `workspace.map_geometry`** — optional misc geometry the renderer treats as one document
(e.g. background mode, parallax config). The *entities* (zones/spawns/tilesets/layers) are tables;
this is only leftover render config. May be dropped if empty.

**3.2 `map_layer.data`** — the tile-gid **matrix** for one layer. Strictly this is non-atomic, but
fully normalizing it into a `tile(layer_id, x, y, gid)` table yields *width×height* rows per layer
(tens of thousands+), with zero relational query benefit — tiles are only ever read as a whole
layer. This is the standard treatment (like storing an image), and is the **single intentional 1NF
exception**, called out explicitly.

**3.3 `desk_widget.config`** — per-widget, type-specific settings whose shape varies by widget
`type` (a clock's timezone vs. a note's text vs. a link list). The repeating group (many widgets) is
normalized into rows; only each widget's variable settings remain JSON. This is preferable to a
sparse EAV table or a wide nullable table.

---

## 4. Indexes (beyond PK/UNIQUE)

- `desk (workspace_id) WHERE is_active` — member directory
- `desk (workspace_id, user_id)` — covered by the unique constraint
- `map_object (workspace_id) WHERE is_active`
- `zone (workspace_id)` · `tileset (workspace_id)` · `map_layer (workspace_id)` · `spawn_point (workspace_id)`
- `workspace_invitation (token)` — covered by unique; add `(workspace_id, status)`

---

## 5. Impact on the build

- **Flyway `V1__init_schema.sql`** creates all 12 tables above (not one `workspace` row with a JSON map).
- **Entities/repositories** — add `Tileset`, `MapLayer`, `Zone`, `SpawnPoint`, `DeskWidget`
  (and their repos) to TASKS Phase 2; drop the `layoutMap`/`deskCustomization` columns.
- **LayoutService (Phase 7)** reads/writes the normalized tables in one `@Transactional`
  unit and (de)serializes the client document at the API edge — `layout_version` still guards
  concurrent admin edits.
- **MapStruct** assembles `LayoutResponse`/`SessionConfigResponse` from the joined tables.
</content>
