package dev.fallingcloud.slate.core.layout;

import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

/**
 * {@code {player}}-style substitutions for label/text elements. Modules add their own with
 * {@link #register} ({@code {friends_online}} comes from Multiplayer). Unknown placeholders stay as-is.
 */
public final class Placeholders {

    private static final Map<String, Supplier<String>> SUPPLIERS = new LinkedHashMap<>();
    private static final Pattern PATTERN = Pattern.compile("\\{([a-z_]+)}");

    static {
        register("player", () -> Minecraft.getInstance().getUser().getName());
        register("uuid", () -> Minecraft.getInstance().getUser().getProfileId().toString());
        register("mc_version", () -> SharedConstants.getCurrentVersion().getName());
        register("loader", () -> SlatePlatform.get().loader().displayName);
        register("mod_count", () -> Integer.toString(SlatePlatform.get().allMods().size()));
        register("fps", () -> Integer.toString(Minecraft.getInstance().getFps()));
        register("time", () -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        register("date", () -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        register("server", () -> {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.getCurrentServer() != null) return mc.getCurrentServer().name;
            return mc.hasSingleplayerServer() ? "Singleplayer" : "";
        });
        register("world", () -> {
            final Minecraft mc = Minecraft.getInstance();
            return mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().getWorldData().getLevelName() : "";
        });
        register("slate_modules", () -> Integer.toString(Modules.all().size()));
    }

    public static void register(final String key, final Supplier<String> supplier) {
        SUPPLIERS.put(key, supplier);
    }

    public static String apply(final String text) {
        if (text == null || text.indexOf('{') < 0) return text == null ? "" : text;
        final Matcher m = PATTERN.matcher(text);
        final StringBuilder sb = new StringBuilder();
        while (m.find()) {
            final Supplier<String> s = SUPPLIERS.get(m.group(1));
            String v;
            try { v = s == null ? m.group() : s.get(); } catch (final Exception e) { v = ""; }
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static java.util.Set<String> keys() { return SUPPLIERS.keySet(); }

    private Placeholders() {}
}
