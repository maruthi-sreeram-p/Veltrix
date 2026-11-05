package com.careerflux.security.access;

import java.util.UUID;

import com.careerflux.candidate.domain.CandidateProfile;
import com.careerflux.common.error.ForbiddenException;
import com.careerflux.common.error.NotFoundException;
import com.careerflux.security.AuthenticatedUser;
import com.careerflux.security.CurrentUser;
import com.careerflux.user.Permission;
import com.careerflux.user.UserRole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The single place resource-level authorization is decided.
 *
 * <p>Role checks answer "may this kind of user do this kind of thing?" and are
 * handled by {@code @PreAuthorize}. This class answers the harder question:
 * "may <em>this</em> user act on <em>this</em> record?"
 *
 * <p>On the individual platform the answer for a profile or resume is simple and
 * absolute: only its owner. Nobody else, including an administrator, reads it.
 *
 * <p><b>Access to someone else's record is reported as not-found, never as
 * forbidden.</b> A 403 confirms the record exists, which is itself a disclosure.
 *
 * <p><b>Every denial is logged.</b> A failure here is either a bug or an attempt.
 */
@Component
public class AccessGuard {

    private static final Logger log = LoggerFactory.getLogger(AccessGuard.class);

    private final CurrentUser currentUser;

    public AccessGuard(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public UUID currentUserId() {
        return currentUser.requireId();
    }

    public UserRole currentRole() {
        return currentUser.require().getRole();
    }

    public void requirePermission(Permission permission) {
        AuthenticatedUser principal = currentUser.require();
        if (!principal.hasPermission(permission)) {
            // The account id, never the address: a denial log is read by whoever
            // operates the platform, and a user's email is not theirs to read.
            log.warn("Denied {} to user {} (role {})", permission, principal.getUserId(), principal.getRole());
            throw new ForbiddenException("You do not have permission to do that.");
        }
    }

    /** Confirms the caller owns this candidate profile. */
    public void requireCanReadCandidate(CandidateProfile candidate) {
        if (candidate.getUser() != null && currentUser.requireId().equals(candidate.getUser().getId())) {
            return;
        }
        log.warn("User {} attempted to read candidate {} that is not theirs",
                currentUser.requireId(), candidate.getId());
        throw NotFoundException.of("Candidate", candidate.getId());
    }

    /** Confirms the caller owns this candidate's resume. */
    public void requireCanReadResume(CandidateProfile candidate) {
        if (candidate.getUser() != null && currentUser.requireId().equals(candidate.getUser().getId())) {
            return;
        }
        log.warn("User {} attempted to read the resume of candidate {} that is not theirs",
                currentUser.requireId(), candidate.getId());
        throw NotFoundException.of("Resume", candidate.getId());
    }

    /** Only the owner may change their own profile. */
    public void requireIsSelf(UUID userId, String resourceLabel, Object resourceId) {
        if (!currentUser.requireId().equals(userId)) {
            log.warn("User {} attempted to modify {} {} belonging to {}",
                    currentUser.requireId(), resourceLabel, resourceId, userId);
            throw NotFoundException.of(resourceLabel, resourceId);
        }
    }
}
