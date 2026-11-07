package com.stagepass.common.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In prod, stops startup before any bean is created when a required environment variable is missing, naming every
 * one (otherwise e.g. a missing DB_URL only surfaces as "'url' must start with jdbc"). Keep {@link #REQUIRED} in step
 * with the ${NAME} placeholders without a default in application*.properties (a test checks this).
 */
@Component
@Profile("prod")
public class RequiredSettings implements BeanFactoryPostProcessor, EnvironmentAware {

    public static final List<String> REQUIRED = List.of("DB_PASSWORD", "DB_URL", "DB_USERNAME", "PAYOUT_ENCRYPTION_KEY",
            "QR_SECRET", "SUPABASE_URL", "WEB_BASE_URL");

    private Environment env;

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> missing = REQUIRED.stream()
                .filter(name -> env.getProperty(name) == null || env.getProperty(name).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required environment variables: " + String.join(", ", missing)
                    + ". Set them in the Render dashboard (see docs/deploy.md).");
        }
    }
}
