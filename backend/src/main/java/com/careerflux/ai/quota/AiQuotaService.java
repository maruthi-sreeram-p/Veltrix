package com.careerflux.ai.quota;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.careerflux.user.User;
import com.careerflux.user.UserRole;
import com.careerflux.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decides whether an AI request may proceed, and records it if so.
 *
 * <p>Each user has a daily allowance, {@code careerflux.ai.user-daily-quota}. An
 * administrator has none: they do not use AI features on their own behalf.
 *
 * <p><b>Where enforcement lives.</b> Here, in the service layer, on the server.
 * There is no client-side check to bypass and no request field that influences
 * the decision: the subject comes from the authenticated user id the caller
 * already holds, and the limit comes from configuration.
 *
 * <p><b>How concurrency is handled.</b> Consumption is a single conditional
 * {@code UPDATE} that both tests the limit and increments the counter. Two
 * simultaneous requests contend for one row and the database picks a winner; the
 * loser sees zero rows updated and is refused. Nothing reads a count and then
 * writes it back, which is the pattern that would let both through.
 *
 * <p><b>What happens on exhaustion.</b> This class refuses; it does not decide
 * what the caller does about it. Features with an honest non-AI fallback — the
 * heuristic resume parser, the rules-based match narrative — fall back and say
 * so. Features without one surface the refusal.
 *
 * <p>Background work is deliberately outside this. Job enrichment during
 * ingestion is platform cost against a global corpus and belongs to no student,
 * so charging it to whoever happened to trigger a sync would be arbitrary.
 */
