-- BlockNote JSON is more verbose than HTML; increase text column limits.
-- Using CLOB for H2 compatibility (H2 MODE=PostgreSQL maps CLOB correctly).
ALTER TABLE notes ALTER COLUMN raw_text VARCHAR(500000);
ALTER TABLE pages ALTER COLUMN content_current VARCHAR(500000);
ALTER TABLE page_revisions ALTER COLUMN content_snapshot VARCHAR(500000);
