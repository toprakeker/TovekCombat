package dev.toprak.combat.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pure string helpers for command matching. Deliberately free of Bukkit imports so the
 * matching rules can be unit tested without a server.
 */
public final class CommandUtil {

    private CommandUtil() {
    }

    /**
     * Extracts the label the server will use to look the command up: the first
     * space-delimited token after the leading slash, lower-cased, namespace intact.
     * This mirrors {@code SimpleCommandMap#dispatch}, which splits on {@code ' '} only,
     * so inputs that the server would treat as an unknown command (e.g. {@code "/ spawn"})
     * yield an empty label here as well.
     */
    public static String commandLabel(String message) {
        if (message == null) {
            return "";
        }
        String s = message;
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int space = s.indexOf(' ');
        if (space != -1) {
            s = s.substring(0, space);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /** Removes a {@code namespace:} prefix, e.g. {@code minecraft:tp -> tp}. */
    public static String stripNamespace(String label) {
        if (label == null) {
            return "";
        }
        int colon = label.indexOf(':');
        return colon == -1 ? label : label.substring(colon + 1);
    }

    /** Normalises a configured entry ({@code "/Essentials:Home "}) to its bare, lower-case root ({@code home}). */
    public static String normalizeBlockedEntry(String entry) {
        if (entry == null) {
            return "";
        }
        String s = entry.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        return stripNamespace(s).toLowerCase(Locale.ROOT);
    }

    /** Normalises every entry and drops blanks. The result is unmodifiable. */
    public static Set<String> normalizeBlockedEntries(Collection<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String entry : entries) {
            String normalized = normalizeBlockedEntry(entry);
            if (!normalized.isEmpty()) {
                out.add(normalized);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * Decides whether a typed command is blocked. Matches the typed label, the canonical
     * name of the command it resolves to, and every registered alias of that command, so a
     * blocked {@code /home} also blocks {@code /ehome}, {@code /essentials:home}, etc.
     *
     * @param blocked      normalised blocked roots (see {@link #normalizeBlockedEntries})
     * @param label        the typed label (see {@link #commandLabel})
     * @param resolvedName canonical name of the resolved command, or {@code null} if unresolved
     * @param aliases      aliases of the resolved command, may be {@code null}
     */
    public static boolean isBlocked(Set<String> blocked, String label, String resolvedName, Collection<String> aliases) {
        if (blocked == null || blocked.isEmpty() || label == null || label.isEmpty()) {
            return false;
        }
        if (blocked.contains(stripNamespace(label))) {
            return true;
        }
        if (resolvedName != null && blocked.contains(stripNamespace(resolvedName.toLowerCase(Locale.ROOT)))) {
            return true;
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && blocked.contains(stripNamespace(alias.toLowerCase(Locale.ROOT)))) {
                    return true;
                }
            }
        }
        return false;
    }
}
