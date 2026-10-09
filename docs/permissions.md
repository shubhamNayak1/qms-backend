# Permission Catalogue — Phase 2 draft

**Status:** _Draft for review._ No endpoint is touched until this doc is approved.

**Date:** 2026-10-09

## Context

Phase 1 Admin consolidation revealed that the auth model is already split
cleanly between two layers:

- **Admin / module access** — guarded by `@PreAuthorize("hasAnyRole(…)")`
  at the controller. Replace role names here with permission names.
- **QMS workflow stages** — guarded by `@PreAuthorize("isAuthenticated()")`
  at the controller + `OrgSecurityService` predicates inside the service
  (`isCurrentUserQaHead()`, `isCurrentUserHodOf(deptId)`, …). These are
  **relationship-based** (HOD of QA dept, member of RA dept, site head),
  not role-based, and should **stay unchanged**. They are already clean
  for 21 CFR Part 11.

So Phase 2 only refactors the first layer. The catalogue below has 42
permissions grouped by functional area.

## Design rules

- **Name**: `MODULE_ACTION` (uppercase, underscore). Matches the 28
  already seeded in V9. We extend rather than rename.
- **Granularity**: one permission per "button" or "page section" the
  admin would reasonably want to grant or revoke independently.
- **No workflow-stage permissions** (e.g. `CAPA_HOD_REVIEW`). Those
  stay as OrgSecurityService predicates.
- **Hard-deletes get their own permission** (`QMS_HARD_DELETE`,
  `DMS_HARD_DELETE`) — today they require `hasRole('SUPER_ADMIN')`.
- **Admin split**: USER-level permissions stay for "manage users",
  separate ADMIN-level permissions cover "manage roles / policies /
  licences / sites / departments" so Create-Role can be delegated
  separately from Create-User.

## Catalogue (42 permissions)

### Module access (6) — "which modules show in the sidebar"

| Permission        | Purpose |
|-------------------|---------|
| `MOD_DASHBOARD`   | See the Dashboard page. |
| `MOD_QMS`         | See the QMS section in the sidebar. |
| `MOD_DMS`         | See the DMS section. |
| `MOD_LMS`         | See the LMS section. |
| `MOD_REPORTS`     | See the Reports section. |
| `MOD_AUDIT`       | See the Audit Trail. |

### User admin (6) — existing in V9

| Permission            | Purpose |
|-----------------------|---------|
| `USER_VIEW`           | List and search users. |
| `USER_CREATE`         | Create users. |
| `USER_UPDATE`         | Edit user profile / dept / flags. |
| `USER_DELETE`         | Disable (soft-delete) users. |
| `USER_MANAGE_ROLES`   | Assign / revoke roles for users. |
| `USER_RESET_PASSWORD` | Admin-reset another user's password. |

### Role & permission admin (4) — NEW

| Permission        | Purpose |
|-------------------|---------|
| `ROLE_VIEW`       | See the Role tab. |
| `ROLE_MANAGE`     | Create / edit / disable roles; assign permissions to a role. |
| `PERM_VIEW`       | See the Permission matrix. |
| `PERM_MANAGE`     | Grant / revoke user-level permission overrides. |

### Organisation admin (5) — NEW

| Permission        | Purpose |
|-------------------|---------|
| `DEPT_VIEW`       | List departments. |
| `DEPT_MANAGE`     | Create / edit / delete departments. |
| `SITE_VIEW`       | See Site profile. |
| `SITE_MANAGE`     | Edit Site profile (incl. site-head assignment). |
| `ORG_TREE_VIEW`   | See the Org Tree visualisation. |

### Policy admin (2) — NEW

| Permission             | Purpose |
|------------------------|---------|
| `PASSWORD_POLICY_MANAGE` | Create / edit the active password policy. |
| `TCD_POLICY_MANAGE`      | Update per-module Target Closure Days rows. |

### Licence admin (3) — NEW

| Permission         | Purpose |
|--------------------|---------|
| `LICENSE_VIEW`     | List licences. |
| `LICENSE_MANAGE`   | Create / edit licences. |
| `LICENSE_ASSIGN`   | Assign / revoke a licence to a user. |

### QMS (7) — mostly existing in V9

| Permission        | Purpose |
|-------------------|---------|
| `QMS_VIEW`        | View any QMS record. |
| `QMS_CREATE`      | Initiate CAPA / Deviation / Incident / Complaint / Change Control. |
| `QMS_UPDATE`      | Edit record metadata (not workflow transitions — those stay contextual). |
| `QMS_APPROVE`     | Approve or reject (used as a coarse flag; actual stage auth remains contextual). |
| `QMS_ASSIGN`      | Assign a record to a user. |
| `QMS_HARD_DELETE` | Soft-delete QMS records (previously SUPER_ADMIN only). |
| `QMS_EXPORT`      | Export QMS records / reports. |

### DMS (6) — existing in V9

