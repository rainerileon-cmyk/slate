package dev.fallingcloud.slate.building.registry;

import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Block entity types. {@link #SHAPE} ({@code slate_building:shape}) is shared by every shape block and stores the
 * material. Its factory resolves the shape blocks, which is safe because both loaders register blocks before block
 * entity types (NeoForge's RegisterEvent order, the Fabric flush order).
 */
public final class BuildingBlockEntities {

    public static final RegistryRef<BlockEntityType<ShapeBlockEntity>> SHAPE = BuildingRegistry.register(Registries.BLOCK_ENTITY_TYPE, "shape",
        () -> BlockEntityType.Builder.of(ShapeBlockEntity::new, BuildingBlocks.resolved()).build(null));

    /** Forces class initialisation (queues the entries). Called by {@link BuildingRegistry#bootstrap()}. */
    static void init() {}

    private BuildingBlockEntities() {}
}
