package dev.fallingcloud.slate.building.chisel.client;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.chisel.ChiselGroups;
import dev.fallingcloud.slate.building.chisel.ChiselSelfTest;
import dev.fallingcloud.slate.building.chisel.ChiselSwap;
import dev.fallingcloud.slate.building.chisel.ChiselSync;
import dev.fallingcloud.slate.building.chisel.ChiselSystem;
import dev.fallingcloud.slate.building.client.BuildingHarness;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Dev harness scenario {@code chisel} ({@code -PbuildingHarness=chisel}): logs the groups of a few reference blocks,
 * checks the safety rules (copper, 1:N links, shapes, loot guard), the sync round trip (split into many parts on
 * purpose), held swaps that keep the shape (stone → stone bricks, stone slab → stone brick slab), in-world chisels
 * that keep orientation, the survival lock and the {@code /reload} rebuild. Every check logs one
 * {@code [BuildingHarness] chisel PASS|FAIL <what>} line, then a summary; one screenshot at the end.
 */
final class ChiselHarness {

    private static final AtomicInteger PASSED = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();

    private static final BlockPos FULL_BLOCK = new BlockPos(-1, -60, 3);
    private static final BlockPos STAIRS_BLOCK = new BlockPos(1, -60, 3);

    private ChiselHarness() {}

    static void register() {
        BuildingHarness.register("chisel", ChiselHarness::script);
    }