@Service
public class AiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaService.class);

    /**
     * Quota days follow the Indian day, not the server's, so a UTC day boundary
     * does not reset allowances in the middle of the afternoon.
     */
    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Kolkata");

    private final AiUsageCounterRepository counters;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final int userDailyQuota;

    public AiQuotaService(AiUsageCounterRepository counters,
                          UserRepository userRepository,
                          PlatformTransactionManager transactionManager,
                          @Value("${careerflux.ai.user-daily-quota:20}") int userDailyQuota) {
        this.userDailyQuota = userDailyQuota;
        this.counters = counters;
        this.userRepository = userRepository;
        // An explicit template rather than @Transactional, for two reasons.
        // Each step needs its own short transaction — a losing insert must roll
        // back without poisoning the rest of the decision — and a self-invoked
        // annotated method would not get a proxy, so the annotation would
        // silently do nothing. MatchingService uses the same approach for the
        // same reason.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** The quota day in the Indian timezone. */
    private LocalDate today() {
        return LocalDate.now(QUOTA_ZONE);
    }

    /**
     * Consumes one AI request for this user.
     *
     * <p>Runs in its own transaction so the consumption is durable whether or not
     * the surrounding work commits. An AI call that was made must stay counted
     * even if the request that made it later fails, otherwise a caller could burn
     * budget and then roll the record away.
     *
     * @return the outcome, never null
     */
    public QuotaDecision tryConsume(UUID userId) {
        return tryConsume(userId, today());
    }

    /**
     * Consumes against an explicit day.
     *
     * <p>Exists so a test can ask for tomorrow without waiting for midnight, and
     * so it can do that through the Spring bean — a hand-built instance would
     * have no transaction proxy, and the counter updates need one.
     */
    public QuotaDecision tryConsume(UUID userId, LocalDate today) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return QuotaDecision.denied(QuotaDecision.Reason.UNKNOWN_USER, 0, 0);
        }
        if (user.getRole() != UserRole.USER) {
            // An administrator has no allowance to draw on. Refusing is correct:
            // nobody has agreed to pay for it.
            return QuotaDecision.denied(QuotaDecision.Reason.NO_ALLOWANCE, 0, 0);
        }

        int studentLimit = userDailyQuota;
        if (studentLimit <= 0) {
            return QuotaDecision.denied(QuotaDecision.Reason.STUDENT_QUOTA_EXHAUSTED, 0, 0);
        }

        if (!consume(AiQuotaScope.USER, userId, today, studentLimit)) {
            log.debug("AI request refused: user {} has used their daily quota of {}", userId, studentLimit);
            return QuotaDecision.denied(QuotaDecision.Reason.STUDENT_QUOTA_EXHAUSTED, 0, studentLimit);
        }

        int used = usedBy(AiQuotaScope.USER, userId, today);
        return QuotaDecision.allowed(Math.max(0, studentLimit - used), studentLimit);
    }

    /**
     * Returns a consumed unit after the AI call itself failed.
     *
     * <p>A model that was unreachable did no work and should not cost a student
     * their allowance.
     */
    public void refund(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() != UserRole.USER) {
            return;
        }
        LocalDate today = today();
        inTransaction(() -> counters.refundOne(AiQuotaScope.USER, userId, today, Instant.now()));
    }

    /** What a student has left today, for display. Never consumes anything. */
    @Transactional(readOnly = true)
    public QuotaStatus status(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() != UserRole.USER) {
            return new QuotaStatus(0, 0, false);
        }
        int limit = userDailyQuota;
        int used = usedBy(AiQuotaScope.USER, userId, today());
        return new QuotaStatus(Math.max(0, limit - used), limit, used < limit);
    }

    /**
     * Attempts the conditional increment, creating the day's row the first time.
     *
     * <p>The insert can lose a race with another request creating the same row.
     * That is expected rather than exceptional: the unique constraint rejects the
     * duplicate and the retry consumes from whichever row won.
     */
    /**
     * Takes one unit if the allowance allows it.
     *
     * <p>Each database step runs in its own short transaction and holds one
     * connection at a time. Nesting them would double the connections a single
     * request needs, which under a burst exhausts the pool and deadlocks — the
     * requests waiting for a connection are holding the ones the others need.
     */
    private boolean consume(AiQuotaScope scope, UUID scopeId, LocalDate day, int limit) {
        if (inTransaction(() -> counters.consumeOne(scope, scopeId, day, limit, Instant.now())) == 1) {
            return true;
        }
        // Zero rows updated means either the allowance is spent or there is no
        // counter for today yet. Only the second is recoverable.
        if (inTransaction(() -> counters.findByScopeAndScopeIdAndUsageDate(scope, scopeId, day)
                .isPresent() ? 1 : 0) == 1) {
            return false;
        }
        createCounter(scope, scopeId, day);
        return inTransaction(() -> counters.consumeOne(scope, scopeId, day, limit, Instant.now())) == 1;
    }

    /**
     * Creates the day's counter, tolerating a lost race.
     *
     * <p>Several requests can arrive for a student with no counter yet; they all
     * see no row and all insert, and the unique constraint rejects every one but
     * the first. That rollback is contained in this transaction, so the callers
     * that lost simply carry on and consume from the row that won.
     */
    private void createCounter(AiQuotaScope scope, UUID scopeId, LocalDate day) {
        try {
            inTransaction(() -> {
                counters.saveAndFlush(new AiUsageCounter(scope, scopeId, day, 0));
                return 1;
            });
        } catch (DataIntegrityViolationException lostTheRace) {
            // Another request created it first, which is all this needed.
        }
    }

    private int inTransaction(java.util.function.Supplier<Integer> work) {
        Integer result = transactionTemplate.execute(status -> work.get());
        return result == null ? 0 : result;
    }

    private int usedBy(AiQuotaScope scope, UUID scopeId, LocalDate day) {
        return counters.findByScopeAndScopeIdAndUsageDate(scope, scopeId, day)
                .map(AiUsageCounter::getUsedCount)
                .orElse(0);
    }

    /** What a student has left, for showing in the interface. */
    public record QuotaStatus(int remaining, int dailyLimit, boolean available) {
    }
}
