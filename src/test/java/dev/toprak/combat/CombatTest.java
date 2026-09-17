package dev.toprak.combat;

import dev.toprak.combat.util.CommandUtil;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sanity checks on the bundled default config.yml (parsed without a YAML dependency). */
class CombatTest {

    private static List<String> readDefaultBlockedCommands() throws Exception {
        List<String> entries = new ArrayList<>();
        try (InputStream in = CombatTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "default config.yml must be on the classpath");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean inList = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("blocked-commands:")) {
                    inList = true;
                    continue;
                }
                if (!inList) continue;
                String trimmed = line.trim();
                if (trimmed.startsWith("- ")) {
                    entries.add(trimmed.substring(2).replace("\"", "").trim());
                } else if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    break; // next top-level key
                }
            }
        }
        return entries;
    }

    @Test
    void defaultBlockedCommandsAreWellFormed() throws Exception {
        List<String> entries = readDefaultBlockedCommands();
        assertFalse(entries.isEmpty(), "default config should ship a blocked-commands list");

        Set<String> normalized = CommandUtil.normalizeBlockedEntries(entries);
        assertEquals(entries.size(), normalized.size(), "default list must not contain blanks or duplicates");
        for (String root : normalized) {
            assertFalse(root.contains("/") || root.contains(":") || root.contains(" "),
                    "normalised root should be a bare command name: " + root);
        }
    }

    @Test
    void defaultListCoversTeleportAcceptance() throws Exception {
        // Blocking /tpa alone is bypassable: the *other* player sends the request and the tagged
        // player accepts it. The default list has to cover the acceptance side too.
        Set<String> normalized = CommandUtil.normalizeBlockedEntries(readDefaultBlockedCommands());
        assertTrue(normalized.contains("tpa"));
        assertTrue(normalized.contains("tpaccept"));
        assertTrue(normalized.contains("tpahere"));
    }
}
