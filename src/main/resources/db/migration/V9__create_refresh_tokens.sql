CREATE TABLE refresh_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    token       VARCHAR(512)  NOT NULL UNIQUE,
    user_id     BIGINT        NOT NULL,
    expiry_date DATETIME(6)   NOT NULL,
    revoked     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_refresh_token_token   (token),
    INDEX idx_refresh_token_user_id (user_id)
);
