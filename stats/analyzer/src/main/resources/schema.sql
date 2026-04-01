CREATE TABLE IF NOT EXISTS event_similarity (
    event_a   BIGINT           NOT NULL,
    event_b   BIGINT           NOT NULL,
    score     DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP        NOT NULL,
    PRIMARY KEY (event_a, event_b)
);

CREATE TABLE IF NOT EXISTS user_actions (
    user_id    BIGINT           NOT NULL,
    event_id   BIGINT           NOT NULL,
    max_weight DOUBLE PRECISION NOT NULL,
    timestamp  TIMESTAMP        NOT NULL,
    PRIMARY KEY (user_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_event_similarity_a ON event_similarity(event_a);
CREATE INDEX IF NOT EXISTS idx_event_similarity_b ON event_similarity(event_b);
CREATE INDEX IF NOT EXISTS idx_user_actions_user ON user_actions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_actions_event ON user_actions(event_id);
