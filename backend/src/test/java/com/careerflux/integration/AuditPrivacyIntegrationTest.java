package com.careerflux.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.careerflux.audit.AuditEvent;
import com.careerflux.audit.AuditEventRepository;
import com.careerflux.audit.AuditService;
import com.careerflux.security.AuthenticatedUser;
import com.careerflux.user.User;
import com.careerflux.user.UserRepository;
import com.careerflux.user.UserRole;
import com.careerflux.user.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may read the audit trail, and what it says about people.
 *
 * <p>Only an administrator reads the platform trail. It names nobody by email;
 * a person is an account id and a role.
 *
 * <p>Audit rows are written in their own transaction and outlive each test's
 * rollback, so every assertion finds its own rows by a marker unique to the
 * test rather than by counting.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditPrivacyIntegrationTest {

    private static final String PASSWORD = "IntegrationTest123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String unique;

    @BeforeEach
    void setUp() {
        unique = Long.toString(System.nanoTime());
    }

    @Test
    @DisplayName("an administrator's own work is in the trail, attributed by role and id")
    void adminWorkIsAttributedByRoleAndId() throws Exception {
        User admin = admin("audit-operator");
        String action = "PLATFORM_CHECK_" + unique;
        runAs(admin, () -> auditService.record(action, "Platform", UUID.randomUUID(), "operator work"));

        JsonNode page = json(get("/api/admin/ops/audit?size=200"), login(admin), 200);
        JsonNode row = rowWith(page, action, null);

        assertThat(row).isNotNull();
        assertThat(row.get("actorUserId").asText()).isEqualTo(admin.getId().toString());
        assertThat(row.get("actorRole").asText()).isEqualTo("ADMIN");
        assertThat(page.toString()).describedAs("no address anywhere in the trail").doesNotContain("@");
    }

    @Test
    @DisplayName("a user cannot read the platform trail, and neither can an anonymous caller")
    void onlyAnAdminReadsTheTrail() throws Exception {
        String user = register("audit-user-" + unique + "@example.com");

        mockMvc.perform(get("/api/admin/ops/audit").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/ops/audit")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a registration names the user by id and role, never by address")
    void registrationNamesTheUserByIdAndRole() throws Exception {
        String email = "audit-registers-" + unique + "@example.com";
        register(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        AuditEvent registered = row("USER_REGISTERED", user.getId());
        assertThat(registered.getActorUserId()).isEqualTo(user.getId());
        assertThat(registered.getActorRole()).isEqualTo("USER");
        assertThat(registered.getActor()).doesNotContain("@");
    }

    @Test
    @DisplayName("a reset request is not attributed to the account holder")
    void resetRequestIsNotAttributed() throws Exception {
        String email = "audit-reset-" + unique + "@example.com";
        register(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().is2xxSuccessful());

        AuditEvent requested = row("PASSWORD_RESET_REQUESTED", user.getId());
        // Anybody may ask for a reset for any address, so it is not the user's act.
        assertThat(requested.getActorUserId()).isNull();
        assertThat(requested.getActor()).isEqualTo("unauthenticated");
    }

    @Test
    @DisplayName("text written before the change never shows an address")
    void olderAddressesAreRemovedOnTheWayOut() throws Exception {
        AuditEvent legacy = new AuditEvent();
        legacy.setActor("legacy.person@example.com");
        legacy.setAction("LEGACY_" + unique);
        legacy.setDetail("reviewed by legacy.person@example.com");
        legacy.setOccurredAt(Instant.now());
        auditRepository.saveAndFlush(legacy);

        String admin = login(admin("audit-admin-legacy"));
        JsonNode row = rowWith(json(get("/api/admin/ops/audit?size=200"), admin, 200), "LEGACY_" + unique, null);

        assertThat(row).isNotNull();
        assertThat(row.toString()).doesNotContain("@").contains("[address removed]");
    }

    // --------------------------------------------------------------- helpers

    private AuditEvent row(String action, UUID entityId) {
        return auditRepository.findAll().stream()
                .filter(event -> action.equals(event.getAction())
                        && entityId.toString().equals(event.getEntityId()))
                .findFirst().orElseThrow();
    }

    /** The row with this action, and this text in its detail when one is named. */
    private static JsonNode rowWith(JsonNode page, String action, String detailContains) {
        List<JsonNode> rows = new ArrayList<>();
        page.get("content").forEach(rows::add);
        return rows.stream()
                .filter(row -> action.equals(row.get("action").asText()))
                .filter(row -> detailContains == null
                        || (row.hasNonNull("detail") && row.get("detail").asText().contains(detailContains)))
                .findFirst().orElse(null);
    }

    private void runAs(User user, Runnable action) {
        AuthenticatedUser principal = new AuthenticatedUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String register(String email) throws Exception {
        return json(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":\"Audit User\"}"
                        .formatted(email, PASSWORD)), null, 201).get("accessToken").asText();
    }

    private User admin(String name) {
        User user = new User();
        user.setEmail(name + "-" + unique + "@careerflux.local");
        user.setFullName("Administrator");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private String login(User user) throws Exception {
        return json(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user.getEmail(), PASSWORD)),
                null, 200).get("accessToken").asText();
    }

    private JsonNode json(MockHttpServletRequestBuilder request, String token, int expected) throws Exception {
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return objectMapper.readTree(mockMvc.perform(request)
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }
}
