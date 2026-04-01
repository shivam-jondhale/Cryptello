package com.cryptonex.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Profile("prod")
public class ProdValidationConfig implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(ProdValidationConfig.class);

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${stripe.api.key:}")
    private String stripeApiKey;

    @Value("${cashfree.app.id:}")
    private String cashfreeAppId;

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Validating Production Configuration...");

        List<String> missingProperties = new ArrayList<>();

        if (!StringUtils.hasText(jwtSecret) || "change-me".equals(jwtSecret)) {
            missingProperties.add("jwt.secret");
        }
        if (!StringUtils.hasText(dbUrl) || dbUrl.contains("localhost")) {
            missingProperties.add("spring.datasource.url (cannot be localhost in prod)");
        }
        if (!StringUtils.hasText(stripeApiKey)) {
            missingProperties.add("stripe.api.key");
        }
        if (!StringUtils.hasText(cashfreeAppId)) {
            missingProperties.add("cashfree.app.id");
        }

        if (!missingProperties.isEmpty()) {
            String errorMsg = "PRODUCTION STARTUP FAILED: Missing or invalid required configuration: "
                    + missingProperties;
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        logger.info("Production Configuration Validated Successfully.");
    }
}
