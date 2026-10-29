package dev.toprak.combat.util;

import java.lang.reflect.Field;

/** Resolves constants that were renamed between Minecraft versions. */
public final class Compat {
    private Compat() {}

    @SuppressWarnings("unchecked")
    public static <T> T constant(Class<T> type, String... names) {
        for (String n : names) {
            try {
                Field f = type.getField(n);
                return (T) f.get(null);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        throw new IllegalStateException("No constant " + String.join("/", names) + " in " + type.getName());
    }

    /** Name of the cloud's base potion type, or null. Handles the 1.20.5 API change. */
    public static String basePotionName(org.bukkit.entity.AreaEffectCloud cloud) {
        try {
            Object type = cloud.getClass().getMethod("getBasePotionType").invoke(cloud);
            return type == null ? null : ((Enum<?>) type).name();
        } catch (NoSuchMethodException e) {
            try {
                Object data = cloud.getClass().getMethod("getBasePotionData").invoke(cloud);
                Object type = data.getClass().getMethod("getType").invoke(data);
                return type == null ? null : ((Enum<?>) type).name();
            } catch (ReflectiveOperationException ex) {
                return null;
            }
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