    private static void script(final BuildingHarness.Script s) {
        final int[] generation = {0};
        final CompletableFuture<?>[] serverCheck = {null};
        s.run(() -> { PASSED.set(0); FAILED.set(0); })
            .command("time set noon")
            .command("weather clear")
            .command("gamemode creative")
            .command("tp @s 0 -60 0 0 32")
            .command("fill -3 -61 1 3 -61 6 minecraft:polished_andesite")
            .command("setblock -1 -60 3 minecraft:stone_bricks")
            .command("setblock 1 -60 3 minecraft:stone_brick_stairs[facing=west]")
            .command("item replace entity @s hotbar.0 with minecraft:stone 32")
            .command("item replace entity @s hotbar.1 with minecraft:stone_slab 16")
            .command("item replace entity @s hotbar.2 with minecraft:cut_copper 8")
            .command("item replace entity @s hotbar.3 with minecraft:copper_block 4")
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.getInventory().selected = 0;
                mc.getToasts().clear();
            })
            .waitUntil(() -> !ChiselGroups.client().isEmpty(), 200)
            .run(ChiselHarness::checkGroups)
            .run(() -> ChiselSelfTest.run().forEach((what, ok) -> expect(ok, what)))
            .run(() -> {
                ChiselClient.chiselHeld(0, Blocks.STONE_BRICKS);
                ChiselClient.chiselHeld(1, Blocks.STONE_BRICKS);
                ChiselClient.chiselHeld(2, Blocks.EXPOSED_CUT_COPPER);    // other oxidation state: refused
                ChiselClient.chiselHeld(2, Blocks.CHISELED_COPPER);       // same state: fine
                ChiselClient.chiselHeld(3, Blocks.CUT_COPPER);            // 1 block = 4 cut copper: refused
                ChiselClient.chiselTarget(FULL_BLOCK, Blocks.CHISELED_STONE_BRICKS);
                ChiselClient.chiselTarget(STAIRS_BLOCK, Blocks.STONE);
            })
            .waitUntil(() -> slot(0).is(Items.STONE_BRICKS) && slot(1).is(Items.STONE_BRICK_SLAB) && slot(2).is(Items.CHISELED_COPPER)
                && block(FULL_BLOCK).is(Blocks.CHISELED_STONE_BRICKS) && block(STAIRS_BLOCK).is(Blocks.STONE_STAIRS), 120)
            .wait(5)
            .run(ChiselHarness::checkSwaps)
            .run(() -> serverCheck[0] = server().submit(ChiselHarness::checkServer))
            .waitUntil(() -> serverCheck[0] != null && serverCheck[0].isDone(), 200)
            .command("gamemode survival")
            .wait(5)
            .run(() -> ChiselClient.chiselHeld(0, Blocks.STONE))
            .wait(30)
            .run(() -> expect(slot(0).is(Items.STONE_BRICKS) && ChiselSwap.heldLock(player()) != null,
                "survival without a chisel: held swap refused (" + text(ChiselSwap.heldLock(player())) + ")"))
            .command("gamemode creative")
            .run(() -> generation[0] = ChiselGroups.client().generation())
            .run(ChiselHarness::writeCompatDatapack)   // picked up (and enabled) by the /reload below
            .command("reload")
            .waitUntil(() -> ChiselGroups.client().generation() > generation[0], 900)
            .run(() -> expect(ChiselGroups.client().generation() > generation[0] && !ChiselGroups.client().isEmpty(),
                "/reload rebuilt the index (generation " + generation[0] + " -> " + ChiselGroups.client().generation() + ")"))
            .run(ChiselHarness::checkCompat)
            .wait(30)
            .run(() -> Minecraft.getInstance().getToasts().clear())   // the item commands and /reload announce recipes
            .wait(3)
            .screenshot("chisel")
            .run(() -> SlateBuilding.LOGGER.info("[BuildingHarness] chisel summary: {} passed, {} failed", PASSED.get(), FAILED.get()));
    }

    // ------------------------------------------------------------------ checks

    private static void checkGroups() {
        final ChiselGroups g = ChiselGroups.client();
        SlateBuilding.LOGGER.info("[BuildingHarness] chisel index {}", g);
        for (final Block b : List.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.QUARTZ_BLOCK, Blocks.SANDSTONE,
                Blocks.COPPER_BLOCK, Blocks.CUT_COPPER, Blocks.ANDESITE)) {
            SlateBuilding.LOGGER.info("[BuildingHarness] chisel groups of {}: {}", id(b), describe(g, b));
        }
        expect(g.linked(Blocks.STONE, Blocks.STONE_BRICKS) && g.linked(Blocks.STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS), "stone ~ stone bricks ~ chiseled stone bricks");
        // Economy (design §1): nothing that vanilla only reaches by smelting or by adding an ingredient.
        expect(!g.linked(Blocks.STONE, Blocks.COBBLESTONE) && !g.linked(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE)
                && !g.linked(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS) && !g.linked(Blocks.STONE, Blocks.SMOOTH_STONE),
            "stone kept apart from cobblestone (smelting), mossy (moss added) and smooth stone (smelting)");
        expect(g.linked(Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE_TILES) && !g.linked(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE),
            "cobbled deepslate ~ deepslate tiles, natural deepslate kept apart (smelting)");
        expect(g.linked(Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR) && !g.linked(Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ)
                && !g.linked(Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE) && !g.linked(Blocks.BASALT, Blocks.SMOOTH_BASALT),
            "quartz block ~ pillar; smooth quartz / sandstone / basalt kept apart (smelting)");
        expect(g.linked(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE), "sandstone ~ cut sandstone");
        expect(g.linked(Blocks.ANDESITE, Blocks.POLISHED_ANDESITE), "andesite ~ polished andesite");
        expect(!g.linked(Blocks.COPPER_BLOCK, Blocks.CUT_COPPER) && !g.linked(Blocks.COPPER_BLOCK, Blocks.CHISELED_COPPER),
            "copper block kept apart from cut/chiseled copper (1:4 stonecutter link)");
        expect(g.linked(Blocks.CUT_COPPER, Blocks.CHISELED_COPPER) && g.linked(Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.WAXED_EXPOSED_CHISELED_COPPER),
            "cut ~ chiseled copper within one state");
        expect(!g.linked(Blocks.CUT_COPPER, Blocks.EXPOSED_CUT_COPPER) && !g.linked(Blocks.CUT_COPPER, Blocks.WAXED_CUT_COPPER)
            && !g.linked(Blocks.EXPOSED_CHISELED_COPPER, Blocks.WEATHERED_CHISELED_COPPER), "no group crosses oxidation or wax states");
        expect(!g.contains(Blocks.STONE_SLAB) && !g.contains(Blocks.STONE_BRICK_STAIRS) && !g.contains(Blocks.SAND),
            "no shapes or gravity blocks in groups");
        expect(g.inWorld(Blocks.STONE_BRICKS) && !g.inWorld(Blocks.STONE) && !g.inWorld(Blocks.DEEPSLATE),
            "in-world flags: stone bricks yes, stone / deepslate no (they drop cobble)");
        expect(g.groups().stream().anyMatch(x -> "stonecutter".equals(x.source())) && g.groups().stream().anyMatch(x -> "families".equals(x.source())),
            "stonecutter and block-family providers contribute");
        final List<ChiselGroups.Group> stonePages = g.groupsOf(Blocks.STONE);
        expect(!stonePages.isEmpty() && "overrides".equals(stonePages.get(0).source()) && stonePages.stream().noneMatch(x -> "stonecutter".equals(x.source())),
            "stone's pages: shipped group first, covered stonecutter page folded away");
        final int parts = ChiselSync.partCount(g, 512);
        final ChiselGroups copy = ChiselSync.roundTrip(g, 512);
        final boolean flagsMatch = g.members().stream().allMatch(b -> g.inWorld(b) == copy.inWorld(b));
        expect(copy.groups().equals(g.groups()) && flagsMatch && copy.generation() == g.generation(),
            "sync round trip over " + parts + " parts (" + g.groups().size() + " groups)");
        final List<ChiselSwap.Page> pages = ChiselClient.pages(new ItemStack(Items.STONE_SLAB));
        expect(!pages.isEmpty() && pages.get(0).options().stream().anyMatch(o -> o.stack().is(Items.STONE_BRICK_SLAB))
                && pages.get(0).options().stream().noneMatch(o -> o.stack().is(Items.COBBLESTONE)),
            "wheel pages for a stone slab offer slabs only (" + (pages.isEmpty() ? "none" : optionsText(pages.get(0))) + ")");
    }

    private static void checkSwaps() {
        expect(slot(0).is(Items.STONE_BRICKS) && slot(0).getCount() == 32, "held stone x32 -> " + stackText(slot(0)));
        expect(slot(1).is(Items.STONE_BRICK_SLAB) && slot(1).getCount() == 16, "held stone slab x16 -> " + stackText(slot(1)) + " (shape kept)");
        expect(slot(2).is(Items.CHISELED_COPPER) && slot(2).getCount() == 8, "cut copper -> exposed refused, -> chiseled copper allowed: " + stackText(slot(2)));
        expect(slot(3).is(Items.COPPER_BLOCK) && slot(3).getCount() == 4, "copper block -> cut copper refused: " + stackText(slot(3)));
        expect(block(FULL_BLOCK).is(Blocks.CHISELED_STONE_BRICKS), "in world: stone bricks -> " + id(block(FULL_BLOCK).getBlock()));
        final BlockState stairs = block(STAIRS_BLOCK);
        expect(stairs.is(Blocks.STONE_STAIRS) && stairs.getValue(StairBlock.FACING) == Direction.WEST,
            "in world: west stone brick stairs -> " + stairs + " (shape and facing kept)");
    }

    /** Server thread: the loot guard and a deterministic rebuild. */
    private static void checkServer() {
        final IntegratedServer server = server();
        final ServerLevel level = server.overworld();
        final boolean stone = ChiselSwap.dropsOwnWorth(level, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null, Blocks.STONE, 1);
        final boolean bricks = ChiselSwap.dropsOwnWorth(level, BlockPos.ZERO, Blocks.STONE_BRICKS.defaultBlockState(), null, Blocks.STONE_BRICKS, 1);
        final boolean glass = ChiselSwap.dropsOwnWorth(level, BlockPos.ZERO, Blocks.GLASS.defaultBlockState(), null, Blocks.GLASS, 1);
        expect(!stone && bricks && !glass, "loot guard: stone (drops cobble) no, stone bricks yes, glass (drops nothing) no");
        final ChiselGroups before = ChiselGroups.server(server);
        ChiselSystem.rebuild(server);
        final ChiselGroups after = ChiselGroups.server(server);
        expect(after != before && after.groups().equals(before.groups()), "rebuild is deterministic (" + after.groups().size() + " groups)");
    }

    // ------------------------------------------------------------------ mod-compat formats

    /** Rechiseled's format, both entry grammars, a connecting variant, an optional unknown id and a shape entry. */
    private static final String RECHISELED_ANDESITE = """
        {"type": "rechiseled:chiseling", "entries": [
          "minecraft:andesite",
          {"block": "minecraft:polished_andesite", "stairs": "minecraft:polished_andesite_stairs", "slab": "minecraft:polished_andesite_slab", "slab_worth": 0.5},
          {"item": "minecraft:calcite"},
          {"block": "minecraft:granite", "connecting_block": "minecraft:polished_granite"},
          {"block": "notamod:missing", "optional": true},
          "minecraft:andesite_slab"
        ]}
        """;
    /** Diorite + cobblestone crafts TWO andesite: a 1:1 group of both would be a dupe, so andesite must fall out. */
    private static final String RECHISELED_DIORITE = """
        {"type": "rechiseled:chiseling", "entries": ["minecraft:diorite", "minecraft:andesite", "minecraft:polished_diorite"]}
        """;

    /**
     * A datapack in the harness world carrying each mod's group data (none of those mods is installed here): Rechiseled
     * {@code chiseling_recipes}, Chipped item tags (read from tags when the {@code chipped:workbench} recipe type is
     * absent) and Chisel Modern {@code carving} tags. {@code /reload} discovers and enables it.
     */
    private static void writeCompatDatapack() {
        final Path root = server().getWorldPath(LevelResource.DATAPACK_DIR).resolve("slate-chisel-harness");
        try {
            write(root.resolve("pack.mcmeta"), "{\"pack\": {\"pack_format\": 48, \"description\": \"Slate Building chisel harness\"}}");
            write(root.resolve("data/slatetest/chiseling_recipes/andesite.json"), RECHISELED_ANDESITE);
            write(root.resolve("data/slatetest/chiseling_recipes/diorite.json"), RECHISELED_DIORITE);
            write(root.resolve("data/chipped/tags/item/harness.json"),
                "{\"values\": [\"minecraft:end_stone\", \"minecraft:end_stone_bricks\", \"minecraft:purpur_block\", \"minecraft:sand\", \"minecraft:chest\"]}");
            write(root.resolve("data/chisel/tags/item/carving/harness.json"),
                "{\"values\": [\"minecraft:bricks\", \"minecraft:mud_bricks\", \"minecraft:copper_block\", \"minecraft:waxed_copper_block\"]}");
        } catch (final IOException e) {
            expect(false, "write the mod-compat datapack: " + e);
        }
    }

    private static void write(final Path file, final String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static void checkCompat() {
        final ChiselGroups g = ChiselGroups.client();
        final List<Block> rechiseled = members(g, "rechiseled", Blocks.ANDESITE);
        expect(rechiseled.equals(List.of(Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.CALCITE, Blocks.GRANITE, Blocks.POLISHED_GRANITE)),
            "Rechiseled format: bare / block / item entries + connecting variant; unknown id and slab skipped (" + ids(rechiseled) + ")");
        final List<Block> dupe = members(g, "rechiseled", Blocks.DIORITE);
        expect(dupe.equals(List.of(Blocks.DIORITE, Blocks.POLISHED_DIORITE)),
            "Rechiseled diorite group drops andesite (diorite + cobblestone -> 2 andesite would dupe) (" + ids(dupe) + ")");
        final List<Block> chipped = members(g, "chipped", Blocks.END_STONE);
        expect(chipped.equals(List.of(Blocks.END_STONE, Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK)),
            "Chipped tag group: falling sand and the chest filtered out (" + ids(chipped) + ")");
        final List<Block> chisel = members(g, "chisel", Blocks.BRICKS);
        expect(chisel.equals(List.of(Blocks.BRICKS, Blocks.MUD_BRICKS)) && !g.contains(Blocks.COPPER_BLOCK),
            "Chisel carving tag group: copper states split off and left alone (" + ids(chisel) + ")");
        final List<String> pages = g.groupsOf(Blocks.ANDESITE).stream().map(x -> x.source() + ":" + x.name().getString()).toList();
        // Other mods of the run may add pages after these two (Create's stonecutting does).
        expect(pages.size() >= 2 && pages.subList(0, 2).equals(List.of("overrides:Andesite", "rechiseled:Rechiseled")),
            "andesite's pages in provider order " + pages);
    }

    /** Members of the first group from {@code source} containing {@code block} (empty when there is none). */
    private static List<Block> members(final ChiselGroups g, final String source, final Block block) {
        for (final ChiselGroups.Group group : g.groups()) {
            if (group.source().equals(source) && group.contains(block)) return group.members();
        }
        return List.of();
    }

    private static String ids(final List<Block> blocks) {
        final StringJoiner j = new StringJoiner(", ");
        for (final Block b : blocks) j.add(id(b));
        return j.toString();
    }

    // ------------------------------------------------------------------ helpers

    private static void expect(final boolean ok, final String what) {
        (ok ? PASSED : FAILED).incrementAndGet();
        if (ok) SlateBuilding.LOGGER.info("[BuildingHarness] chisel PASS {}", what);
        else SlateBuilding.LOGGER.error("[BuildingHarness] chisel FAIL {}", what);
    }

    private static String describe(final ChiselGroups g, final Block b) {
        final List<ChiselGroups.Group> groups = g.groupsOf(b);
        if (groups.isEmpty()) return "(none)";
        final StringJoiner pages = new StringJoiner(" | ");
        for (final ChiselGroups.Group group : groups) {
            final StringJoiner members = new StringJoiner(", ", group.name().getString() + " [", "]");
            for (final Block m : group.members()) members.add(id(m));
            pages.add(members.toString());
        }
        return pages.toString();
    }

    private static String optionsText(final ChiselSwap.Page page) {
        final StringJoiner j = new StringJoiner(", ");
        for (final ChiselSwap.Option o : page.options()) j.add(id(o.stack()) + (o.current() ? "*" : ""));
        return j.toString();
    }

    private static String id(final Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).getPath();
    }

    private static String id(final ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
    }

    private static String stackText(final ItemStack s) {
        return s.isEmpty() ? "empty" : id(s) + " x" + s.getCount();
    }

    private static String text(final @Nullable net.minecraft.network.chat.Component c) {
        return c == null ? "unlocked" : c.getString();
    }

    private static ItemStack slot(final int slot) {
        final Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(slot);
    }

    private static BlockState block(final BlockPos pos) {
        final Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? Blocks.AIR.defaultBlockState() : mc.level.getBlockState(pos);
    }

    private static net.minecraft.world.entity.player.Player player() {
        return Minecraft.getInstance().player;
    }

    private static IntegratedServer server() {
        final IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) throw new IllegalStateException("the chisel scenario needs the integrated server");
        return server;
    }
}
