-- Repeatable seed: demo workspaces so SkyOffice has something to render on day one
-- (TASKS.md Phase 13). Idempotent — each workspace is guarded on its slug, so re-running is a no-op.
--
-- Two workspaces are seeded, each with five active members:
--   * 'demo'  (id 1): users 1..5  — the primary demo office; the login JWT carries userId 1 or 2.
--   * 'demo2' (id 2): users 6..10 — a second office to prove workspace isolation.
-- The userIds line up with the chat-channel members and rooms seeded in the other services.
--
-- Tileset image_url values are paths the SkyOffice client resolves against its bundled assets
-- (client/public/assets/...), so no image hosting is required for the demo.
--
-- Coordinates: tiles are 32px; the map is 25x18 tiles (800x576 px). Zone/spawn/desk/map-object
-- positions are in PIXELS (matching avatar positions), tile-layer `data` is a flat row-major
-- array of gids (length = width*height), exactly what Phaser/Tiled expect.

DO
$$
    DECLARE
        cols    CONSTANT INT := 25;
        rows    CONSTANT INT := 18;
        ws      RECORD;
        ws_id   BIGINT;
        team_id BIGINT;
        idx     INT;
    BEGIN
        FOR ws IN
            SELECT *
            FROM (VALUES
                      ('demo', 'Demo Office', 1, 'Engineering',
                       ARRAY [1, 2, 3, 4, 5],
                       ARRAY ['Demo User','Ash Rivera','Lucy Park','Nancy Kim','Sam Diaz'],
                       ARRAY ['ADAM','ASH','LUCY','NANCY','ADAM']),
                      ('demo2', 'Acme HQ', 6, 'Operations',
                       ARRAY [6, 7, 8, 9, 10],
                       ARRAY ['Riley Stone','Mia Chen','Omar Said','Tara Vale','Jon Frost'],
                       ARRAY ['NANCY','ASH','LUCY','ADAM','ASH'])
                  ) AS t(slug, name, owner_id, team_name, user_ids, full_names, avatars)
            LOOP
                IF EXISTS (SELECT 1 FROM workspace WHERE slug = ws.slug) THEN
                    CONTINUE; -- already seeded
                END IF;

                INSERT INTO workspace (name, slug, owner_id, description, status, visibility,
                                       invite_token, default_timezone, tile_size, map_width,
                                       map_height, layout_version)
                VALUES (ws.name, ws.slug, ws.owner_id, 'Seeded demo workspace for SkyOffice', 'ACTIVE',
                        'INVITE_ONLY', gen_random_uuid(), 'UTC', 32, cols, rows, 1)
                RETURNING id INTO ws_id;

                -- Tileset (the SkyOffice floor/ground sheet, resolved client-side).
                INSERT INTO tileset (workspace_id, name, image_url, first_gid, tile_width, tile_height,
                                     columns, tile_count)
                VALUES (ws_id, 'FloorAndGround', 'assets/map/FloorAndGround.png', 1, 32, 32, 8, 64);

                -- Ground layer: every tile is the first floor tile (gid 1).
                INSERT INTO map_layer (workspace_id, name, layer_index, collides, data)
                VALUES (ws_id, 'Ground', 0, FALSE,
                        (SELECT jsonb_agg(1 ORDER BY i) FROM generate_series(0, cols * rows - 1) AS i));

                -- Walls layer: a collidable border (gid 2) around the edge, empty (0) inside.
                INSERT INTO map_layer (workspace_id, name, layer_index, collides, data)
                VALUES (ws_id, 'Walls', 1, TRUE,
                        (SELECT jsonb_agg(
                                        CASE
                                            WHEN (i / cols) = 0 OR (i / cols) = rows - 1
                                                OR (i % cols) = 0 OR (i % cols) = cols - 1 THEN 2
                                            ELSE 0
                                            END ORDER BY i)
                         FROM generate_series(0, cols * rows - 1) AS i));

                -- A private meeting room (zone voice) plus the open floor (proximity voice).
                INSERT INTO zone (workspace_id, type, name, x, y, width, height, voice_room_id,
                                  proximity_radius)
                VALUES (ws_id, 'MEETING_ROOM', 'Sync Room', 64, 64, 192, 160, ws.slug || '-meeting-1', NULL),
                       (ws_id, 'OPEN', 'Main Floor', 0, 0, 800, 576, NULL, 120);

                INSERT INTO spawn_point (workspace_id, x, y, label, is_default)
                VALUES (ws_id, 400, 300, 'Entrance', TRUE);

                INSERT INTO team (workspace_id, name, description)
                VALUES (ws_id, ws.team_name, 'Demo team')
                RETURNING id INTO team_id;

                -- Five active members so the office is lively. The first is the OWNER whose desk
                -- drives spawn/avatar/name in SkyOffice. Desks are spread along the floor near the
                -- spawn so two users land close enough to fall into one proximity voice group.
                FOR idx IN 1..array_length(ws.user_ids, 1)
                    LOOP
                        INSERT INTO desk (user_id, workspace_id, full_name, avatar_character, timezone,
                                          status, position_x, position_y, role, team_id, invite_status,
                                          is_active, joined_at)
                        VALUES (ws.user_ids[idx], ws_id, ws.full_names[idx], ws.avatars[idx], 'UTC',
                                'ACTIVE', 320 + idx * 48, 300,
                                CASE WHEN idx = 1 THEN 'OWNER' ELSE 'MEMBER' END,
                                team_id, 'ACCEPTED', TRUE, now());
                    END LOOP;

                -- Interactive objects (stable roomIds drive whiteboard/computer rejoin).
                INSERT INTO map_object (workspace_id, type, label, position_x, position_y, room_id,
                                        capacity)
                VALUES (ws_id, 'COMPUTER', 'Desk PC 1', 160, 200, ws.slug || '-computer-1', 4),
                       (ws_id, 'COMPUTER', 'Desk PC 2', 608, 200, ws.slug || '-computer-2', 4),
                       (ws_id, 'WHITEBOARD', 'Main Board', 400, 128, ws.slug || '-whiteboard-1', 8);
            END LOOP;
    END
$$;
