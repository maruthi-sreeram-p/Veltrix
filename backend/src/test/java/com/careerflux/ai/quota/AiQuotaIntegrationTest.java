package com.careerflux.ai.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.careerflux.user.User;
import com.careerflux.user.UserRepository;
import com.careerflux.user.UserRole;
import com.careerflux.user.UserStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI quota enforcement.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. Consumption runs in its own
 * transaction and the concurrency test needs several real threads to contend for
 * the same counter row — both of which a test-managed rollback would hide. Each
 * test therefore uses its own users and cleans up its counters.
 *
 * <p>These tests exercise {@link AiQuotaService} rather than an HTTP endpoint on
 * purpose: enforcement lives in the service layer precisely so that no route,
 * present or future, can route around it.
 */
@SpringBootTest(properties = "careerflux.ai.user-daily-quota=3")
@ActiveProfiles("test")
class AiQuotaIntegrationTest {

    @Autowired
    private AiQuotaService quotaService;

    @Autowired
    private AiUsageCounterRepository counters;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final int LIMIT = 3;

    @Test
    @DisplayName("a user within their allowance is allowed")
    void withinQuotaIsAllowed() {
        UUID user = user();

        QuotaDecision first = quotaService.tryConsume(user);
        assertThat(first.allowed()).isTrue();
        assertThat(first.dailyLimit()).isEqualTo(LIMIT);
        assertThat(first.remaining()).isEqualTo(LIMIT - 1);
    }

    @Test
    @DisplayName("the request after the allowance is spent is refused, and stays refused")
    void exhaustedQuotaIsRefusedAndStaysRefused() {
        UUID user = user();

        for (int i = 0; i < LIMIT; i++) {
            assertThat(quotaService.tryConsume(user).allowed()).isTrue();
        }

        QuotaDecision refused = quotaService.tryConsume(user);
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.reason()).isEqualTo(QuotaDecision.Reason.STUDENT_QUOTA_EXHAUSTED);
        assertThat(refused.message()).contains("resets tomorrow");

        // Still refused, and the counter has not crept past the limit.
        assertThat(quotaService.tryConsume(user).allowed()).isFalse();
        assertThat(used(AiQuotaScope.USER, user)).isEqualTo(LIMIT);
    }

    @Test
    @DisplayName("one user exhausting their allowance does not affect another")
    void quotasAreIsolatedPerUser() {
        UUID alice = user();
        UUID bob = user();

        for (int i = 0; i < LIMIT; i++) {
            assertThat(quotaService.tryConsume(alice).allowed()).isTrue();
        }
        assertThat(quotaService.tryConsume(alice).allowed()).isFalse();

        assertThat(quotaService.tryConsume(bob).allowed())
                .as("Bob has his own allowance")
                .isTrue();
    }

    @Test
    @DisplayName("concurrent requests cannot exceed the allowance")
    void concurrentRequestsCannotOverspend() throws Exception {
        int threads = 32;
        UUID user = user();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLine = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            attempts.add(() -> {
                // Released together so the requests genuinely overlap rather
                // than queueing behind each other's setup.
                startLine.await(10, TimeUnit.SECONDS);
                return quotaService.tryConsume(user).allowed();
            });
        }
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> attempt : attempts) {
            futures.add(pool.submit(attempt));
        }
        startLine.countDown();

        int granted = 0;
        for (Future<Boolean> future : futures) {
            if (Boolean.TRUE.equals(future.get(30, TimeUnit.SECONDS))) {
                granted++;
            }
        }
        pool.shutdownNow();

        assertThat(granted)
                .as("%d concurrent requests against a limit of %d must grant exactly %d",
                        threads, LIMIT, LIMIT)
                .isEqualTo(LIMIT);
        assertThat(used(AiQuotaScope.USER, user)).isEqualTo(LIMIT);
    }

    @Test
    @DisplayName("a failed model call returns the unit")
    void refundGivesTheUnitBack() {
        UUID user = user();

        quotaService.tryConsume(user);
        assertThat(used(AiQuotaScope.USER, user)).isEqualTo(1);

        quotaService.refund(user);
        assertThat(used(AiQuotaScope.USER, user)).isZero();
    }

    @Test
    @DisplayName("the allowance resets on the next day")
    void quotaResetsDaily() {
        UUID user = user();

        for (int i = 0; i < LIMIT; i++) {
            assertThat(quotaService.tryConsume(user).allowed()).isTrue();
        }
        assertThat(quotaService.tryConsume(user).allowed()).isFalse();

        // Tomorrow has its own counter row, which does not exist yet — that is
        // the whole reset mechanism, with no scheduled job to go wrong. Asked
        // through the Spring bean so the transaction proxy still applies.
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        assertThat(quotaService.tryConsume(user, tomorrow).allowed())
                .as("a new day is a new allowance")
                .isTrue();
    }

    @Test
    @DisplayName("an administrator has no allowance to draw on")
    void adminHasNoQuota() {
        User admin = new User();
        admin.setEmail("quota-admin-" + System.nanoTime() + "@careerflux.local");
        admin.setFullName("Platform Operator");
        admin.setPasswordHash(passwordEncoder.encode("IntegrationTest123!"));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(admin);

        QuotaDecision decision = quotaService.tryConsume(admin.getId());
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(QuotaDecision.Reason.NO_ALLOWANCE);
    }

    @Test
    @DisplayName("status reports the remaining allowance without consuming any")
    void statusDoesNotConsume() {
        UUID user = user();

        quotaService.tryConsume(user);
        AiQuotaService.QuotaStatus before = quotaService.status(user);
        AiQuotaService.QuotaStatus after = quotaService.status(user);

        assertThat(before.remaining()).isEqualTo(LIMIT - 1);
        assertThat(after.remaining()).isEqualTo(LIMIT - 1);
        assertThat(after.dailyLimit()).isEqualTo(LIMIT);
        assertThat(after.available()).isTrue();
    }

    // ------------------------------------------------------------------

    private UUID user() {
        User user = new User();
        user.setEmail("quota-" + System.nanoTime() + "@example.com");
        user.setFullName("Quota User");
        user.setPasswordHash(passwordEncoder.encode("IntegrationTest123!"));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return users.saveAndFlush(user).getId();
    }

    private int used(AiQuotaScope scope, UUID scopeId) {
        return counters.findByScopeAndScopeIdAndUsageDate(
                        scope, scopeId, java.time.LocalDate.now(ZoneId.of("Asia/Kolkata")))
                .map(counter -> counter.getUsedCount())
                .orElse(0);
    }
}
