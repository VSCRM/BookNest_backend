package com.booknest.auth.config;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * {@link DotenvEnvironmentPostProcessor} reads a real {@code .env} file from
 * the process's working directory, so all filesystem access is stubbed via
 * {@link MockedStatic} rather than relying on (or mutating) whatever
 * happens to be on disk in the module's actual working directory.
 */
class DotenvEnvironmentPostProcessorTest {

    private final DotenvEnvironmentPostProcessor processor = new DotenvEnvironmentPostProcessor();
    private final SpringApplication application = new SpringApplication();

    @Test
    void orderRunsWellBeforeConfigDataPlaceholderResolution() {
        assertThat(processor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
    }

    @Test
    void noReadableDotenvFileIsANoOp() {
        StandardEnvironment environment = new StandardEnvironment();
        int before = environment.getPropertySources().size();

        try (MockedStatic<Files> files = mockStatic(Files.class)) {
            files.when(() -> Files.isReadable(any(Path.class))).thenReturn(false);

            processor.postProcessEnvironment(environment, application);

            files.verify(() -> Files.readAllLines(any(Path.class)), never());
        }

        assertThat(environment.getPropertySources()).hasSize(before);
        assertThat(environment.getPropertySources().contains("dotenvFile")).isFalse();
    }

    @Test
    void readableButOnlyCommentsAndBlankLinesIsANoOp() {
        StandardEnvironment environment = new StandardEnvironment();
        int before = environment.getPropertySources().size();

        try (MockedStatic<Files> files = mockStatic(Files.class)) {
            files.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            files.when(() -> Files.readAllLines(any(Path.class)))
                    .thenReturn(List.of("", "   ", "# just a comment", "=novalidkey"));

            processor.postProcessEnvironment(environment, application);
        }

        assertThat(environment.getPropertySources()).hasSize(before);
        assertThat(environment.getPropertySources().contains("dotenvFile")).isFalse();
    }

    @Test
    void ioExceptionWhileReadingIsSwallowedAsNoOp() {
        StandardEnvironment environment = new StandardEnvironment();
        int before = environment.getPropertySources().size();

        try (MockedStatic<Files> files = mockStatic(Files.class)) {
            files.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            files.when(() -> Files.readAllLines(any(Path.class)))
                    .thenThrow(new IOException("permission denied"));

            processor.postProcessEnvironment(environment, application);
        }

        assertThat(environment.getPropertySources()).hasSize(before);
        assertThat(environment.getPropertySources().contains("dotenvFile")).isFalse();
    }

    @Test
    void parsesValidEntriesAndAddsThemRightAfterSystemEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();

        try (MockedStatic<Files> files = mockStatic(Files.class)) {
            files.when(() -> Files.isReadable(any(Path.class))).thenReturn(true);
            files.when(() -> Files.readAllLines(any(Path.class))).thenReturn(List.of(
                    "# a comment line, ignored",
                    "",
                    "   ",
                    "export EXPORTED_KEY=exported-value",
                    "PLAIN_KEY=plain-value",
                    "DOUBLE_QUOTED=\"hello world\"",
                    "SINGLE_QUOTED='hello single'",
                    "WITH_INLINE_COMMENT=raw-value # trailing note",
                    "QUOTED_WITH_HASH=\"value#not-a-comment\"",
                    "NO_EQUALS_SIGN_IS_SKIPPED",
                    "=STARTS_WITH_EQUALS_IS_SKIPPED",
                    "  SPACED_KEY  =  spaced-value  "
            ));

            processor.postProcessEnvironment(environment, application);
        }

        PropertySource<?> source = environment.getPropertySources().get("dotenvFile");
        assertThat(source).isInstanceOf(MapPropertySource.class);
        MapPropertySource mapSource = (MapPropertySource) source;

        assertThat(mapSource.getProperty("EXPORTED_KEY")).isEqualTo("exported-value");
        assertThat(mapSource.getProperty("PLAIN_KEY")).isEqualTo("plain-value");
        assertThat(mapSource.getProperty("DOUBLE_QUOTED")).isEqualTo("hello world");
        assertThat(mapSource.getProperty("SINGLE_QUOTED")).isEqualTo("hello single");
        assertThat(mapSource.getProperty("WITH_INLINE_COMMENT")).isEqualTo("raw-value");
        assertThat(mapSource.getProperty("QUOTED_WITH_HASH")).isEqualTo("value#not-a-comment");
        assertThat(mapSource.getProperty("SPACED_KEY")).isEqualTo("spaced-value");
        assertThat(mapSource.getPropertyNames())
                .doesNotContain("NO_EQUALS_SIGN_IS_SKIPPED", "STARTS_WITH_EQUALS_IS_SKIPPED");

        // Must sit immediately after the system-environment source, so real
        // OS/deployment env vars still win but .env beats application.yml's
        // hardcoded ${VAR:default} fallback.
        List<String> names = new ArrayList<>();
        environment.getPropertySources().forEach(ps -> names.add(ps.getName()));
        int sysEnvIndex = names.indexOf(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        int dotenvIndex = names.indexOf("dotenvFile");
        assertThat(sysEnvIndex).isNotEqualTo(-1);
        assertThat(dotenvIndex).isEqualTo(sysEnvIndex + 1);
    }
}
