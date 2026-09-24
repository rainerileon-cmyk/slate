package dev.fallingcloud.slate.building.client.settings;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.ServerSettingsClient;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.config.BuildingConfig;
import dev.fallingcloud.slate.building.config.BuildingServerConfig;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.HudSettings;
import dev.fallingcloud.slate.building.config.ModeSettings;
import dev.fallingcloud.slate.building.config.PreviewSettings;
import dev.fallingcloud.slate.building.config.ServerChisel;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.config.ServerToolbox;
import dev.fallingcloud.slate.building.config.ServerVariants;
import dev.fallingcloud.slate.building.config.WheelSettings;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Every Slate Building setting, described once (label and tooltip from lang, type, range, default, getter/setter)
 * and rendered by two front ends: the Slate Config "Building" tab ({@link BuildingSettingsTab}, when Slate Config is
 * installed) and the standalone {@link BuildingSettingsScreen}. Client settings write {@code building.json} and
 * apply live; server rules write {@code building-server.json} in singleplayer (and are pushed to LAN guests) and
 * are read-only while connected to a server, where they show that server's values.
 *
 * <p>Lang: {@code slate_building.settings.<id>} (label) and {@code .desc} (tooltip); groups
 * {@code slate_building.settings.group.<id>}.</p>
 */
public final class BuildingSettings {

    public enum Type { BOOL, INT, DOUBLE, CHOICE, INFO }

    /** One setting. {@code choices} are stored ids, labelled by {@code choiceLabel}. */
    public record Setting(String id, Type type, Component label, @Nullable Component tooltip, Supplier<Object> get,
                          Consumer<Object> set, @Nullable Object def, double min, double max, double step, List<String> choices,
                          Function<String, Component> choiceLabel, BooleanSupplier enabled, @Nullable Function<Object, Component> format,
                          boolean restart) {}

    /** A titled group; {@code wheelEditor} adds the "Edit wheels…" row at its end. */
    public record Group(String id, Component title, @Nullable Component description, List<Setting> settings, boolean wheelEditor) {}

    public static List<Group> groups() {
        final List<Group> out = new ArrayList<>();
        out.add(general());
        out.add(preview());
        out.add(wheel());
        out.add(menuHud());
        out.add(server());
        return out;
    }

    // ------------------------------------------------------------------ client groups

    private static BuildingConfig cfg() {
        final BuildingConfig c = SlateBuilding.config();
        if (c.preview == null) c.preview = new PreviewSettings();
        if (c.modes == null) c.modes = new ModeSettings();
        WheelConfig.wheel();
        WheelConfig.hud();
        return c;
    }

    private static void saveClient() {
        WheelConfig.saveAndNotify();
    }

    private static Group general() {
        final List<Setting> s = new ArrayList<>();
        final WheelSettings d = new WheelSettings();
        final HudSettings hd = new HudSettings();
        s.add(bool("general.exclusive_swap_key", () -> cfg().wheel.exclusiveSwapKey, v -> cfg().wheel.exclusiveSwapKey = v, d.exclusiveSwapKey));
        s.add(choice("general.menu_key_context", () -> cfg().wheel.menuKeyContext, v -> cfg().wheel.menuKeyContext = v, d.menuKeyContext,
            List.of("SMART", "ALWAYS")));
        s.add(bool("general.pick_block_swaps", () -> cfg().wheel.pickBlockSwaps, v -> cfg().wheel.pickBlockSwaps = v, d.pickBlockSwaps));
        s.add(bool("general.sounds", () -> cfg().hud.sounds, v -> cfg().hud.sounds = v, hd.sounds));
        return new Group("general", group("general"), null, s, false);
    }

