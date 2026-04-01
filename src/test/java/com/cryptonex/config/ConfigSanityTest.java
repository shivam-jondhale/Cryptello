package com.cryptonex.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigSanityTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProdValidationConfig.class, JwtConfig.class);

    @Test
    @DisplayName("Dev profile should start without sensitive env vars if we provide defaults")
    void testDevProfileLoads() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .withPropertyValues("jwt.secret=fake-secret-for-dev-profile-testing-only-must-be-long-enough")
                .run((context) -> {
                    assertThat(context).hasNotFailed();
                    // Dev doesn't use ProdValidationConfig, so it should be fine
                    assertThat(context).doesNotHaveBean(ProdValidationConfig.class);
                });
    }

    @Test
    @DisplayName("Prod profile should FAIL if secrets are missing")
    void testProdProfileFailsWithoutEnv() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run((context) -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("PRODUCTION STARTUP FAILED");
                });
    }

    @Test
    @DisplayName("Prod profile should START if secrets are present")
    void testProdProfileStartsWithEnv() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .withPropertyValues(
                        "jwt.secret=real-production-secret-needs-to-be-secure-and-long",
                        "spring.datasource.url=jdbc:mysql://prod-db:3306/db",
                        "stripe.api.key=sk_live_xyz",
                        "cashfree.app.id=123")
                .run((context) -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProdValidationConfig.class);
                });
    }
}
