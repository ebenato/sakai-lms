ALTER TABLE MFR_OPEN_FORUM_T ADD COLUMN LOCKED_AFTER_CLOSED BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE MFR_TOPIC_T ADD COLUMN LOCKED_AFTER_CLOSED BOOLEAN NOT NULL DEFAULT 0;