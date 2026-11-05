-- ---------------------------------------------------------------------------
-- V30  Individual platform: remove the college model.
--
-- This product has two actors, USER and ADMIN. Colleges, departments, batches,
-- staff scopes, company requirements, shortlists, placement stage changes and
-- CGPA records belong to the college edition and are removed. Constraints and
-- indexes are dropped explicitly before their columns so the migration behaves
-- the same on PostgreSQL and H2.
-- ---------------------------------------------------------------------------

-- Placement and requirement tables, children first.
DROP TABLE IF EXISTS placement_stage_changes;
DROP TABLE IF EXISTS company_requirement_shortlists;
DROP TABLE IF EXISTS company_requirement_departments;
DROP TABLE IF EXISTS company_requirement_skills;
DROP TABLE IF EXISTS company_requirements;
DROP TABLE IF EXISTS staff_scopes;

-- Roles: STUDENT becomes USER, the portal administrator becomes ADMIN. Any
-- college staff account has no meaning here and is removed with its data.
DELETE FROM users WHERE role IN ('DEPARTMENT_COORDINATOR', 'PLACEMENT_COORDINATOR');
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users DROP CONSTRAINT ck_users_institution_required;
UPDATE users SET role = 'USER' WHERE role = 'STUDENT';
UPDATE users SET role = 'ADMIN' WHERE role = 'PORTAL_ADMIN';
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));

-- Users.
ALTER TABLE users DROP CONSTRAINT fk_users_institution;
ALTER TABLE users DROP CONSTRAINT fk_users_department;
ALTER TABLE users DROP CONSTRAINT fk_users_batch;
DROP INDEX IF EXISTS idx_users_institution_role;
DROP INDEX IF EXISTS idx_users_institution;
DROP INDEX IF EXISTS idx_users_department;
DROP INDEX IF EXISTS idx_users_batch;
ALTER TABLE users DROP COLUMN institution_id;
ALTER TABLE users DROP COLUMN department_id;
ALTER TABLE users DROP COLUMN batch_id;
ALTER TABLE users DROP COLUMN roll_number;
ALTER TABLE users DROP COLUMN section;

-- Candidate profiles: institution link and CGPA records.
ALTER TABLE candidate_profiles DROP CONSTRAINT ck_candidate_reported_cgpa_range;
ALTER TABLE candidate_profiles DROP CONSTRAINT ck_candidate_cgpa_paired;
ALTER TABLE candidate_profiles DROP CONSTRAINT ck_candidate_cgpa_source;
ALTER TABLE candidate_profiles DROP CONSTRAINT ck_candidate_cgpa_scale;
ALTER TABLE candidate_profiles DROP CONSTRAINT ck_candidate_cgpa_range;
ALTER TABLE candidate_profiles DROP CONSTRAINT fk_candidate_cgpa_recorded_by;
ALTER TABLE candidate_profiles DROP CONSTRAINT fk_candidate_profiles_institution;
DROP INDEX IF EXISTS idx_candidate_profiles_cgpa_source;
DROP INDEX IF EXISTS idx_candidate_profiles_institution;
ALTER TABLE candidate_profiles DROP COLUMN institution_id;
ALTER TABLE candidate_profiles DROP COLUMN cgpa;
ALTER TABLE candidate_profiles DROP COLUMN cgpa_scale;
ALTER TABLE candidate_profiles DROP COLUMN cgpa_source;
ALTER TABLE candidate_profiles DROP COLUMN cgpa_recorded_by;
ALTER TABLE candidate_profiles DROP COLUMN cgpa_recorded_at;
ALTER TABLE candidate_profiles DROP COLUMN reported_cgpa;
ALTER TABLE candidate_profiles DROP COLUMN reported_cgpa_recorded_at;

-- Job matches, audit events, erasure requests.
ALTER TABLE job_matches DROP CONSTRAINT fk_job_matches_institution;
DROP INDEX IF EXISTS idx_job_matches_institution_score;
ALTER TABLE job_matches DROP COLUMN institution_id;

DROP INDEX IF EXISTS idx_audit_events_institution;
ALTER TABLE audit_events DROP COLUMN institution_id;

DROP INDEX IF EXISTS idx_account_erasures_institution;
ALTER TABLE account_erasures DROP COLUMN institution_id;

-- AI quota: only per-user allowances remain.
DELETE FROM ai_usage_counters WHERE scope = 'INSTITUTION';
ALTER TABLE ai_usage_counters DROP CONSTRAINT ck_ai_usage_counters_scope;
ALTER TABLE ai_usage_counters ADD CONSTRAINT ck_ai_usage_counters_scope CHECK (scope IN ('USER'));

-- The college tables themselves.
DROP TABLE IF EXISTS batches;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS institutions;
