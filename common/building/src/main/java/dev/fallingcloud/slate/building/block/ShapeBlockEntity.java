package dev.fallingcloud.slate.building.block;

import dev.fallingcloud.slate.building.registry.BuildingBlockEntities;
import dev.fallingcloud.slate.building.registry.BuildingComponents;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The one block entity behind every shape block: it only remembers the MATERIAL (the full block this stair / slab /
 * panel is made of). Everything material-dependent (model, drops, hardness, sound, light) is derived from it.
 *
 * <p>Sync: the material travels in {@link #getUpdateTag} (chunk load) and {@link #getUpdatePacket} (changes).
 * Vanilla/Fabric and NeoForge's default {@code onDataPacket}/{@code handleUpdateTag} all end in
 * {@link #loadAdditional}, which re-meshes the section on the client when the material actually changed
 * ({@code sendBlockUpdated} with flag 8 = rebuild now, so there is no frame with the old texture).
 *
 * <p>Items: the material component ({@link BuildingComponents#MATERIAL}, a block id) is applied automatically when
 * a shape item is placed (vanilla {@code BlockItem.place} → {@code applyComponentsFromItemStack} →
 * {@link #applyImplicitComponents}), and exported by {@link #collectImplicitComponents} for pick-block with data.
 *
 * <p>Skeleton: implemented fully. Owner A may add behaviour (e.g. more synced fields), keeping the contract above.
 */
public class ShapeBlockEntity extends BlockEntity {

    private static final String TAG_MATERIAL = "material";
    /** {@code Block.UPDATE_IMMEDIATE}: the client rebuilds the section on this frame instead of queueing it. */
    private static final int RERENDER_NOW = Block.UPDATE_IMMEDIATE;

    private @Nullable BlockState material;

    public ShapeBlockEntity(final BlockPos pos, final BlockState state) {
        super(BuildingBlockEntities.SHAPE.get(), pos, state);
    }

    /** The material (a full block's state), or null while unset. Never air. */
    public @Nullable BlockState material() {
        return material;
    }

    public boolean hasMaterial() {
        return material != null;
    }

    /**
     * Sets the material; null or air clears it. On the server this marks the chunk dirty and sends the update to
     * watching clients; on the client it re-meshes immediately. Light is re-checked because the material decides the
     * block's light emission.
     */
    public void setMaterial(final @Nullable BlockState newMaterial) {
        if (!replaceMaterial(newMaterial)) return;
        setChanged();
        if (level != null) {
            final BlockState state = syncStateToMaterial();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS | RERENDER_NOW);
        }
    }

    /**
     * Server side: the block state mirrors the material's light and opacity ({@link ShapeBehaviour#LIGHT},
     * {@link ShapeBehaviour#OPAQUE}; light engines and face culling read them without the block entity), so a material
     * change that did not come with a matching state updates the state too. Same block, so this entity stays.
     * Returns the (possibly new) state.
     */
    private BlockState syncStateToMaterial() {
        final BlockState state = getBlockState();
        if (level == null || level.isClientSide()) return state;
        final BlockState synced = ShapeBehaviour.withMaterial(state, material);
        if (synced == state || level.getBlockState(worldPosition) != state) return state;
        level.setBlock(worldPosition, synced, Block.UPDATE_CLIENTS);
        return synced;
    }

    /** Stores the material; returns whether it changed. Also re-checks light (both sides). */
    private boolean replaceMaterial(final @Nullable BlockState newMaterial) {
        final BlockState normalised = newMaterial == null || newMaterial.isAir() ? null : newMaterial;
        if (Objects.equals(normalised, material)) return false;
        material = normalised;
        if (level != null) level.getLightEngine().checkBlock(worldPosition);
        return true;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (material != null) tag.put(TAG_MATERIAL, NbtUtils.writeBlockState(material));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // An unknown block id (mod removed) reads back as air, which clears the material: the shape shows "unset".
        final BlockState loaded = tag.contains(TAG_MATERIAL, Tag.TAG_COMPOUND)
            ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(TAG_MATERIAL))
            : null;
        if (replaceMaterial(loaded)) rerenderIfClient();
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(final BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        final ResourceLocation id = input.get(BuildingComponents.MATERIAL.get());
        if (id == null) return;
        final Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block != null && replaceMaterial(block.defaultBlockState())) {
            rerenderIfClient();
            syncStateToMaterial();
        }
    }

    @Override
    protected void collectImplicitComponents(final DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (material != null) components.set(BuildingComponents.MATERIAL.get(), BuiltInRegistries.BLOCK.getKey(material.getBlock()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(final CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(TAG_MATERIAL);
    }

    private void rerenderIfClient() {
        if (level != null && level.isClientSide()) {
            final BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, RERENDER_NOW);
        }
    }
}