    private static Group preview() {
        final List<Setting> s = new ArrayList<>();
        final PreviewSettings d = new PreviewSettings();
        s.add(bool("preview.enabled", () -> cfg().preview.enabled, v -> cfg().preview.enabled = v, d.enabled));
        s.add(bool("preview.all_blocks", () -> cfg().preview.allBlocks, v -> cfg().preview.allBlocks = v, d.allBlocks));
        s.add(percent("preview.opacity", () -> cfg().preview.opacity, v -> cfg().preview.opacity = v, d.opacity, 0.05));
        s.add(percent("preview.saturation", () -> cfg().preview.saturation, v -> cfg().preview.saturation = v, d.saturation, 0.0));
        s.add(bool("preview.outline", () -> cfg().preview.outline, v -> cfg().preview.outline = v, d.outline));
        s.add(bool("preview.pulse", () -> cfg().preview.pulse, v -> cfg().preview.pulse = v, d.pulse));
        s.add(integer("preview.max_blocks", () -> cfg().preview.maxBlocks, v -> cfg().preview.maxBlocks = v, d.maxBlocks, 256, 32768, 256));
        s.add(bool("preview.show_mirrored", () -> cfg().preview.showMirrored, v -> cfg().preview.showMirrored = v, d.showMirrored));
        return new Group("preview", group("preview"), null, s, false);
    }

    private static Group wheel() {
        final List<Setting> s = new ArrayList<>();
        final WheelSettings d = new WheelSettings();
        s.add(bool("wheel.enabled", () -> cfg().wheel.swapEnabled, v -> cfg().wheel.swapEnabled = v, d.swapEnabled));
        s.add(integer("wheel.max_slices", () -> WheelConfig.maxSlices(), v -> cfg().wheel.maxSlices = v, d.maxSlices, WheelConfig.MIN_SLICES, WheelConfig.MAX_SLICES, 1));
        s.add(number("wheel.scale", () -> (double) WheelConfig.wheelScale(), v -> cfg().wheel.wheelScale = v, d.wheelScale, 0.5, 2.0, 0.05,
            v -> Component.literal(String.format(Locale.ROOT, "%.2f×", ((Number) v).doubleValue()))));
        s.add(bool("wheel.hide_unavailable", () -> cfg().wheel.hideUnavailable, v -> cfg().wheel.hideUnavailable = v, d.hideUnavailable));
        s.add(bool("wheel.release_to_select", () -> cfg().wheel.releaseToSelect, v -> cfg().wheel.releaseToSelect = v, d.releaseToSelect));
        s.add(bool("wheel.click_switches", () -> cfg().wheel.clickSwitchesWheel, v -> cfg().wheel.clickSwitchesWheel = v, d.clickSwitchesWheel));
        s.add(bool("wheel.scroll_selects", () -> cfg().wheel.scrollSelects, v -> cfg().wheel.scrollSelects = v, d.scrollSelects));
        s.add(bool("wheel.number_keys", () -> cfg().wheel.numberKeys, v -> cfg().wheel.numberKeys = v, d.numberKeys));
        s.add(bool("wheel.labels", () -> cfg().wheel.labels, v -> cfg().wheel.labels = v, d.labels));
        s.add(bool("wheel.chisel_page", () -> cfg().wheel.includeChiselPage, v -> cfg().wheel.includeChiselPage = v, d.includeChiselPage));
        return new Group("wheel", group("wheel"), null, s, true);
    }

    private static Group menuHud() {
        final List<Setting> s = new ArrayList<>();
        final HudSettings d = new HudSettings();
        final ModeSettings md = new ModeSettings();
        s.add(bool("hud.enabled", () -> cfg().hud.enabled, v -> cfg().hud.enabled = v, d.enabled));
        s.add(choice("hud.anchor", () -> cfg().hud.anchor, v -> cfg().hud.anchor = v, d.anchor,
            List.of("TOP_LEFT", "TOP", "TOP_RIGHT", "LEFT", "RIGHT", "BOTTOM_LEFT", "BOTTOM", "BOTTOM_RIGHT")));
        s.add(number("hud.scale", () -> (double) WheelConfig.hudScale(), v -> cfg().hud.scale = v, d.scale, 0.5, 2.0, 0.05,
            v -> Component.literal(String.format(Locale.ROOT, "%.2f×", ((Number) v).doubleValue()))));
        s.add(bool("hud.action_bar", () -> cfg().hud.actionBar, v -> cfg().hud.actionBar = v, d.actionBar));
        s.add(integer("modes.air_distance", () -> cfg().modes.airDistance, v -> cfg().modes.airDistance = v, md.airDistance, 1, 16, 1));
        s.add(bool("modes.confirm_right_click", () -> cfg().modes.confirmWithRightClick, v -> cfg().modes.confirmWithRightClick = v, md.confirmWithRightClick));
        return new Group("menu_hud", group("menu_hud"), null, s, false);
    }

    // ------------------------------------------------------------------ server rules

