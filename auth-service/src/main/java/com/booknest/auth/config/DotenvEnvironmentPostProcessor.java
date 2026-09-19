package com.booknest.auth.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a {@code .env} file (KEY=VALUE per line, {@code #} comments allowed)
 * from the process's working directory and exposes its entries as regular
 * Spring {@link org.springframework.core.env.Environment} properties.
 *
 * <p>Plain Spring Boot does NOT read {@code .env} files on its own — that
 * only happens when something external (docker-compose, an IDE plugin, a
 * shell {@code source .env}) puts the values into the process environment
 * first. Without this class, values like {@code ADMIN_PASSWORD} or
 * {@code DB_PASSWORD} in {@code auth-service/.env} were silently ignored
 * whenever the app was started directly (IDE run button, {@code mvn
 * spring-boot:run}, {@code java -jar ...}), and every {@code ${VAR:default}}
 * placeholder in {@code application.yml} silently fell back to its
 * hardcoded default instead.
 *
 * <p><b>Precedence:</b> real OS/process environment variables and JVM system
 * properties still win over {@code .env} — this file only fills in values
 * that aren't already set for real, so a genuine deployment (Docker,
 * systemd, CI) that exports proper env vars is completely unaffected. If no
 * readable {@code .env} file exists, this is a silent no-op.
 *
 * <p><b>Not used in the Docker image:</b> {@link Path#of} resolves relative
 * to the JVM's working directory, and the Dockerfile does not copy a
 * {@code .env} file into the image (secrets don't belong baked into an
 * image). For Docker/production, pass real environment variables via
 * {@code docker run -e} / {@code docker-compose.yml environment:} instead —
 * this loader is purely a local-development convenience.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "dotenvFile";
    private static final String DOTENV_FILENAME = ".env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dotenvPath = Path.of(DOTENV_FILENAME);

        // Diagnostic output on System.out (not a Logger — this runs before
        // the logging system is initialised, so a Logger call here would
        // silently vanish). Look for this line right at the top of the
        // console output on every startup to confirm whether/what .env
        // actually got picked up.
        if (!Files.isReadable(dotenvPath)) {
            System.out.println(
                    "[dotenv] No readable .env found at " + dotenvPath.toAbsolutePath()
                            + " — using real env vars / application.yml defaults only. "
                            + "(On Windows, double check it isn't actually named \".env.txt\" — "
                            + "Explorer hides known extensions by default: `dir /a` in this folder to confirm.)");
            return;
        }

        Map<String, Object> values = parse(dotenvPath);
        if (values.isEmpty()) {
            System.out.println(
                    "[dotenv] Found " + dotenvPath.toAbsolutePath()
                            + " but parsed 0 key=value entries from it — check the file's contents/format.");
            return;
        }

        System.out.println(
                "[dotenv] Loaded " + values.size() + " variable(s) from " + dotenvPath.toAbsolutePath()
                        + ": " + values.keySet());

        // Inserted right after the real system environment property source,
        // so actual OS/deployment env vars still take priority, but a .env
        // entry now beats the hardcoded ${VAR:default} fallback in
        // application.yml — which is the whole point of having a .env file.
        environment.getPropertySources()
                .addAfter(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new MapPropertySource(PROPERTY_SOURCE_NAME, values));
    }

    private static Map<String, Object> parse(Path dotenvPath) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(dotenvPath);
        } catch (IOException e) {
            // Fail soft: a missing-permission or malformed .env should never
            // block startup — the app just behaves as if it weren't there.
            return values;
        }

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            // Support the common "export KEY=VALUE" style some people copy
            // in from shell scripts.
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = stripInlineComment(line.substring(eq + 1).strip());
            values.put(key, stripSurroundingQuotes(value));
        }
        return values;
    }

    /** Strips a trailing {@code # comment} on unquoted values, e.g. {@code PORT=9000 # dev only}. */
    private static String stripInlineComment(String value) {
        if (value.startsWith("\"") || value.startsWith("'")) {
            return value;
        }
        int hash = value.indexOf('#');
        return hash >= 0 ? value.substring(0, hash).strip() : value;
    }

    private static String stripSurroundingQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    @Override
    public int getOrder() {
        // Run early, well before ConfigDataEnvironmentPostProcessor resolves
        // application.yml placeholders.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
