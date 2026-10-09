-- Admin module Phase 1 (2026-10-09) — seed the new ADMIN role.
--
-- Context: the frontend /admin page consolidates Users / Org / Licenses
-- into one place. The user has asked for a second-tier admin role so
-- SUPER_ADMIN can delegate day-to-day user and org admin without
-- handing over the master keys.
--
-- Scope (Phase 1): ADMIN gets access to the same admin-y endpoints
-- SUPER_ADMIN gates today — users, roles (read-only), permissions
-- (read-only), departments, sites, licenses, policies. The actual
-- authorization change happens in the Java side (@PreAuthorize now
-- reads hasAnyRole('SUPER_ADMIN','ADMIN')), so this migration only
-- seeds the role row + grants it every existing permission.
--
-- Phase 2 (planned): once @PreAuthorize is refactored to
-- hasAuthority('perm.name'), ADMIN keeps only the admin-scoped
-- permissions and loses workflow-level perms, which it should never
-- have had.

INSERT INTO roles (name, display_name, description, is_system_role, is_deleted, created_at, created_by)
VALUES (
    'ADMIN',
    'Administrator',
    'Day-to-day administrator — users, departments, licences, policies. Below SUPER_ADMIN.',
    TRUE,
    FALSE,
    NOW(),
    'SYSTEM'
)
ON CONFLICT (name) DO NOTHING;

-- Grant ADMIN every existing permission (parity with the SUPER_ADMIN
-- grant from V1). Phase 2 narrows this to admin-only permissions once
-- the permission catalogue is finalised.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
