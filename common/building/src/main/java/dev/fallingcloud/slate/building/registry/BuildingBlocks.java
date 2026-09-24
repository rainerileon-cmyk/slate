package dev.fallingcloud.slate.building.registry;

import dev.fallingcloud.slate.building.block.LayerBlock;
import dev.fallingcloud.slate.building.block.PanelBlock;
import dev.fallingcloud.slate.building.block.PostBlock;
import dev.fallingcloud.slate.building.block.ShapeFenceBlock;
import dev.fallingcloud.slate.building.block.ShapeFenceGateBlock;
import dev.fallingcloud.slate.building.block.ShapePaneBlock;
import dev.fallingcloud.slate.building.block.ShapeSlabBlock;
import dev.fallingcloud.slate.building.block.ShapeStairBlock;
import dev.fallingcloud.slate.building.block.ShapeWallBlock;
import dev.fallingcloud.slate.building.block.StepBlock;
import dev.fallingcloud.slate.building.block.VerticalSlabBlock;
import dev.fallingcloud.slate.building.block.VerticalStairsBlock;
import dev.fallingcloud.slate.building.block.VerticalStepBlock;
import dev.fallingcloud.slate.building.variant.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.Nullable;

/**
 * Slate Building's shape blocks: one block per custom {@link Shape}, registered as {@code slate_building:<shape id>}
 * (e.g. {@code slate_building:vertical_slab}). The material is not part of the block; it lives in the block entity.
 */
public final class BuildingBlocks {

    private static final Map<Shape, RegistryRef<? extends Block>> BY_SHAPE = new EnumMap<>(Shape.class);
    private static final List<RegistryRef<? extends Block>> ALL = new ArrayList<>();

    public static final RegistryRef<ShapeStairBlock> STAIRS = shape(Shape.STAIRS, ShapeStairBlock::new);
    public static final RegistryRef<ShapeSlabBlock> SLAB = shape(Shape.SLAB, ShapeSlabBlock::new);
    public static final RegistryRef<VerticalSlabBlock> VERTICAL_SLAB = shape(Shape.VERTICAL_SLAB, VerticalSlabBlock::new);
    public static final RegistryRef<VerticalStairsBlock> VERTICAL_STAIRS = shape(Shape.VERTICAL_STAIRS, VerticalStairsBlock::new);
    public static final RegistryRef<ShapeWallBlock> WALL = shape(Shape.WALL, ShapeWallBlock::new);
    public static final RegistryRef<ShapeFenceBlock> FENCE = shape(Shape.FENCE, ShapeFenceBlock::new);
    public static final RegistryRef<StepBlock> STEP = shape(Shape.STEP, StepBlock::new);
    public static final RegistryRef<PanelBlock> PANEL = shape(Shape.PANEL, PanelBlock::new);
    public static final RegistryRef<ShapeFenceGateBlock> FENCE_GATE = shape(Shape.FENCE_GATE, ShapeFenceGateBlock::new);
    public static final RegistryRef<VerticalStepBlock> VERTICAL_STEP = shape(Shape.VERTICAL_STEP, VerticalStepBlock::new);
    public static final RegistryRef<PostBlock> POST = shape(Shape.POST, PostBlock::new);
    public static final RegistryRef<LayerBlock> LAYER = shape(Shape.LAYER, LayerBlock::new);
    public static final RegistryRef<ShapePaneBlock> PANE = shape(Shape.PANE, ShapePaneBlock::new);

    /**
     * Base properties every shape block starts from (a fresh copy per block). They describe a generic material;
     * the real hardness, sound, light and drops are delegated to the material per position by the block classes,
     * which may refine these in their constructors. No loot table: drops are computed from the material.
     */
    public static BlockBehaviour.Properties baseProperties() {
        return BlockBehaviour.Properties.of()
            .strength(1.5F)
            .noOcclusion()
            .dynamicShape()
            .noLootTable();
    }

    /** The block realising {@code shape}; null for {@link Shape#FULL} (the material itself). */
    public static @Nullable RegistryRef<? extends Block> forShape(final Shape shape) {
        return BY_SHAPE.get(shape);
    }

    /** All shape blocks in {@link Shape} order. */
    public static List<RegistryRef<? extends Block>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Resolved shape blocks (only valid after registration). */
    public static Block[] resolved() {
        final Block[] out = new Block[ALL.size()];
        for (int i = 0; i < out.length; i++) out[i] = ALL.get(i).get();
        return out;
    }

    private static <T extends Block> RegistryRef<T> shape(final Shape shape, final Function<BlockBehaviour.Properties, T> factory) {
        final RegistryRef<T> ref = BuildingRegistry.register(Registries.BLOCK, shape.id(), () -> factory.apply(baseProperties()));
        BY_SHAPE.put(shape, ref);
        ALL.add(ref);
        return ref;
    }

    /** Forces class initialisation (queues the entries). Called by {@link BuildingRegistry#bootstrap()}. */
    static void init() {}

    private BuildingBlocks() {}
}
