-- Repeatable seed: one demo workspace so SkyOffice has something to render on day one
-- (TASKS.md Phase 13). Idempotent — guarded on the workspace slug, so re-running is a no-op.
--
-- The demo user is user-service id 1: the JWT used to enter SkyOffice must carry userId=1.
-- Tileset image_url values are paths the SkyOffice client resolves against its bundled assets
-- (client/public/assets/...), so no image hosting is required for the demo.
--
-- Coordinates: tiles are 32px; the map is 25x18 tiles (800x576 px). Zone/spawn/desk/map-object
-- positions are in PIXELS (matching avatar positions), tile-layer `data` is a flat row-major
-- array of gids (length = width*height), exactly what Phaser/Tiled expect.

DO
$$
    DECLARE
        ws_id   BIGINT;
        team_id BIGINT;
        cols    CONSTANT INT := 25;
        rows    CONSTANT INT := 18;
    BEGIN
        IF EXISTS (SELECT 1 FROM workspace WHERE slug = 'demo') THEN
            RETURN; -- already seeded
        END IF;

        INSERT INTO workspace (name, slug, owner_id, description, status, visibility, invite_token,
                               default_timezone, tile_size, map_width, map_height, layout_version)
        VALUES ('Demo Office', 'demo', 1, 'Seeded demo workspace for SkyOffice', 'ACTIVE',
                'INVITE_ONLY', gen_random_uuid(), 'UTC', 32, cols, rows, 1)
        RETURNING id INTO ws_id;

        -- Tileset (the SkyOffice floor/ground sheet, resolved client-side).
        INSERT INTO tileset (workspace_id, name, image_url, first_gid, tile_width, tile_height, columns, tile_count)
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
        INSERT INTO zone (workspace_id, type, name, x, y, width, height, voice_room_id, proximity_radius)
        VALUES (ws_id, 'MEETING_ROOM', 'Sync Room', 64, 64, 192, 160, 'demo-meeting-1', NULL),
               (ws_id, 'OPEN', 'Main Floor', 0, 0, 800, 576, NULL, 120);

        INSERT INTO spawn_point (workspace_id, x, y, label, is_default)
        VALUES (ws_id, 400, 300, 'Entrance', TRUE);

        INSERT INTO team (workspace_id, name, description)
        VALUES (ws_id, 'Engineering', 'Demo team')
        RETURNING id INTO team_id;

        -- Three active members so the office isn't empty: user 1 (the demo login) plus two
        -- teammates. Their userIds line up with the chat-channel members and room seeded in the
        -- other services. user 1 is the OWNER whose desk drives spawn/avatar/name in SkyOffice.
        INSERT INTO desk (user_id, workspace_id, full_name, avatar_character, timezone, status,
                          position_x, position_y, role, team_id, invite_status, is_active, joined_at)
        VALUES (1, ws_id, 'Demo User', 'ADAM', 'UTC', 'ACTIVE', 400, 300, 'OWNER', team_id,
                'ACCEPTED', TRUE, now()),
               (2, ws_id, 'Ash Rivera', 'ASH', 'UTC', 'ACTIVE', 200, 360, 'MEMBER', team_id,
                'ACCEPTED', TRUE, now()),
               (3, ws_id, 'Lucy Park', 'LUCY', 'UTC', 'ACTIVE', 560, 360, 'MEMBER', team_id,
                'ACCEPTED', TRUE, now());

        -- Interactive objects (stable roomIds drive whiteboard/computer rejoin).
        INSERT INTO map_object (workspace_id, type, label, position_x, position_y, room_id, capacity)
        VALUES (ws_id, 'COMPUTER', 'Desk PC 1', 160, 200, 'demo-computer-1', 4),
               (ws_id, 'COMPUTER', 'Desk PC 2', 608, 200, 'demo-computer-2', 4),
               (ws_id, 'WHITEBOARD', 'Main Board', 400, 128, 'demo-whiteboard-1', 8);
    END
$$;
