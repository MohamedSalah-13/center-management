package com.codejava.center;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeLoggingPolicyTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path APPLICATION_PROPERTIES =
            Path.of("src/main/resources/application.properties");

    private static final Pattern DIRECT_OUTPUT = Pattern.compile(
            "printStackTrace\\s*\\(|System\\.(?:out|err)\\s*\\.");

    @Test
    void productionCodeNeverReliesOnAConsoleThatJpackageDoesNotHave() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (DIRECT_OUTPUT.matcher(source).find()) {
                    offenders.add(file.toString());
                }
            }
        }

        assertThat(offenders)
                .as("طباعة مباشرة تضيع في jpackage؛ استخدم SLF4J وملف التشغيل")
                .isEmpty();
    }

    @Test
    void productionLoggingIsBoundedAndDoesNotPrintSqlOrBindValues() throws IOException {
        String properties = Files.readString(APPLICATION_PROPERTIES, StandardCharsets.UTF_8);

        assertThat(properties).contains(
                "spring.jpa.show-sql=false",
                "logging.file.name=",
                "logging.logback.rollingpolicy.max-file-size=10MB",
                "logging.logback.rollingpolicy.max-history=30",
                "logging.logback.rollingpolicy.total-size-cap=200MB",
                "logging.level.org.hibernate.orm.jdbc.bind=OFF");
        assertThat(properties).doesNotContain("spring.jpa.show-sql=true");
    }
}
