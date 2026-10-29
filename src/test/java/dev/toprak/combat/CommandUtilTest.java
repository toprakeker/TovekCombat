package dev.toprak.combat;

import dev.toprak.combat.util.CommandUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandUtilTest {

    @Test
    void labelStripsSlashArgumentsAndCase() {
        assertEquals("spawn", CommandUtil.commandLabel("/spawn"));
        assertEquals("spawn", CommandUtil.commandLabel("/SPAWN now"));
        assertEquals("spawn", CommandUtil.commandLabel("/spawn   "));
        assertEquals("minecraft:tp", CommandUtil.commandLabel("/minecraft:tp @s ~ ~ ~"));
    }

    @Test
    void labelMirrorsServerDispatchForOddInput() {
        // The server splits on ' ' only; these would all be "Unknown command" and must not match.
        assertEquals("", CommandUtil.commandLabel("/ spawn"));
        assertEquals("/spawn", CommandUtil.commandLabel("//spawn"));
        assertEquals("spawn\t", CommandUtil.commandLabel("/spawn\t"));
        assertEquals("", CommandUtil.commandLabel("/"));
        assertEquals("", CommandUtil.commandLabel(""));
        assertEquals("", CommandUtil.commandLabel(null));
    }

    @Test
    void stripNamespace() {
        assertEquals("tp", CommandUtil.stripNamespace("minecraft:tp"));
        assertEquals("tp", CommandUtil.stripNamespace("tp"));
        assertEquals("", CommandUtil.stripNamespace(null));
    }

    @Test
    void normalizeEntriesDropsBlanksAndNoise() {
        Set<String> blocked = CommandUtil.normalizeBlockedEntries(
                Arrays.asList("/spawn", "Home", " /Essentials:TP ", "", "   ", "/", null, "spawn"));
        assertEquals(Set.of("spawn", "home", "tp"), blocked);
        assertThrows(UnsupportedOperationException.class, () -> blocked.add("x"));
        assertTrue(CommandUtil.normalizeBlockedEntries(null).isEmpty());
        assertTrue(CommandUtil.normalizeBlockedEntries(List.of()).isEmpty());
    }

    @Test
    void blocksTypedLabelExactlyNotByPrefix() {
        Set<String> blocked = Set.of("spawn", "tp");
        assertTrue(CommandUtil.isBlocked(blocked, "spawn", null, null));
        assertTrue(CommandUtil.isBlocked(blocked, "minecraft:tp", null, null));
        assertFalse(CommandUtil.isBlocked(blocked, "spawner", null, null));
        assertFalse(CommandUtil.isBlocked(blocked, "/spawn", null, null)); // "//spawn" typed
        assertFalse(CommandUtil.isBlocked(blocked, "", "spawn", List.of("spawn")));
    }

    @Test
    void blocksResolvedNameAndAliases() {
        Set<String> blocked = Set.of("home", "tpa");
        // /ehome resolves to the Essentials "home" command.
        assertTrue(CommandUtil.isBlocked(blocked, "ehome", "home", List.of("ehome", "homes")));
        // Typed the canonical name but the plugin registered it under a different label.
        assertTrue(CommandUtil.isBlocked(blocked, "call", "tpask", List.of("call", "tpa", "etpa")));
        // Namespaced canonical names are normalised too.
        assertTrue(CommandUtil.isBlocked(blocked, "essentials:home", "essentials:home", List.of()));
        // Unrelated command with unrelated aliases stays allowed.
        assertFalse(CommandUtil.isBlocked(blocked, "sethome", "sethome", List.of("esethome", "createhome")));
        assertFalse(CommandUtil.isBlocked(Set.of(), "home", "home", List.of("home")));
    }
}