    /** Whether the rules shown are a remote server's (read-only). */
    public static boolean serverReadOnly() {
        return ServerSettingsClient.isRemote();
    }

    private static BuildingServerConfig rules() {
        return filled(ServerSettingsClient.get().config());
    }

    /** A hand-edited file or an odd server may leave whole sections null; fill them with defaults. */
    private static BuildingServerConfig filled(final BuildingServerConfig c) {
        if (c.variants == null) c.variants = new ServerVariants();
        if (c.ops == null) c.ops = new ServerOps();
        if (c.toolbox == null) c.toolbox = new ServerToolbox();
        if (c.chisel == null) c.chisel = new ServerChisel();
        return c;
    }

    private static BuildingServerConfig local() {
        return filled(SlateBuilding.serverConfig());
    }

    /** Saves {@code building-server.json}, rebuilds what depends on it and pushes it to LAN guests. */
    private static void saveServer(final boolean variants) {
        SlateBuilding.serverConfigFile().save();
        if (variants) VariantRegistry.invalidate();
        final IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) server.execute(() -> BuildingServerSettings.broadcast(server));
    }

    private static Setting rule(final String id, final Function<BuildingServerConfig, Boolean> read,
                                final BiConsumer<BuildingServerConfig, Boolean> write, final boolean def, final boolean variants,
                                final boolean restart) {
        return new Setting("server." + id, Type.BOOL, label("server." + id), tooltip("server." + id),
            () -> read.apply(rules()), v -> {
                if (serverReadOnly()) return;
                write.accept(local(), Boolean.TRUE.equals(v));
                saveServer(variants);
            }, def, 0, 0, 0, List.of(), Component::literal, () -> !serverReadOnly(), null, restart);
    }

    private static Setting ruleInt(final String id, final Function<BuildingServerConfig, Integer> read,
                                   final BiConsumer<BuildingServerConfig, Integer> write, final int def, final int min, final int max) {
        return new Setting("server." + id, Type.INT, label("server." + id), tooltip("server." + id),
            () -> read.apply(rules()), v -> {
                if (serverReadOnly()) return;
                write.accept(local(), ((Number) v).intValue());
                saveServer(false);
            }, def, min, max, 1, List.of(), Component::literal, () -> !serverReadOnly(), null, false);
    }

    private static Group server() {
        final List<Setting> s = new ArrayList<>();
        final ServerVariants v = new ServerVariants();
        final ServerOps o = new ServerOps();
        final ServerToolbox tb = new ServerToolbox();
        final ServerChisel ch = new ServerChisel();
        s.add(new Setting("server.status", Type.INFO, label("server.status"), null,
            () -> Component.translatable(serverReadOnly() ? "slate_building.settings.server.remote" : "slate_building.settings.server.local"),
            x -> {}, null, 0, 0, 0, List.of(), Component::literal, () -> true, null, false));
        s.add(rule("unify", c -> c.variants.unify, (c, b) -> c.variants.unify = b, v.unify, true, false));
        s.add(rule("custom_shapes", c -> c.variants.customShapes, (c, b) -> c.variants.customShapes = b, v.customShapes, true, false));
        s.add(rule("rebalance_recipes", c -> c.variants.rebalanceRecipes, (c, b) -> c.variants.rebalanceRecipes = b, v.rebalanceRecipes, true, true));
        s.add(rule("delete_native_variants", c -> c.variants.deleteNativeVariants, (c, b) -> c.variants.deleteNativeVariants = b, v.deleteNativeVariants, true, true));
        s.add(rule("swap_needs_tool", c -> c.variants.swapNeedsTool, (c, b) -> c.variants.swapNeedsTool = b, v.swapNeedsTool, false, false));
        s.add(rule("ops_enabled", c -> c.ops.enabled, (c, b) -> c.ops.enabled = b, o.enabled, false, false));
        s.add(rule("require_toolbox", c -> c.ops.requireToolbox, (c, b) -> c.ops.requireToolbox = b, o.requireToolbox, false, false));
        s.add(rule("creative_bypass", c -> c.ops.creativeBypass, (c, b) -> c.ops.creativeBypass = b, o.creativeBypass, false, false));
        s.add(rule("place_what_you_can", c -> c.ops.placeWhatYouCan, (c, b) -> c.ops.placeWhatYouCan = b, o.placeWhatYouCan, false, false));
        s.add(rule("respect_claims", c -> c.ops.respectClaims, (c, b) -> c.ops.respectClaims = b, o.respectClaims, false, false));
        s.add(rule("allow_block_entities", c -> c.ops.allowBlockEntities, (c, b) -> c.ops.allowBlockEntities = b, o.allowBlockEntities, false, false));
        s.add(ruleInt("undo_depth", c -> c.ops.undoDepth, (c, n) -> c.ops.undoDepth = n, o.undoDepth, 1, 100));
        s.add(ruleInt("durability_per_blocks", c -> c.ops.durabilityPerBlocks, (c, n) -> c.ops.durabilityPerBlocks = n, o.durabilityPerBlocks, 1, 64));
        s.add(rule("allow_pouch", c -> c.toolbox.allowPouch, (c, b) -> c.toolbox.allowPouch = b, tb.allowPouch, false, false));
        s.add(rule("chisel_enabled", c -> c.chisel.enabled, (c, b) -> c.chisel.enabled = b, ch.enabled, false, false));
        s.add(rule("chisel_in_world", c -> c.chisel.inWorld, (c, b) -> c.chisel.inWorld = b, ch.inWorld, false, false));
        s.add(rule("chisel_stonecutter", c -> c.chisel.stonecutterGroups, (c, b) -> c.chisel.stonecutterGroups = b, ch.stonecutterGroups, false, true));
        s.add(rule("chisel_families", c -> c.chisel.blockFamilies, (c, b) -> c.chisel.blockFamilies = b, ch.blockFamilies, false, true));
        s.add(rule("chisel_mod_compat", c -> c.chisel.modCompat, (c, b) -> c.chisel.modCompat = b, ch.modCompat, false, true));
        return new Group("server", group("server"), Component.translatable("slate_building.settings.group.server.desc"), s, false);
    }

    // ------------------------------------------------------------------ builders

    private static Component group(final String id) {
        return Component.translatable("slate_building.settings.group." + id);
    }

    private static Component label(final String id) {
        return Component.translatable("slate_building.settings." + id);
    }

    private static Component tooltip(final String id) {
        return Component.translatable("slate_building.settings." + id + ".desc");
    }

    private static Setting bool(final String id, final Supplier<Boolean> get, final Consumer<Boolean> set, final boolean def) {
        return new Setting(id, Type.BOOL, label(id), tooltip(id), get::get, v -> { set.accept(Boolean.TRUE.equals(v)); saveClient(); },
            def, 0, 0, 0, List.of(), Component::literal, () -> true, null, false);
    }

    private static Setting integer(final String id, final Supplier<Integer> get, final Consumer<Integer> set, final int def,
                                   final int min, final int max, final int step) {
        return new Setting(id, Type.INT, label(id), tooltip(id), get::get, v -> {
            final int n = (int) Math.max(min, Math.min(max, Math.round(((Number) v).doubleValue())));
            set.accept(n);
            saveClient();
        }, def, min, max, step, List.of(), Component::literal, () -> true, null, false);
    }

    private static Setting number(final String id, final Supplier<Double> get, final Consumer<Double> set, final double def,
                                  final double min, final double max, final double step, final Function<Object, Component> format) {
        return new Setting(id, Type.DOUBLE, label(id), tooltip(id), get::get, v -> {
            set.accept(Math.max(min, Math.min(max, ((Number) v).doubleValue())));
            saveClient();
        }, def, min, max, step, List.of(), Component::literal, () -> true, format, false);
    }

    private static Setting percent(final String id, final Supplier<Double> get, final Consumer<Double> set, final double def, final double min) {
        return number(id, get, set, def, min, 1.0, 0.05, v -> Component.literal(Math.round(((Number) v).doubleValue() * 100) + "%"));
    }

    private static Setting choice(final String id, final Supplier<String> get, final Consumer<String> set, final String def, final List<String> ids) {
        return new Setting(id, Type.CHOICE, label(id), tooltip(id), get::get, v -> { set.accept(String.valueOf(v)); saveClient(); },
            def, 0, 0, 0, ids, c -> Component.translatable("slate_building.settings." + id + "." + c.toLowerCase(Locale.ROOT)),
            () -> true, null, false);
    }

    private BuildingSettings() {}
}
