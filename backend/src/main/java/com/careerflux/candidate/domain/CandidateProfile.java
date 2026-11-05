package com.careerflux.candidate.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.careerflux.common.BaseEntity;
import com.careerflux.common.taxonomy.Seniority;
import com.careerflux.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * The candidate's career intelligence profile: everything CareerFlux knows about
 * who they are and what they are looking for. Populated from a resume, then
 * corrected by the candidate; both paths write the same fields.
 */
@Entity
@Table(name = "candidate_profiles")
public class CandidateProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "headline", length = 200)
    private String headline;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "location", length = 160)
    private String location;

    @Column(name = "phone", length = 40)
    private String phone;

    /** Where match alerts are also emailed. Optional; entering it is the opt-in. */
    @Column(name = "personal_email", length = 254)
    private String personalEmail;

    /** SHA-256 of the pending confirmation token; the raw token exists only in the email. */
    @Column(name = "personal_email_token_hash", length = 64)
    private String personalEmailTokenHash;

    @Column(name = "personal_email_token_expires_at")
    private java.time.Instant personalEmailTokenExpiresAt;

    /** Set when the owner followed the emailed link. Alerts are sent only when it is set. */
    @Column(name = "personal_email_verified_at")
    private java.time.Instant personalEmailVerifiedAt;

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    @Column(name = "github_url", length = 300)
    private String githubUrl;

    @Column(name = "portfolio_url", length = 300)
    private String portfolioUrl;

    @Column(name = "primary_role", length = 120)
    private String primaryRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority", length = 32)
    private Seniority seniority = Seniority.UNSPECIFIED;

    @Column(name = "years_experience", precision = 4, scale = 1)
    private BigDecimal yearsExperience;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_stage", nullable = false, length = 32)
    private OnboardingStage onboardingStage = OnboardingStage.RESUME_UPLOAD;

    @Column(name = "profile_completeness", nullable = false)
    private int profileCompleteness;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CandidateSkill> skills = new ArrayList<>();

    /**
     * Skills this student named that the shared dictionary does not know. Kept
     * here and nowhere else: never added to the dictionary, never shown to
     * staff, never matched. See {@link CandidateCustomSkill}.
     */
    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CandidateCustomSkill> customSkills = new ArrayList<>();

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<CandidateExperience> experiences = new ArrayList<>();

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<CandidateEducation> education = new ArrayList<>();

    @OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CandidatePreferences preferences;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPersonalEmail() {
        return personalEmail;
    }

    public void setPersonalEmail(String personalEmail) {
        this.personalEmail = personalEmail;
    }

    public String getPersonalEmailTokenHash() {
        return personalEmailTokenHash;
    }

    public void setPersonalEmailTokenHash(String personalEmailTokenHash) {
        this.personalEmailTokenHash = personalEmailTokenHash;
    }

    public java.time.Instant getPersonalEmailTokenExpiresAt() {
        return personalEmailTokenExpiresAt;
    }

    public void setPersonalEmailTokenExpiresAt(java.time.Instant personalEmailTokenExpiresAt) {
        this.personalEmailTokenExpiresAt = personalEmailTokenExpiresAt;
    }

    public java.time.Instant getPersonalEmailVerifiedAt() {
        return personalEmailVerifiedAt;
    }

    public void setPersonalEmailVerifiedAt(java.time.Instant personalEmailVerifiedAt) {
        this.personalEmailVerifiedAt = personalEmailVerifiedAt;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }

    public void setPortfolioUrl(String portfolioUrl) {
        this.portfolioUrl = portfolioUrl;
    }

    public String getPrimaryRole() {
        return primaryRole;
    }

    public void setPrimaryRole(String primaryRole) {
        this.primaryRole = primaryRole;
    }

    public Seniority getSeniority() {
        return seniority;
    }

    public void setSeniority(Seniority seniority) {
        this.seniority = seniority == null ? Seniority.UNSPECIFIED : seniority;
    }

    public BigDecimal getYearsExperience() {
        return yearsExperience;
    }

    public void setYearsExperience(BigDecimal yearsExperience) {
        this.yearsExperience = yearsExperience;
    }

    public OnboardingStage getOnboardingStage() {
        return onboardingStage;
    }

    public void setOnboardingStage(OnboardingStage onboardingStage) {
        this.onboardingStage = onboardingStage;
    }

    public int getProfileCompleteness() {
        return profileCompleteness;
    }

    public void setProfileCompleteness(int profileCompleteness) {
        this.profileCompleteness = profileCompleteness;
    }

    public List<CandidateSkill> getSkills() {
        return skills;
    }

    public List<CandidateCustomSkill> getCustomSkills() {
        return customSkills;
    }

    public List<CandidateExperience> getExperiences() {
        return experiences;
    }

    public List<CandidateEducation> getEducation() {
        return education;
    }

    public CandidatePreferences getPreferences() {
        return preferences;
    }

    public void setPreferences(CandidatePreferences preferences) {
        this.preferences = preferences;
        if (preferences != null) {
            preferences.setCandidate(this);
        }
    }

}
