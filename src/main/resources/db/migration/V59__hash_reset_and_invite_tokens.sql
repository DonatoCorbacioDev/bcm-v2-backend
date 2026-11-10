-- Password reset and invitation links are stored as their SHA-256 (OWASP
-- API2), like refresh tokens: whoever reads the database (a dump, a stolen
-- backup) holds nothing that works as a link. SHA2(token, 256) is lowercase
-- hex, the same as TokenHash.sha256Hex, so links already sent keep working.
UPDATE password_reset_token SET token = SHA2(token, 256);
UPDATE invite_token SET token = SHA2(token, 256);
