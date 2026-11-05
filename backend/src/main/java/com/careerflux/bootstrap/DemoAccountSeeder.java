package com.careerflux.bootstrap;

import com.careerflux.candidate.service.CandidateProfileService;
import com.careerflux.user.User;
import com.careerflux.user.UserRepository;
import com.careerflux.user.UserRole;
import com.careerflux.user.UserStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates one demonstration user and one demonstration administrator so a fresh
 * checkout has something to sign in to.
 *
 * <p>Runs only under the {@code demo} profile, and only when
 * {@code CAREERFLUX_DEMO_PASSWORD} is set: shipping working credentials in
 * source would be a backdoor, whatever the profile is called. Both accounts share
 * that one password and are skipped if they already exist.
 */
@Component
@Profile("demo")
@Order(4)
public class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    public static final String DEMO_USER_EMAIL = "user@demo.careerflux.local";
    public static final String DEMO_ADMIN_EMAIL = "admin@demo.careerflux.local";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CandidateProfileService candidateProfileService;
    private final String demoPassword;

    public DemoAccountSeeder(UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             CandidateProfileService candidateProfileService,
                             @Value("${CAREERFLUX_DEMO_PASSWORD:}") String demoPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.candidateProfileService = candidateProfileService;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(demoPassword)) {
            log.info("No demo accounts created. Set CAREERFLUX_DEMO_PASSWORD to create a demo user "
                    + "and a demo administrator.");
            return;
        }
        if (!userRepository.existsByEmailIgnoreCase(DEMO_USER_EMAIL)) {
            User user = account(DEMO_USER_EMAIL, "Demo User", UserRole.USER);
            candidateProfileService.createForUser(user);
        }
        if (!userRepository.existsByEmailIgnoreCase(DEMO_ADMIN_EMAIL)) {
            account(DEMO_ADMIN_EMAIL, "Demo Administrator", UserRole.ADMIN);
        }
        log.info("Demo accounts are available: {} and {}", DEMO_USER_EMAIL, DEMO_ADMIN_EMAIL);
    }

    private User account(String email, String name, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(name);
        user.setPasswordHash(passwordEncoder.encode(demoPassword));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }
}
