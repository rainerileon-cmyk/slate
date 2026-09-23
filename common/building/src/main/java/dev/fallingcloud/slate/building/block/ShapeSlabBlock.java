package dev.fallingcloud.slate.building.block;

import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.SlabBlock;

/**
 * The SLAB shape block (vanilla slab; a double slab is worth 2 units and merges only with the same material). One instance serves every material; the material lives in {@link ShapeBlockEntity}.
 *
 * <p>Owner: A (variants). Skeleton placeholder: registered, has the block entity, and reports a full cube with one
 * unit so everything downstream compiles and runs; A adds states, geometry, placement, merging and material
 * delegation (design §2). Keep the constructor signature: {@code BuildingBlocks} constructs it with shared base
 * properties that the constructor may refine.
 */
public class ShapeSlabBlock extends SlabBlock implements ShapeBlock, EntityBlock {

    public ShapeSlabBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public Shape shape() {
        return Shape.SLAB;
    }

    @Override
    public List<AABB> renderBoxes(final BlockState state) {
        return FULL_CUBE;
    }

    @Override
    public int units(final BlockState state) {
        return 1;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ShapeBlockEntity(pos, state);
    }
}
