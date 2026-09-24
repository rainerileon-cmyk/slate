package dev.fallingcloud.slate.building.chisel;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.ServerChisel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Builds the server's {@link ChiselGroups} from every provider in page order ({@link ChiselSources}), applying the
 * rules of {@link ChiselRules} to each group. Deterministic for a given mod set, data and config: providers are read
 * in a fixed order, each sorts its own input (by id or registry order), and nothing iterates a hash order.
 */
final class ChiselIndexBuilder {

    private ChiselIndexBuilder() {}

    static ChiselGroups build(final MinecraftServer server, final ServerChisel cfg, final int generation) {
        if (!cfg.enabled) {
            SlateBuilding.LOGGER.info("[Slate Building] chisel groups are turned off (building-server.json → chisel.enabled)");
            return new ChiselGroups(List.of(), Set.of(), generation);
        }
        final long start = System.nanoTime();
        final ChiselOverrides file = ChiselOverrides.load();
        final Set<Block> excluded = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final String id : file.exclude) {
            final Block b = ChiselSources.block(id);
            if (b != null) excluded.add(b);
        }
        final Map<Block, Boolean> eligibleCache = new IdentityHashMap<>();
        final Predicate<Block> allowed = b -> !excluded.contains(b) && eligibleCache.computeIfAbsent(b, ChiselRules::eligible);

        final ChiselRules.BadPairs bad = ChiselSources.badPairs(server.getRecipeManager(), server.registryAccess());

        final List<ChiselSources.Raw> raw = new ArrayList<>();
        read(raw, ChiselSources.OVERRIDES, () -> ChiselSources.overrides(file));
        if (cfg.modCompat) {
            read(raw, ChiselSources.RECHISELED, () -> ChiselSources.rechiseled(server.getResourceManager()));
            read(raw, ChiselSources.CHIPPED, () -> ChiselSources.chipped(server));
            if (ChiselSources.hasChiselModernTags()) read(raw, "chisel (Modern)", ChiselSources::chiselModern);
            else read(raw, "chisel (Reborn)", ChiselSources::chiselReborn);
        }
        if (cfg.stonecutterGroups) read(raw, ChiselSources.STONECUTTER, () -> ChiselSources.stonecutter(server.getRecipeManager(), server.registryAccess(), allowed));
        if (cfg.blockFamilies) read(raw, ChiselSources.FAMILIES, () -> ChiselSources.families(allowed));

        final List<ChiselGroups.Group> groups = new ArrayList<>();
        final Map<String, List<Set<Block>>> seenPerSource = new LinkedHashMap<>();
        final Map<String, Integer> perSource = new LinkedHashMap<>();
        for (final ChiselSources.Raw r : raw) {
            for (final List<Block> members : ChiselRules.clean(r.members(), allowed, bad)) {
                final Set<Block> set = ChiselRules.asSet(members);
                final List<Set<Block>> seen = seenPerSource.computeIfAbsent(r.source(), k -> new ArrayList<>());
                if (seen.contains(set)) continue;           // the same group twice from one provider
                seen.add(set);
                groups.add(new ChiselGroups.Group(r.source(), name(r), members));
                perSource.merge(r.source(), 1, Integer::sum);
            }
        }

        final Set<Block> inWorld = inWorldFlags(server.overworld(), groups);
        final ChiselGroups index = new ChiselGroups(groups, inWorld, generation);
        SlateBuilding.LOGGER.info("[Slate Building] chisel groups: {} groups over {} blocks {} ({} one-way recipe links guarded) in {} ms",
            groups.size(), index.members().size(), perSource, bad.size(), (System.nanoTime() - start) / 1_000_000);
        return index;
    }

    private static void read(final List<ChiselSources.Raw> into, final String what, final Supplier<List<ChiselSources.Raw>> source) {
        try {
            into.addAll(source.get());
        } catch (final RuntimeException | LinkageError e) {
            SlateBuilding.LOGGER.warn("[Slate Building] chisel source '{}' failed and is skipped", what, e);
        }
    }

    private static Component name(final ChiselSources.Raw r) {
        return r.key().isEmpty() ? Component.literal(r.fallback()) : Component.translatableWithFallback(r.key(), r.fallback());
    }

    /**
     * Members that drop exactly themselves with no tool (checked on the default state in the overworld): those can be
     * chiselled in place without skipping their loot table. The chisel action re-checks the real block.
     */
    private static Set<Block> inWorldFlags(final ServerLevel level, final List<ChiselGroups.Group> groups) {
        final Set<Block> out = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Block> checked = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final ChiselGroups.Group g : groups) {
            for (final Block b : g.members()) {
                if (!checked.add(b)) continue;
                try {
                    final List<ItemStack> drops = Block.getDrops(b.defaultBlockState(), level, BlockPos.ZERO, null);
                    if (drops.size() == 1 && drops.get(0).getCount() == 1 && drops.get(0).is(b.asItem())) out.add(b);
                } catch (final RuntimeException e) {
                    // a loot table that needs more context than a bare block: not safe to chisel in place
                }
            }
        }
        return out;
    }
}
