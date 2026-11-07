package com.stagepass.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** render.yaml must list every environment variable the API needs, and never hold a secret value. */
class RenderBlueprintTest {

    private static final Path API = Path.of("").toAbsolutePath();
    private static final Path BLUEPRINT = API.resolveSibling("render.yaml");

    /** ${NAME} placeholders that have no default in the main and prod property files. */
    static Set<String> requiredVariables() throws IOException {
        Set<String> names = new TreeSet<>();
        Pattern p = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");
        for (String file : List.of("application.properties", "application-prod.properties")) {
            Matcher m = p.matcher(Files.readString(API.resolve("src/main/resources/" + file)));
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    /** env var key → the line after it ("sync: false", "value: …", "generateValue: true") for one service. */
    static Map<String, String> serviceEnv(String service) throws IOException {
        List<String> lines = Files.readAllLines(BLUEPRINT);
        Map<String, String> env = new LinkedHashMap<>();
        boolean inService = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (lines.get(i).startsWith("  - type:")) {
                inService = false;
            }
            if (line.equals("name: " + service)) {
                inService = true;
            }
            if (inService && line.startsWith("- key:")) {
                env.put(line.substring("- key:".length()).strip(), i + 1 < lines.size() ? lines.get(i + 1).strip() : "");
            }
        }
        return env;
    }

    @Test
    void everyRequiredVariableIsInTheBlueprint() throws IOException {
        Set<String> keys = serviceEnv("stagepass-api").keySet();
        List<String> missing = new ArrayList<>(requiredVariables());
        missing.removeAll(keys);
        assertThat(missing).as("required by application*.properties but missing from render.yaml").isEmpty();
    }

    @Test
    void startupCheckListsExactlyTheRequiredVariables() throws IOException {
        assertThat(new TreeSet<>(com.stagepass.common.config.RequiredSettings.REQUIRED)).isEqualTo(requiredVariables());
    }

    @Test
    void blueprintKeepsSecretsOutOfGit() throws IOException {
        for (String service : List.of("stagepass-api", "stagepass-web")) {
            serviceEnv(service).forEach((key, next) -> {
                if (!key.equals("SPRING_PROFILES_ACTIVE")) {
                    assertThat(next).as(service + " " + key).isIn("sync: false", "generateValue: true");
                }
            });
        }
    }

    @Test
    void blueprintCoversOptionalCompanyAndMapsSettings() throws IOException {
        assertThat(serviceEnv("stagepass-api")).containsKeys("COMPANY_NAME", "COMPANY_ADDRESS");
        assertThat(serviceEnv("stagepass-web")).containsKey("VITE_GOOGLE_MAPS_KEY");
    }
}
