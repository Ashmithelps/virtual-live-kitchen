-- Milestone 5: guest checkout creates CUSTOMER users identified only by phone, with no password
-- until they register (which then claims the account).
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users ADD CONSTRAINT ck_users_password_required_for_staff
    CHECK (password_hash IS NOT NULL OR role = 'CUSTOMER');
