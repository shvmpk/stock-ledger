-- V12__platform_pause_reason.sql
-- A pause must explain itself. The reason lives on the connection while it is
-- paused so the card can show it, and on the history entry that recorded the
-- pause so older reasons survive later pauses and resumes.

ALTER TABLE platform_connections
    ADD COLUMN pause_reason VARCHAR(500);

ALTER TABLE platform_connection_events
    ADD COLUMN reason VARCHAR(500);
