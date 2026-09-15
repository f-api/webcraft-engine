package com.gameexpert.bootstrap;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@org.springframework.scheduling.annotation.EnableScheduling
@AutoConfiguration
@ComponentScan(basePackages = "com.gameexpert", useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM,
                classes = EngineComponentFilter.class))
public class WebcraftAutoConfiguration {
}
