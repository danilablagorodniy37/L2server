ALTER TABLE random_spawn RENAME COLUMN broadcastSpawn TO old_broadcastSpawn;
ALTER TABLE random_spawn RENAME COLUMN randomSpawn TO old_randomSpawn;
ALTER TABLE random_spawn ADD COLUMN broadcastSpawn BOOL NOT NULL DEFAULT FALSE;
ALTER TABLE random_spawn ADD COLUMN randomSpawn BOOL NOT NULL DEFAULT TRUE;
UPDATE random_spawn SET broadcastSpawn=TRUE WHERE CAST(old_broadcastSpawn AS CHAR)='true' OR CAST(old_broadcastSpawn AS CHAR)='1';
UPDATE random_spawn SET randomSpawn=FALSE WHERE CAST(old_randomSpawn AS CHAR)='false' OR CAST(old_randomSpawn AS CHAR)='0';
ALTER TABLE random_spawn DROP COLUMN old_broadcastSpawn, DROP COLUMN old_randomSpawn;