| Permission        | Purpose |
|-------------------|---------|
| `DMS_VIEW`        | List & search documents. |
| `DMS_UPLOAD`      | Upload / create new documents. |
| `DMS_UPDATE`      | Edit document metadata & content. |
| `DMS_APPROVE`     | Approve / publish documents. |
| `DMS_DOWNLOAD`    | Download document files. |
| `DMS_HARD_DELETE` | Soft-delete documents (previously SUPER_ADMIN only). |

### LMS (6) — existing in V9

| Permission        | Purpose |
|-------------------|---------|
| `LMS_VIEW`        | View training programs & enrollments. |
| `LMS_CREATE`      | Create training programs. |
| `LMS_UPDATE`      | Edit programs / content. |
| `LMS_DELETE`      | Delete programs. |
| `LMS_ENROLL`      | Enroll users in training. |
| `LMS_CERTIFY`     | Issue certificates. |

### Reporting & Audit (4) — existing in V9

| Permission        | Purpose |
|-------------------|---------|
| `REPORT_VIEW`     | View reports and dashboards. |
| `REPORT_EXPORT`   | Export reports to PDF / Excel. |
| `AUDIT_VIEW`      | View the audit trail. |
| `AUDIT_EXPORT`    | Export audit logs. |

### System (1) — NEW

| Permission        | Purpose |
|-------------------|---------|
| `SYSTEM_OVERRIDE` | The "god mode" bit. Replaces today's hardcoded `isSuperAdmin()` check in `OrgSecurityService` — any user holding this bypasses every contextual predicate (QA Head, HOD, Site Head, RA, Dept Reviewer). Only SUPER_ADMIN gets this by default. |

**Running total: 42 permissions. 28 already seeded, 14 new.**

## Default Role × Permission mapping

### Existing seeded roles (post-Phase-2)

| Role             | Permissions |
|------------------|-------------|
| `SUPER_ADMIN`    | All 42 (unchanged). |
| `ADMIN`          | MOD_DASHBOARD · USER_* · ROLE_* · PERM_* · DEPT_* · SITE_* · ORG_TREE_VIEW · PASSWORD_POLICY_MANAGE · TCD_POLICY_MANAGE · LICENSE_* · AUDIT_VIEW (every admin op; no workflow access). |
| `QA_MANAGER`     | MOD_DASHBOARD · MOD_QMS · MOD_DMS · MOD_LMS · MOD_REPORTS · MOD_AUDIT · QMS_* · DMS_* · LMS_VIEW · LMS_ENROLL · REPORT_* · AUDIT_VIEW · USER_VIEW · DEPT_VIEW · SITE_VIEW. |
| `QA_OFFICER`     | MOD_DASHBOARD · MOD_QMS · MOD_DMS · QMS_VIEW · QMS_CREATE · QMS_UPDATE · DMS_VIEW · DMS_UPLOAD · DMS_UPDATE · DMS_DOWNLOAD · REPORT_VIEW. |
| `AUDITOR`        | MOD_DASHBOARD · MOD_QMS · MOD_DMS · MOD_LMS · MOD_REPORTS · MOD_AUDIT · QMS_VIEW · DMS_VIEW · DMS_DOWNLOAD · LMS_VIEW · REPORT_VIEW · AUDIT_VIEW · AUDIT_EXPORT. |
| `DOC_CONTROLLER` | MOD_DASHBOARD · MOD_DMS · DMS_* (less HARD_DELETE) · REPORT_VIEW. |
| `EMPLOYEE`       | MOD_DASHBOARD · MOD_QMS · MOD_DMS · MOD_LMS · QMS_VIEW · QMS_CREATE · DMS_VIEW · DMS_DOWNLOAD · LMS_VIEW · LMS_ASSESS. |

Workflow-stage access (HOD of CAPA, QA Reviewer, Site Head, etc.) stays
unchanged — governed by `OrgSecurityService` based on org structure,
not role. **A user becomes "QA Head" by being set as HOD of a QA
department; it is not a role grant.**

## Backend refactor plan

### `@PreAuthorize` edits (~36 total)

| Controller                     | Before                                           | After |
|--------------------------------|---------------------------------------------------|-------|
| `UserController` (11)          | `hasAnyRole('SUPER_ADMIN','ADMIN',…)`             | `hasAuthority('USER_*')` per endpoint |
| `RoleController` (1)           | class-level `hasAnyRole('SUPER_ADMIN','ADMIN')`   | `hasAuthority('ROLE_MANAGE')` on writes, `ROLE_VIEW` on reads |
| `PermissionController` (1)     | class-level                                       | `PERM_VIEW` / `PERM_MANAGE` |
| `PasswordPolicyController` (6) |                                                   | `PASSWORD_POLICY_MANAGE` on writes, `PASSWORD_POLICY_VIEW`¹ on reads |
| `TcdPolicyController` (3)      |                                                   | `TCD_POLICY_MANAGE` on writes, auth'd reads |
| `DepartmentController` (3)     |                                                   | `DEPT_MANAGE` / `DEPT_VIEW` |
| `SiteController` (1)           |                                                   | `SITE_MANAGE` |
| `LicenseController` (5)        |                                                   | `LICENSE_MANAGE` / `LICENSE_ASSIGN` |
| QMS `DELETE` endpoints (6)     | `hasRole('SUPER_ADMIN')`                          | `hasAuthority('QMS_HARD_DELETE')` |
| DMS `DELETE` endpoint          | `hasRole('SUPER_ADMIN')`                          | `hasAuthority('DMS_HARD_DELETE')` |

