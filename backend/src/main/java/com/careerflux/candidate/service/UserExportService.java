package com.careerflux.candidate.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.careerflux.ai.proposal.ProfileProposalService;
import com.careerflux.ai.proposal.dto.ProposalDtos.ProposalView;
import com.careerflux.audit.AuditService;
import com.careerflux.candidate.domain.CandidateProfile;
import com.careerflux.candidate.domain.Resume;
import com.careerflux.candidate.dto.CandidateDtos.CandidateProfileResponse;
import com.careerflux.candidate.dto.CandidateDtos.ResumeSummary;
import com.careerflux.common.error.NotFoundException;
import com.careerflux.consent.ConsentService;
import com.careerflux.consent.ConsentService.ConsentEvent;
import com.careerflux.engagement.domain.JobInteraction;
import com.careerflux.engagement.repository.JobInteractionRepository;
import com.careerflux.privacy.erasure.AccountErasureService;
import com.careerflux.privacy.erasure.AccountErasureService.ErasureView;
import com.careerflux.user.User;
import com.careerflux.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything CareerFlux holds about a user that the user may see, in one
 * document, for the user.
 *
 * <p>Built only from reads the user can already make, each answering for the
 * signed-in account alone: their profile, their resumes, their own job activity,
 * their consent history and the readings proposed from their resumes. Resume
 * files are listed with the download path the user already uses, which checks
 * ownership itself; this service never reads a file.
 *
 * <p><b>Left out on purpose</b>, and said so in the document: the extracted text
 * of resumes, which is removed once it has been used, and audit records.
 */
@Service
public class UserExportService {

    static final String FORMAT = "careerflux-user-export/1";

    static final List<String> NOT_INCLUDED = List.of(
            "The extracted text of your resumes. It is removed once the suggestions made from it are answered, "
                    + "or after the retention period; your resume files themselves are listed under resumes.",
            "Audit records of actions taken by administrators or by CareerFlux itself.");

    public record UserDataExport(String format, Instant exportedAt, AccountData account,
                                 CandidateProfileResponse profile, List<ResumeData> resumes,
                                 List<JobInteractionData> jobInteractions,
                                 List<ConsentEvent> consentHistory, List<ProposalView> aiProposals,
                                 ErasureView erasureRequest, List<String> notIncluded) {
    }

    public record AccountData(UUID userId, String email, String fullName) {
    }

    /** One resume; the file itself is at {@code downloadPath}. */
    public record ResumeData(ResumeSummary resume, boolean active, String downloadPath) {
    }

    public record JobInteractionData(UUID jobId, String jobTitle, String companyName, String type,
                                     String applicationStatus, String note, Instant recordedAt) {
    }

    private final UserRepository users;
    private final CandidateProfileService profiles;
    private final CandidateMapper mapper;
    private final ResumeService resumes;
    private final JobInteractionRepository interactions;
    private final ConsentService consents;
    private final ProfileProposalService proposals;
    private final AccountErasureService erasures;
    private final AuditService audit;

    public UserExportService(UserRepository users, CandidateProfileService profiles, CandidateMapper mapper,
                             ResumeService resumes, JobInteractionRepository interactions,
                             ConsentService consents, ProfileProposalService proposals,
                             AccountErasureService erasures, AuditService audit) {
        this.users = users;
        this.profiles = profiles;
        this.mapper = mapper;
        this.resumes = resumes;
        this.interactions = interactions;
        this.consents = consents;
        this.proposals = proposals;
        this.erasures = erasures;
        this.audit = audit;
    }

    /** The signed-in user's export. The id comes from the session, never from a request. */
    @Transactional(readOnly = true)
    public UserDataExport exportFor(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> NotFoundException.of("Account", userId));
        CandidateProfile profile = profiles.requireByUserId(userId);

        UserDataExport export = new UserDataExport(
                FORMAT,
                Instant.now(),
                new AccountData(user.getId(), user.getEmail(), user.getFullName()),
                profiles.getProfile(userId),
                resumes.history(userId).stream().map(this::resumeData).toList(),
                interactions.findByCandidateIdOrderByCreatedAtDesc(profile.getId()).stream()
                        .map(UserExportService::interactionData).toList(),
                consents.history(userId),
                proposals.list(userId).stream().map(summary -> proposals.get(userId, summary.id())).toList(),
                erasures.statusForSelf(userId).orElse(null),
                NOT_INCLUDED);

        audit.record("DATA_EXPORTED", "User", userId, "format=" + FORMAT);
        return export;
    }

    private ResumeData resumeData(Resume resume) {
        return new ResumeData(mapper.toResumeSummary(resume), resume.isActive(),
                "/api/candidate/resumes/" + resume.getId() + "/file");
    }

    private static JobInteractionData interactionData(JobInteraction interaction) {
        return new JobInteractionData(
                interaction.getJob().getId(),
                interaction.getJob().getTitle(),
                interaction.getJob().getCompany() == null ? null : interaction.getJob().getCompany().getName(),
                interaction.getInteractionType().name(),
                interaction.getApplicationStatus() == null ? null : interaction.getApplicationStatus().name(),
                interaction.getNote(),
                interaction.getCreatedAt());
    }
}
