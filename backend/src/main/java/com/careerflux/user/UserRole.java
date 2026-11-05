package com.careerflux.user;

import java.util.EnumSet;
import java.util.Set;

import static com.careerflux.user.Permission.AI_USAGE_MANAGE;
import static com.careerflux.user.Permission.AUDIT_READ_PLATFORM;
import static com.careerflux.user.Permission.INGESTION_MANAGE;
import static com.careerflux.user.Permission.JOB_MARKET_VIEW;
import static com.careerflux.user.Permission.SELF_ACCOUNT_DELETE;
import static com.careerflux.user.Permission.SELF_AI_USE;
import static com.careerflux.user.Permission.SELF_JOBS_MANAGE;
import static com.careerflux.user.Permission.SELF_PROFILE_MANAGE;
import static com.careerflux.user.Permission.SOURCE_MANAGE;
import static com.careerflux.user.Permission.SOURCE_VIEW;
import static com.careerflux.user.Permission.SYSTEM_HEALTH_VIEW;

/**
 * The two actors of the individual platform.
 *
 * <p>There are no employer, recruiter or institution roles. A {@link #USER} owns
 * their profile and sees only their own data; an {@link #ADMIN} operates the
 * platform (sources, ingestion, health) and holds no permission to read a user's
 * profile or resume. Owning the platform is not a reason to read anyone's data.
 */
public enum UserRole {
    /** A job seeker. Sees only their own data. */
    USER(EnumSet.of(
            SELF_PROFILE_MANAGE,
            SELF_JOBS_MANAGE,
            SELF_AI_USE,
            SELF_ACCOUNT_DELETE)),
    /** Operates CareerFlux itself: job sources, ingestion, audit and AI usage. */
    ADMIN(EnumSet.of(
            SOURCE_VIEW,
            SOURCE_MANAGE,
            INGESTION_MANAGE,
            SYSTEM_HEALTH_VIEW,
            AUDIT_READ_PLATFORM,
            AI_USAGE_MANAGE,
            JOB_MARKET_VIEW));

    private final Set<Permission> permissions;

    UserRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
