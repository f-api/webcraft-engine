package com.gameexpert.bootstrap;

import java.io.IOException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

public final class WebcraftDefaults implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            var resource = new ClassPathResource("META-INF/webcraft-defaults.properties", getClass().getClassLoader());
            var defaults = PropertiesLoaderUtils.loadProperties(resource);
            environment.getPropertySources().addLast(new PropertiesPropertySource("webcraftEngineDefaults", defaults));
        } catch (IOException failure) {
            throw new IllegalStateException("WebCraft defaults could not be loaded", failure);
        }
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
