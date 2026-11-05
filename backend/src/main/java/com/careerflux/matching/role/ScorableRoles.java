package com.careerflux.matching.role;

import java.math.BigDecimal;
import java.util.List;

import com.careerflux.common.taxonomy.EmploymentType;
import com.careerflux.common.taxonomy.Seniority;
import com.careerflux.common.taxonomy.WorkMode;
import com.careerflux.job.domain.Job;

/**
 * Adapts what CareerFlux scores candidates against.
 *
 * <p>An adapter rather than making {@code Job} implement the interface itself:
 * Job is an ingestion concern and should not grow a matching interface to
 * satisfy the scorer, and keeping the mapping here means the flattening of skill
 * rows lives in one obvious place.
 */
public final class ScorableRoles {

    private ScorableRoles() {
    }

    /** An ingested market posting. */
    public static ScorableRole of(Job job) {
        return new JobRole(job);
    }

    private record JobRole(Job job) implements ScorableRole {

        @Override
        public String title() {
            return job.getTitle();
        }

        @Override
        public String normalizedTitle() {
            return job.getNormalizedTitle();
        }

        @Override
        public Seniority seniority() {
            return job.getSeniority();
        }

        @Override
        public WorkMode workMode() {
            return job.getWorkMode();
        }

        @Override
        public String locationRaw() {
            return job.getLocationRaw();
        }

        @Override
        public String city() {
            return job.getCity();
        }

        @Override
        public EmploymentType employmentType() {
            return job.getEmploymentType();
        }

        @Override
        public BigDecimal minExperienceYears() {
            return job.getMinExperienceYears();
        }

        @Override
        public BigDecimal maxExperienceYears() {
            return job.getMaxExperienceYears();
        }

        @Override
        public List<RoleSkill> skills() {
            return job.getSkills().stream()
                    .map(skill -> new RoleSkill(skill.getSkill().getSlug(),
                            skill.getSkill().getCanonicalName(), skill.getRequirement()))
                    .toList();
        }

        @Override
        public BigDecimal salaryMin() {
            return job.getSalaryMin();
        }

        @Override
        public BigDecimal salaryMax() {
            return job.getSalaryMax();
        }

        @Override
        public String salaryCurrency() {
            return job.getSalaryCurrency();
        }

        @Override
        public String salaryPeriod() {
            return job.getSalaryPeriod();
        }

        @Override
        public String descriptor() {
            return "posting";
        }
    }
}