¹ `PASSWORD_POLICY_VIEW` isn't in the catalogue — reads collapse to
"any admin can view", so we use `hasAnyAuthority('PASSWORD_POLICY_MANAGE','PERM_VIEW')`
or just leave reads at `isAuthenticated()`. (TBD per endpoint during
implementation.)

### `OrgSecurityService.isSuperAdmin()`

Today:
```java
public boolean isSuperAdmin() {
    return currentUser().map(u -> u.getRoles().stream()
        .anyMatch(r -> "SUPER_ADMIN".equals(r.getName()))).orElse(false);
}
```

After:
```java
public boolean isSuperAdmin() {
    // Backward-compat helper; actual check is now permission-based.
    return hasSystemOverride();
}
public boolean hasSystemOverride() {
    return SecurityContextHolder.getContext().getAuthentication()
        .getAuthorities().stream()
        .anyMatch(a -> "SYSTEM_OVERRIDE".equals(a.getAuthority()));
}
```

### `JwtAuthenticationFilter` / `UserPrincipal`

Spring maps `GrantedAuthority` from the user's permissions, not roles.
Today authorities are prefixed with `ROLE_` and derived from role
names; after refactor authorities = union of (role perms) + (user
grants) − (user revokes). Role-name prefix stays for the small number
of `hasRole(…)` checks that remain (if any).

## Flyway migration shape (V35)

```sql
-- 1. Add the 14 new permissions
INSERT INTO permissions (name, display_name, module, description, …) VALUES
  ('MOD_DASHBOARD',          'Access Dashboard',    'MOD',    …),
  ('MOD_QMS',                'Access QMS',          'MOD',    …),
  … (14 new rows) …
ON CONFLICT (name) DO NOTHING;

-- 2. Create user_permissions table (grant/revoke overrides)
CREATE TABLE user_permissions (
  user_id       BIGINT    NOT NULL REFERENCES users(id),
  permission_id BIGINT    NOT NULL REFERENCES permissions(id),
  grant_type    VARCHAR(10) NOT NULL CHECK (grant_type IN ('GRANT','REVOKE')),
  granted_by    VARCHAR(100),
  granted_at    TIMESTAMP NOT NULL DEFAULT NOW(),
  reason        VARCHAR(500),
  PRIMARY KEY (user_id, permission_id)
);

-- 3. Default role mappings (per the table above)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name IN (
  'MOD_DASHBOARD','USER_VIEW','USER_CREATE', …
) ON CONFLICT DO NOTHING;

-- (similar INSERTs for QA_MANAGER, QA_OFFICER, AUDITOR, DOC_CONTROLLER, EMPLOYEE)
```

## Frontend impact

- `AdminPage` — gate changes from `isSuperAdmin || hasRole('ADMIN')` to
  `hasAuthority('MOD_DASHBOARD')` with each tab additionally gating on
  its own permission (`DEPT_VIEW`, `USER_VIEW`, `ROLE_VIEW`, etc.).
- `Sidebar` — each menu item's `moduleKey` becomes a permission check.
- `AdminRoleTab` — Create / Edit / Disable become live; new RoleEditDialog.
- `AdminPermissionTab` — placeholder becomes a live Role × Perm +
  User × Perm matrix with grant/revoke colouring.

## Risks and mitigations

| Risk                                            | Mitigation |
|-------------------------------------------------|------------|
| Logged-in users mid-session lose access after refactor | Phase 2 ships a one-shot `/auth/me` refresh; JWT re-issued on next request. |
| Admin UI breaks for seeded roles if mapping is wrong | Default role × permission grants in V35 are reviewed in this doc **before** the migration lands. |
| Someone grants `SYSTEM_OVERRIDE` by accident | UI shows a red destructive-action modal + e-sign + requires a reason stored in audit_log. |
| `OrgSecurityService` behaviour changes under us  | Workflow predicates don't change at all. Only `isSuperAdmin()` is rewritten, and the semantics stay "any user who can bypass contextual gates". |

## What this doc asks you to approve

- The 14 **new** permissions and their names.
- The default **Role × Permission mapping** above.
- The decision to **leave QMS workflow stages** on `OrgSecurityService`
  predicates and not promote them to permissions.
- The decision to replace **`isSuperAdmin()`** with
  `hasAuthority('SYSTEM_OVERRIDE')`.

Once you sign off, I ship V35 + the 36 `@PreAuthorize` edits + the
`user_permissions` table + the matrix UI + Create/Edit Role.
