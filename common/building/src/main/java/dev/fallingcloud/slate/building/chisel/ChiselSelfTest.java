package dev.fallingcloud.slate.building.chisel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Checks of the package-private safety rules on synthetic input, for the dev harness ({@code chisel} scenario): the
 * shipped groups are already clean, so these feed the rules deliberately bad groups. Needs bound registries (a running
 * game); Minecraft's registries cannot be bootstrapped in a plain unit test under NeoForge.
 */
public final class ChiselSelfTest {

    private ChiselSelfTest() {}

    /** Check name → passed, in a fixed order. */
    public static Map<String, Boolean> run() {
        final Map<String, Boolean> out = new LinkedHashMap<>();

        final ChiselRules.BadPairs bad = new ChiselRules.BadPairs();
        bad.add(Blocks.COPPER_BLOCK, Blocks.CUT_COPPER);
        bad.add(Blocks.COPPER_BLOCK, Blocks.CHISELED_COPPER);
        final List<List<Block>> copper = ChiselRules.clean(
            List.of(Blocks.COPPER_BLOCK, Blocks.CUT_COPPER, Blocks.EXPOSED_CUT_COPPER, Blocks.WAXED_CUT_COPPER, Blocks.CHISELED_COPPER, Blocks.STONE),
            b -> true, bad);
        out.put("rules split a mixed copper group per state and drop the 1:4 copper block (" + copper + ")",
            copper.equals(List.of(List.of(Blocks.CUT_COPPER, Blocks.CHISELED_COPPER))));

        final ChiselRules.BadPairs slab = new ChiselRules.BadPairs();
        slab.add(Blocks.STONE, Blocks.SMOOTH_STONE);   // pretend 1 stone -> 2 smooth stone
        final List<List<Block>> tie = ChiselRules.clean(List.of(Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS), b -> true, slab);
        out.put("a lone bad pair removes the recipe's output, the raw block stays (" + tie + ")",
            tie.equals(List.of(List.of(Blocks.STONE, Blocks.STONE_BRICKS))));

        out.put("eligible: stone, glass, oak log; not: slab, stairs, sand, chest, door, bed, barrier",
            ChiselRules.eligible(Blocks.STONE) && ChiselRules.eligible(Blocks.GLASS) && ChiselRules.eligible(Blocks.OAK_LOG)
                && !ChiselRules.eligible(Blocks.STONE_SLAB) && !ChiselRules.eligible(Blocks.STONE_STAIRS) && !ChiselRules.eligible(Blocks.SAND)
                && !ChiselRules.eligible(Blocks.CHEST) && !ChiselRules.eligible(Blocks.OAK_DOOR) && !ChiselRules.eligible(Blocks.RED_BED)
                && !ChiselRules.eligible(Blocks.BARRIER));

        final ChiselRules.CopperKey exposedWaxed = ChiselRules.copperKey(Blocks.WAXED_EXPOSED_CUT_COPPER);
        out.put("copper keys: waxed exposed cut copper = age 1 waxed, stone = none (" + exposedWaxed + ")",
            exposedWaxed != null && exposedWaxed.age() == 1 && exposedWaxed.waxed() && ChiselRules.copperKey(Blocks.STONE) == null
                && ChiselRules.copperKey(Blocks.OXIDIZED_COPPER) != null && ChiselRules.copperKey(Blocks.OXIDIZED_COPPER).age() == 3);

        final BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        final BlockState copied = ChiselRules.copyShared(pillar, Blocks.PURPUR_PILLAR.defaultBlockState());
        out.put("shared properties copied: quartz pillar axis=x -> " + copied,
            copied.is(Blocks.PURPUR_PILLAR) && copied.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X);
        return out;
    }
}
