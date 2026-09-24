package dev.fallingcloud.slate.building.chisel;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.net.ChiselGroupsSync;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Wire format of {@link ChiselGroupsSync}. A large modded index (Chipped alone has ~280 groups) is split into parts
 * well under the clientbound payload limit; each part is self-contained (its own block-id table), and the client
 * installs the index once every part of one generation has arrived.
 *
 * <pre>{@code
 * { v: 1, gen: <generation>, part: i, parts: n,
 *   blocks: ["minecraft:stone", ...],          // this part's id table
 *   world:  [1b, 0b, ...],                      // per table entry: may be chiselled in place
 *   groups: [{ s: "stonecutter", k: "<lang key or empty>", f: "<fallback or literal>", m: [<table index>, ...] }] }
 * }</pre>
 */
public final class ChiselSync {

    private static final int VERSION = 1;
    /** Rough byte budget per part (the clientbound limit is 1 MiB; this leaves room for NBT overhead). */
    private static final int PART_BUDGET = 256 * 1024;

    private ChiselSync() {}

    /** The payloads that carry {@code index} (always at least one, so an empty index clears the client's copy). */
    public static List<ChiselGroupsSync> payloads(final ChiselGroups index) {
        final List<ChiselGroupsSync> out = new ArrayList<>();
        for (final CompoundTag tag : encode(index)) out.add(new ChiselGroupsSync(tag));
        return out;
    }

    /**
     * Encodes {@code index} into parts of at most ~{@code partBudget} bytes and decodes them again through an
     * {@link Assembler}, exactly like a remote client (dev checks; a small budget exercises the multi-part path).
     */
    public static ChiselGroups roundTrip(final ChiselGroups index, final int partBudget) {
        final Assembler assembler = new Assembler();
        ChiselGroups out = null;
        for (final CompoundTag tag : encode(index, partBudget)) out = assembler.accept(tag.copy());
        return out != null ? out : ChiselGroups.empty();
    }

    /** How many payloads {@code index} needs at a given part budget. */
    public static int partCount(final ChiselGroups index, final int partBudget) {
        return encode(index, partBudget).size();
    }

    static List<CompoundTag> encode(final ChiselGroups index) {
        return encode(index, PART_BUDGET);
    }

