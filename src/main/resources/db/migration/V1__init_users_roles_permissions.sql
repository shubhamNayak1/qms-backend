-- =============================================================
-- V1__init_users_roles_permissions.sql (PostgreSQL FIXED)
-- =============================================================

-- ─── permissions ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS permissions (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    module       VARCHAR(50)  NOT NULL,
    description  VARCHAR(255),
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    CONSTRAINT uq_permission_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_perm_module ON permissions (module);
CREATE INDEX IF NOT EXISTS idx_perm_deleted ON permissions (is_deleted);

-- ─── roles ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS roles (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(60)  NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    description    VARCHAR(255),
    is_system_role BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    CONSTRAINT uq_role_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_role_deleted ON roles (is_deleted);

-- ─── role_permissions ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- ─── users ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id                          BIGSERIAL PRIMARY KEY,
    username                    VARCHAR(80)  NOT NULL,
    email                       VARCHAR(150) NOT NULL,
    password_hash               VARCHAR(255) NOT NULL,
    first_name                  VARCHAR(80)  NOT NULL,
    last_name                   VARCHAR(80)  NOT NULL,
    phone                       VARCHAR(25),
    department                  VARCHAR(100),
    designation                 VARCHAR(100),
    employee_id                 VARCHAR(50),
    profile_picture_url         VARCHAR(500),
    is_active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_email_verified           BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts       INT          NOT NULL DEFAULT 0,
    locked_until                TIMESTAMP,
    last_login_at               TIMESTAMP,
    password_changed_at         TIMESTAMP,
    refresh_token_hash          VARCHAR(255),
    password_reset_token        VARCHAR(255),
    password_reset_token_expiry TIMESTAMP,
    is_deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP    NOT NULL,
    updated_at                  TIMESTAMP,
    created_by                  VARCHAR(100),
    updated_by                  VARCHAR(100),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_employee_id UNIQUE (employee_id)
);

CREATE INDEX IF NOT EXISTS idx_users_department ON users (department);
CREATE INDEX IF NOT EXISTS idx_users_active ON users (is_active);
CREATE INDEX IF NOT EXISTS idx_users_deleted ON users (is_deleted);

-- ─── user_roles ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

-- =============================================================
-- SEED DATA (PostgreSQL FIXED)
-- =============================================================

-- Roles
INSERT INTO roles (name, display_name, description, is_system_role, is_deleted, created_at, created_by)
VALUES
('SUPER_ADMIN','Super Administrator','Full unrestricted access',TRUE,FALSE,NOW(),'SYSTEM'),
('QA_MANAGER','QA Manager','Full QMS access',FALSE,FALSE,NOW(),'SYSTEM'),
('QA_OFFICER','QA Officer','Create/update records',FALSE,FALSE,NOW(),'SYSTEM'),
('AUDITOR','Auditor','Read-only access',FALSE,FALSE,NOW(),'SYSTEM'),
('DOC_CONTROLLER','Document Controller','DMS control',FALSE,FALSE,NOW(),'SYSTEM'),
('EMPLOYEE','Employee','Basic access',FALSE,FALSE,NOW(),'SYSTEM')
ON CONFLICT (name) DO NOTHING;

-- Permissions (example block — apply same pattern to all)
INSERT INTO permissions (name, display_name, module, description, is_deleted, created_at, created_by)
VALUES
('USER_VIEW','View Users','USER','View users',FALSE,NOW(),'SYSTEM')
ON CONFLICT (name) DO NOTHING;

-- SUPER_ADMIN → all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

-- Default admin user
INSERT INTO users (
    username, email, password_hash, first_name, last_name,
    is_active, is_email_verified, failed_login_attempts,
    is_deleted, created_at, created_by
)
VALUES (
    'admin','admin@qms.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN4kqJe.MqHJqIb/m6JCu',
    'System','Administrator',
    TRUE, TRUE, 0, FALSE, NOW(),'SYSTEM'
)
ON CONFLICT (username) DO NOTHING;

-- Assign role to admin
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;