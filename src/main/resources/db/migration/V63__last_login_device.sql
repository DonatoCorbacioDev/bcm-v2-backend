-- The kind of device of each account's last sign-in ("Chrome su Windows"),
-- to email its owner when a sign-in comes from another one (NewDeviceMonitor,
-- OWASP ASVS 6.3): the browser's family and the operating system only, never
-- the full User-Agent nor the address, so the column says nothing an account
-- page would not. Empty until the next sign-in, which then sends no email.
ALTER TABLE users ADD COLUMN last_login_device VARCHAR(100) NULL;