    static List<CompoundTag> encode(final ChiselGroups index, final int partBudget) {
        final List<Part> parts = new ArrayList<>();
        Part current = new Part();
        for (final ChiselGroups.Group g : index.groups()) {
            if (!current.isEmpty() && current.bytes + current.cost(g) > partBudget) {
                parts.add(current);
                current = new Part();
            }
            current.add(g, index);
        }
        parts.add(current);
        final List<CompoundTag> tags = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) tags.add(parts.get(i).toTag(index.generation(), i, parts.size()));
        return tags;
    }

    /** One payload's worth of groups with its own block table. */
    private static final class Part {
        private final Map<Block, Integer> table = new IdentityHashMap<>();
        private final ListTag blocks = new ListTag();
        private final List<Boolean> world = new ArrayList<>();
        private final ListTag groups = new ListTag();
        private int bytes;

        boolean isEmpty() { return groups.isEmpty(); }

        int cost(final ChiselGroups.Group g) {
            int c = 32 + g.source().length() + 4 * g.members().size() + nameKey(g.name()).length() + nameText(g.name()).length();
            for (final Block b : g.members()) if (!table.containsKey(b)) c += 8 + BuiltInRegistries.BLOCK.getKey(b).toString().length();
            return c;
        }

        void add(final ChiselGroups.Group g, final ChiselGroups index) {
            bytes += cost(g);
            final int[] members = new int[g.members().size()];
            for (int i = 0; i < members.length; i++) {
                final Block b = g.members().get(i);
                Integer at = table.get(b);
                if (at == null) {
                    at = table.size();
                    table.put(b, at);
                    blocks.add(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(b).toString()));
                    world.add(index.inWorld(b));
                }
                members[i] = at;
            }
            final CompoundTag group = new CompoundTag();
            group.putString("s", g.source());
            group.putString("k", nameKey(g.name()));
            group.putString("f", nameText(g.name()));
            group.putIntArray("m", members);
            groups.add(group);
        }

        CompoundTag toTag(final int generation, final int part, final int parts) {
            final CompoundTag tag = new CompoundTag();
            tag.putInt("v", VERSION);
            tag.putInt("gen", generation);
            tag.putInt("part", part);
            tag.putInt("parts", parts);
            tag.put("blocks", blocks);
            final byte[] flags = new byte[world.size()];
            for (int i = 0; i < flags.length; i++) flags[i] = (byte) (world.get(i) ? 1 : 0);
            tag.putByteArray("world", flags);
            tag.put("groups", groups);
            return tag;
        }
    }

    private static String nameKey(final Component name) {
        return name.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    private static String nameText(final Component name) {
        if (name.getContents() instanceof TranslatableContents t) return t.getFallback() != null ? t.getFallback() : "";
        return name.getString();
    }

    /**
     * Collects the parts of one generation (client side). Parts of an older generation that arrive late are dropped;
     * a newer generation discards an unfinished older one.
     */
    public static final class Assembler {

        private int generation = -1;
        private @Nullable CompoundTag[] parts;

        /** Adds one part; returns the complete index when this was the last missing one, else null. */
        public synchronized @Nullable ChiselGroups accept(final CompoundTag tag) {
            if (tag.getInt("v") != VERSION) {
                SlateBuilding.LOGGER.warn("[Slate Building] chisel groups from the server use format {} (this client reads {}); ignored", tag.getInt("v"), VERSION);
                return null;
            }
            final int gen = tag.getInt("gen");
            final int count = Math.max(1, Math.min(tag.getInt("parts"), 4096));
            final int part = tag.getInt("part");
            if (part < 0 || part >= count) return null;
            if (gen < generation) return null;   // late part of an older index (generations only grow per connection)
            if (gen != generation || parts == null || parts.length != count) {
                generation = gen;
                parts = new CompoundTag[count];
            }
            parts[part] = tag;
            for (final CompoundTag p : parts) if (p == null) return null;
            final ChiselGroups done = decode(List.of(parts), gen);
            parts = null;
            return done;
        }

        public synchronized void reset() {
            generation = -1;
            parts = null;
        }
    }

    /** Decodes a complete set of parts; ids unknown to this client are skipped (groups left with one member vanish). */
    static ChiselGroups decode(final List<CompoundTag> parts, final int generation) {
        final List<ChiselGroups.Group> groups = new ArrayList<>();
        final Set<Block> inWorld = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final CompoundTag tag : parts) {
            final ListTag ids = tag.getList("blocks", Tag.TAG_STRING);
            final byte[] world = tag.getByteArray("world");
            final Block[] table = new Block[ids.size()];
            for (int i = 0; i < table.length; i++) {
                final ResourceLocation id = ResourceLocation.tryParse(ids.getString(i));
                table[i] = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                if (table[i] != null && i < world.length && world[i] != 0) inWorld.add(table[i]);
            }
            final ListTag list = tag.getList("groups", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                final CompoundTag g = list.getCompound(i);
                final List<Block> members = new ArrayList<>();
                for (final int at : g.getIntArray("m")) {
                    if (at >= 0 && at < table.length && table[at] != null) members.add(table[at]);
                }
                if (members.size() < 2) continue;
                final String key = g.getString("k");
                final String text = g.getString("f");
                final Component name = key.isEmpty() ? Component.literal(text) : Component.translatableWithFallback(key, text);
                groups.add(new ChiselGroups.Group(g.getString("s"), name, members));
            }
        }
        return new ChiselGroups(groups, inWorld, generation);
    }
}
