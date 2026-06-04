CREATE TABLE notifications (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    org_id     BIGINT        NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    type       VARCHAR(20)   NOT NULL DEFAULT 'INFO',
    read_status BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifications_user (user_id),
    INDEX idx_notifications_org  (org_id),
    INDEX idx_notifications_read (read_status)
);
