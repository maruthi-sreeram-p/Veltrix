package com.careerflux.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.careerflux.engagement.service.EngagementService;
import com.careerflux.job.domain.Job;
import com.careerflux.job.domain.JobStatus;
import com.careerflux.job.repository.JobRepository;
import com.careerflux.privacy.erasure.AccountErasureService;
import com.careerflux.privacy.erasure.AccountErasureService.ErasureRunReport;
import com.careerflux.privacy.erasure.ErasureExecutor;
import com.careerflux.source.domain.Company;
import com.careerflux.source.repository.CompanyRepository;
import com.careerflux.support.PrivacyFixture;
import com.careerflux.support.PrivacyFixture.Account;
import com.careerflux.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Account erasure (Phase 2B, F10 / R22 / R23 / L-2 / PD-7): a grace period that
 * removes nothing, then anonymisation in place that removes the student and
 * keeps the college's placement history.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountErasureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PrivacyFixture fixture;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccountErasureService erasures;

    @Autowired
    private EngagementService engagement;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private CompanyRepository companies;

    private Account kiran;
    private String kiranToken;
    private UUID companyId;
    private UUID jobId;

    @BeforeEach
    void setUp() throws Exception {
        fixture.begin();
        kiran = fixture.user("Kiran Rao");
        kiranToken = fixture.login(mockMvc, kiran.email());
    }

    @AfterEach
    void tearDown() {
        fixture.cleanUp();
        if (jobId != null) {
            jdbc.update("delete from jobs where id = ?", jobId);
            jdbc.update("delete from companies where id = ?", companyId);
        }
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, int expected) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : objectMapper.readTree(body);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static Instant daysAfter(long days) {
        return Instant.now().plus(Duration.ofDays(days));
    }

    @Nested
    @DisplayName("requesting")
    class Requesting {

        @Test
        @DisplayName("a student's request removes nothing and waits out a thirty-day grace period")
        void graceFirst() throws Exception {
            JsonNode request = call(post("/api/candidate/erasure"), kiranToken, 200);

            assertThat(request.get("status").asText()).isEqualTo("GRACE_PERIOD");
            assertThat(request.get("cancellable").asBoolean()).isTrue();
            Instant requested = Instant.parse(request.get("requestedAt").asText());
            Instant graceEnds = Instant.parse(request.get("graceEndsAt").asText());
            assertThat(Duration.between(requested, graceEnds)).isEqualTo(Duration.ofDays(30));

            erasures.processDue(Instant.now(), false, 200);

            assertThat(call(get("/api/candidate/erasure"), kiranToken, 200).get("status").asText())
                    .isEqualTo("GRACE_PERIOD");
            assertThat(fixture.login(mockMvc, kiran.email())).isNotBlank();
        }

        @Test
        @DisplayName("asking twice is the same request")
        void idempotent() throws Exception {
            String first = call(post("/api/candidate/erasure"), kiranToken, 200).get("id").asText();
            String second = call(post("/api/candidate/erasure"), kiranToken, 200).get("id").asText();

            assertThat(second).isEqualTo(first);
        }

        @Test
        @DisplayName("a request cancelled during the grace period is never carried out, and a new one can be made")
        void cancellation() throws Exception {
            call(post("/api/candidate/erasure"), kiranToken, 200);
            assertThat(call(post("/api/candidate/erasure/cancel"), kiranToken, 200).get("status").asText())
                    .isEqualTo("CANCELLED");

            erasures.processDue(daysAfter(31), false, 200);

            assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, kiran.userId()))
                    .isEqualTo(kiran.email());
            assertThat(call(post("/api/candidate/erasure"), kiranToken, 200).get("status").asText())
                    .isEqualTo("GRACE_PERIOD");
        }

        @Test
        @DisplayName("an administrator cannot request erasure, and another user's request is only their own")
        void nobodyElseMay() throws Exception {
            Account admin = fixture.admin();
            Account other = fixture.user("Vikram Singh");

            call(post("/api/candidate/erasure"), fixture.login(mockMvc, admin.email()), 403);
            call(post("/api/candidate/erasure"), fixture.login(mockMvc, other.email()), 200);

            assertThat(count("select count(*) from account_erasures where subject_user_id = ?", kiran.userId()))
                    .isZero();
            assertThat(count("select count(*) from account_erasures where subject_user_id = ?", other.userId()))
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("carrying it out")
    class CarryingOut {

        /** A student with something in every category erasure deals with. */
        private void giveKiranAHistory() throws Exception {
            call(put("/api/candidate/profile").contentType(MediaType.APPLICATION_JSON).content("""
                    {"headline":"Mechatronics enthusiast","phone":"+91 98480 22338","location":"Warangal",
                     "linkedinUrl":"https://linkedin.com/in/kiran-rao",
                     "skills":[{"name":"Java"},{"name":"SolidWorks Pro"}]}
                    """), kiranToken, 200);
            mockMvc.perform(multipart("/api/candidate/resume").file(new MockMultipartFile("file", "kiran-rao.txt",
                            "text/plain", "Kiran Rao\n+91 98480 22338\n\nSKILLS\nJava, SQL\n".getBytes()))
                            .header("Authorization", "Bearer " + kiranToken))
                    .andExpect(status().isOk());
            String consents = mockMvc.perform(get("/api/consents").header("Authorization", "Bearer " + kiranToken))
                    .andReturn().getResponse().getContentAsString();
            String noticeId = objectMapper.readTree(consents).get("purposes").get(2).get("currentNotice").get("id")
                    .asText();
            call(post("/api/consents/ai-processing/accept").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"noticeVersionId\":\"" + noticeId + "\"}"), kiranToken, 200);

            Company company = new Company();
            company.setName("Warangal Robotics");
            company.setSlug("warangal-robotics-" + UUID.randomUUID());
            companyId = fixture.inTransaction(() -> companies.saveAndFlush(company).getId());
            jobId = fixture.inTransaction(() -> {
                Job job = new Job();
                job.setCompany(companies.findById(companyId).orElseThrow());
                job.setCanonicalKey("erasure-test-" + UUID.randomUUID());
                job.setTitle("Graduate Robotics Engineer");
                job.setNormalizedTitle("graduate robotics engineer");
                job.setSearchText("graduate robotics engineer");
                job.setStatus(JobStatus.OPEN);
                job.setFirstObservedAt(Instant.now());
                job.setLastObservedAt(Instant.now());
                return jobs.saveAndFlush(job).getId();
            });
            engagement.save(kiran.userId(), jobId, "Apply after exams");
            jdbc.update("insert into notifications (id, user_id, category, priority, title, created_at) "
                    + "values (?, ?, 'MATCH', 'HIGH', 'A new match', ?)", UUID.randomUUID(), kiran.userId(),
                    Timestamp.from(Instant.now()));
        }

        @Test
        @DisplayName("after the grace period the user's identity and data are gone")
        void erasedButHistoryKept() throws Exception {
            giveKiranAHistory();
            call(post("/api/candidate/erasure"), kiranToken, 200);

            ErasureRunReport report = erasures.processDue(daysAfter(31), false, 200);
            assertThat(report.completed()).isPositive();

            // The account: a tombstone that can never be used again.
            Map<String, Object> account = jdbc.queryForMap("select email, full_name, status, sessions_valid_after "
                    + "from users where id = ?", kiran.userId());
            assertThat((String) account.get("email")).startsWith("erased+").endsWith("@erased.invalid");
            assertThat(account.get("full_name")).isEqualTo(ErasureExecutor.ERASED_NAME);
            assertThat(account.get("status")).isEqualTo("ERASED");
            assertThat(account.get("sessions_valid_after")).isNotNull();
            call(get("/api/candidate/profile"), kiranToken, 401);
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + kiran.email() + "\",\"password\":\"" + PrivacyFixture.PASSWORD
                                    + "\"}"))
                    .andExpect(status().isForbidden());

            // The profile: stripped.
            Map<String, Object> profile = jdbc.queryForMap("select headline, phone, location, linkedin_url "
                    + "from candidate_profiles where id = ?", kiran.profileId());
            assertThat(profile.values()).containsOnlyNulls();

            // Everything that belonged to the student alone.
            for (String table : List.of("resumes", "ai_profile_proposals", "candidate_skills",
                    "candidate_custom_skills", "candidate_preferences", "job_interactions", "job_matches")) {
                assertThat(count("select count(*) from " + table + " where candidate_id = ?", kiran.profileId()))
                        .describedAs(table).isZero();
            }
            assertThat(count("select count(*) from notifications where user_id = ?", kiran.userId())).isZero();
            assertThat(Files.exists(fixture.resumeRoot().resolve(kiran.profileId().toString()))).isFalse();

            // Evidence kept, identity not.
            assertThat(count("select count(*) from consent_records where user_id = ?", kiran.userId())).isEqualTo(1);
            assertThat(count("select count(*) from audit_events where action = 'CONSENT_ACCEPTED' and actor_user_id = ?",
                    kiran.userId())).isEqualTo(1);
            assertThat(count("select count(*) from audit_events where action = 'ERASURE_COMPLETED' and entity_id = ?",
                    kiran.userId().toString())).isEqualTo(1);
            String counts = jdbc.queryForObject("select category_counts from account_erasures where subject_user_id = ?",
                    String.class, kiran.userId());
            assertThat(counts).contains("\"resumes\":1").doesNotContain("Kiran").doesNotContain("@")
                    .doesNotContain("98480");
        }

        @Test
        @DisplayName("in dry-run mode a due request is reported and left waiting, with nothing removed")
        void dryRunLeavesItWaiting() throws Exception {
            call(post("/api/candidate/erasure"), kiranToken, 200);

            ErasureRunReport report = erasures.processDue(daysAfter(31), true, 200);

            assertThat(report.dryRun()).isTrue();
            assertThat(report.due()).isPositive();
            assertThat(report.completed()).isZero();
            assertThat(jdbc.queryForObject("select status from account_erasures where subject_user_id = ?",
                    String.class, kiran.userId())).isEqualTo("GRACE_PERIOD");
            assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, kiran.userId()))
                    .isEqualTo(kiran.email());
        }
    }
}
