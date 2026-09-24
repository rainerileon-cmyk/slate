package dev.fallingcloud.slate.building.chisel;

import com.google.gson.JsonParseException;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.core.config.JsonConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * {@code config/slate/building-chisel.json}: the server's own chisel groups, the first page source of the chisel wheel
 * (design §9). The mod ships a set of defaults for the vanilla gaps the recipe-based sources cannot see (cracked
 * variants, the deepslate chain, quartz bricks, per-state copper) in {@code default-groups.json} next to this class;
 * {@link #includeDefaults} keeps them on without copying them into the file, so later versions can extend them.
 *
 * <p>The defaults only link blocks that vanilla turns into each other one for one with nothing added and nothing
 * smelted (the block families' cracked variants aside, which design §9 accepts): no cobblestone ↔ stone or deepslate
 * ↔ cobbled deepslate (free smelting), no smooth stone, sandstone, quartz or basalt (smelted), no mossy blocks (moss
 * or vines added). Every group still goes through the safety rules of {@link ChiselRules}. Edits apply on
 * {@code /reload}.
 */
public final class ChiselOverrides {

    private static final String FILE = "building-chisel";
    private static final String DEFAULTS = "default-groups.json";

    private static final List<String> README = List.of(
        "Chisel groups for Slate Building. Every group is one page of the chisel wheel; the blocks in a group can be",
        "chiseled into each other one for one. Edit this file, then run /reload (or restart the server).",
        "includeDefaults: also use the groups that ship with the mod (stone and stone bricks, the deepslate family,",
        "  cracked variants, copper per oxidation state, ...). They never link blocks vanilla only converts by smelting",
        "  or by adding an ingredient (cobblestone and stone, smooth or mossy blocks stay apart).",
        "groups: your own pages, e.g. {\"name\": \"Marble\", \"members\": [\"somemod:marble\", \"somemod:marble_bricks\"]}.",
        "  \"key\" may name a translation key for the page title instead of (or as well as) \"name\".",
        "exclude: blocks that never appear in ANY chisel group, whichever mod or recipe suggested them.",
        "Safety rules always apply: members must be plain full blocks (no block entities, doors, beds or falling",
        "blocks), copper never mixes oxidation or wax states, and no two members may be joined by a recipe that is not",
        "one for one (1 copper block -> 4 cut copper keeps those two apart).");

    /** Explains the file to whoever opens it; rewritten on load when outdated. */
    public List<String> _readme = README;
    /** Also use the groups shipped with the mod. */
    public boolean includeDefaults = true;
    /** This server's own groups, one wheel page each. */
    public List<GroupSpec> groups = new ArrayList<>();
    /** Block ids that never join any chisel group. */
    public List<String> exclude = new ArrayList<>();

    /** One group as written in the file. */
    public static final class GroupSpec {
        /** Page title (plain text); "Custom" when neither this nor {@link #key} is set. */
        public @Nullable String name;
        /** Optional translation key for the page title; {@link #name} is its fallback. */
        public @Nullable String key;
        /** Block ids in wheel order; unknown ids are skipped (so one file serves several mod sets). */
        public List<String> members = new ArrayList<>();
    }

    private static @Nullable JsonConfig<ChiselOverrides> file;
    private static @Nullable List<GroupSpec> shipped;

    /** The file, re-read from disk (so {@code /reload} picks up edits). */
    static synchronized ChiselOverrides load() {
        if (file == null) {
            file = JsonConfig.of(FILE, ChiselOverrides.class, ChiselOverrides::new);
        } else {
            file.load();
        }
        final ChiselOverrides value = file.get();
        boolean dirty = false;
        if (value.groups == null) { value.groups = new ArrayList<>(); dirty = true; }
        if (value.exclude == null) { value.exclude = new ArrayList<>(); dirty = true; }
        if (!README.equals(value._readme)) { value._readme = README; dirty = true; }
        if (dirty) file.save();
        return value;
    }

    /** The groups shipped in the jar ({@code default-groups.json}); empty (and logged) if unreadable. */
    static synchronized List<GroupSpec> shippedDefaults() {
        if (shipped == null) shipped = readShipped();
        return shipped;
    }

    private static List<GroupSpec> readShipped() {
        try (InputStream in = ChiselOverrides.class.getResourceAsStream(DEFAULTS)) {
            if (in == null) {
                SlateBuilding.LOGGER.error("[Slate Building] chisel defaults {} missing from the jar", DEFAULTS);
                return List.of();
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                final Shipped parsed = JsonConfig.GSON.fromJson(r, Shipped.class);
                return parsed == null || parsed.groups == null ? List.of() : List.copyOf(parsed.groups);
            }
        } catch (final IOException | JsonParseException e) {
            SlateBuilding.LOGGER.error("[Slate Building] could not read the chisel defaults", e);
            return List.of();
        }
    }

    /** Shape of {@code default-groups.json}. */
    public static final class Shipped {
        public List<GroupSpec> groups;
    }
}
