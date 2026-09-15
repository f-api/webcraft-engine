package com.gameexpert.bootstrap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

public final class EngineComponentFilter implements TypeFilter {
    static final Set<String> COMPONENTS = load();
    private static Set<String> load() {
        try (var input = new ClassPathResource("META-INF/webcraft-components.txt").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank()).collect(Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
    @Override
    public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
        return COMPONENTS.contains(reader.getClassMetadata().getClassName());
    }
}
