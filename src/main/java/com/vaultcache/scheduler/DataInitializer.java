package com.vaultcache.scheduler;

import com.vaultcache.entity.UserEntity;
import com.vaultcache.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository  userRepo;
    private final PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        if (userRepo.count() == 0) {
            seed("admin", "password123", "ADMIN");
            seed("demo",  "password123", "USER");
            log.info("=============================================");
            log.info("Default users seeded:");
            log.info("  admin / password123  (ADMIN)");
            log.info("  demo  / password123  (USER)");
            log.info("=============================================");
        }
    }

    private void seed(String username, String raw, String role) {
        userRepo.save(UserEntity.builder()
                .username(username).password(encoder.encode(raw)).role(role).build());
    }
}
