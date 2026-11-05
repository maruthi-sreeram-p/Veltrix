package com.careerflux.user;

/**
 * A single thing a principal is allowed to do.
 *
 * <p>Permissions are granted through roles, and the mapping lives in
 * {@link UserRole} rather than in a database table: the two roles' capabilities
 * are a product decision, not something to redefine at runtime.
 *
 * <p>Each constant becomes a Spring Security authority, so it can be used
 * directly in {@code @PreAuthorize("hasAuthority('SOURCE_MANAGE')")}.
 */
public enum Permission {
    // --- Own data -----------------------------------------------------------
    /** Read and write one's own candidate profile, resume and preferences. */
    SELF_PROFILE_MANAGE,
    /** Save, dismiss and track jobs for oneself. */
    SELF_JOBS_MANAGE,
    /** Use AI features against one's own quota. */
    SELF_AI_USE,
    /** Request deletion of one's own account and derived data. */
    SELF_ACCOUNT_DELETE,

    // --- Market -------------------------------------------------------------
    /** Job-market intelligence across the corpus. */
    JOB_MARKET_VIEW,

    // --- Platform operation -------------------------------------------------
    SOURCE_VIEW,
    SOURCE_MANAGE,
    INGESTION_MANAGE,
    SYSTEM_HEALTH_VIEW,
    /** Read the platform audit log. Never reveals profile content. */
    AUDIT_READ_PLATFORM,
    /** Adjust AI usage quotas. */
    AI_USAGE_MANAGE
}
