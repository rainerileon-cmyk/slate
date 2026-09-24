package dev.fallingcloud.slate.building.block;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.registry.BuildingBlockEntities;
import dev.fallingcloud.slate.building.registry.BuildingComponents;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The one block entity behind every shape block: it only remembers the MATERIAL (the full block this stair / slab /
 * panel is made of). Everything material-dependent (model, drops, hardness, sound) is derived from it; light and
 * occlusion live in the block state ({@link ShapeBehaviour#LIGHT}, {@link ShapeBehaviour#OPAQUE}).
 *
 * <p>What can be a material: anything but air and another shape block ({@link #canHold}). Every way in
 * ({@link #setMaterial}, {@link #loadAdditional} from a save, a chunk packet, {@code /data} or a schematic, and
 * {@link #applyImplicitComponents} from an item) goes through that check, because the shape delegates hardness, map
 * colour and more to its material AT THE SAME POSITION: a shape made of a shape would call itself until the stack
 * overflows (a crash on every mining attempt, or on the server every time a map is held over it). An invalid material
 * loads as "unset".
 *
 * <p>Sync: the material travels in {@link #getUpdateTag} (chunk load) and {@link #getUpdatePacket} (changes).
 * Vanilla/Fabric and NeoForge's default {@code onDataPacket}/{@code handleUpdateTag} all end in
 * {@link #loadAdditional}, which re-meshes the section on the client when the material actually changed. Only a real
 * CHANGE of an already-known material asks for an immediate rebuild ({@code sendBlockUpdated} flag 8, so there is no
 * frame with the old texture); the first fill of a fresh entity (every shape in a chunk that streams in) just queues
 * one, so joining a large build does not make every section "changed by the player" (synchronous compiles under the
 * Semi/Fully Blocking chunk-builder options). No light re-check either way: the light engines read the state.
 *
 * <p>Items: the material component ({@link BuildingComponents#MATERIAL}, a block id) is applied automatically when
 * a shape item is placed (vanilla {@code BlockItem.place} → {@code applyComponentsFromItemStack} →
 * {@link #applyImplicitComponents}), and exported by {@link #collectImplicitComponents} for pick-block with data.
 */
public class ShapeBlockEntity extends BlockEntity {

    private static final String TAG_MATERIAL = "material";
    private static final String TAG_DIAGONALS = "diagonals";
    /** {@code Block.UPDATE_IMMEDIATE}: the client rebuilds the section on this frame instead of queueing it. */
    private static final int RERENDER_NOW = Block.UPDATE_IMMEDIATE;

    private static volatile boolean warnedInvalid;

    private @Nullable BlockState material;
    /** Diagonal arms of a fence / wall / pane ({@link DiagonalShapes.Diagonal} bits); 0 for every other shape. */
    private int diagonals;

    public ShapeBlockEntity(final BlockPos pos, final BlockState state) {
        super(BuildingBlockEntities.SHAPE.get(), pos, state);
    }

    /** The diagonal arms, a mask of {@link DiagonalShapes.Diagonal#bit()}s. */
    public int diagonals() {
        return diagonals;
    }

    /** Stores the arms ({@link DiagonalShapes#refresh}); a change is saved, sent to clients and re-meshed. */
    public void setDiagonals(final int mask) {
        if (mask == diagonals) return;
        diagonals = mask;
        setChanged();
        if (level != null) {
            final BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS | RERENDER_NOW);
        }
    }

    /**
     * Whether {@code block} may be stored as a shape's material: not air and not one of our shape blocks (which would
     * delegate to itself forever). Anything else is accepted, including materials the current config no longer
     * allows, so a rules change never wipes existing builds.
     */
    public static boolean canHold(final @Nullable Block block) {
        return block != null && block != Blocks.AIR && block != Blocks.CAVE_AIR && block != Blocks.VOID_AIR && !(block instanceof ShapeBlock);
    }

    /** The material (a full block's state), or null while unset. Never air, never a shape block. */
    public @Nullable BlockState material() {
        return material;
    }

    public boolean hasMaterial() {
        return material != null;
    }

    /**
     * Sets the material; null, air or anything {@link #canHold} rejects clears it. On the server this marks the chunk
     * dirty, syncs the state's light/opacity to the material and sends the update to watching clients; on the client it
     * re-meshes immediately.
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
     * change that did not come with a matching state updates the state too, which also re-lights the block. Same
     * block, so this entity stays. Returns the (possibly new) state.
     */
    private BlockState syncStateToMaterial() {
        final BlockState state = getBlockState();
        if (level == null || level.isClientSide()) return state;
        final BlockState synced = ShapeBehaviour.withMaterial(state, material);
        if (synced == state || level.getBlockState(worldPosition) != state) return state;
        level.setBlock(worldPosition, synced, Block.UPDATE_CLIENTS);
        return synced;
    }

    /**
     * Stores the material (normalised: null, air and shape blocks become "unset"); returns whether it changed. No light
     * check: emission and occlusion are state properties, re-lit by {@link #syncStateToMaterial}'s {@code setBlock} on
     * the server and by the resulting block-update packet on clients.
     */
    private boolean replaceMaterial(final @Nullable BlockState newMaterial) {
        BlockState normalised = newMaterial == null || newMaterial.isAir() ? null : newMaterial;
        if (normalised != null && !canHold(normalised.getBlock())) {
            if (!warnedInvalid) {
                warnedInvalid = true;
                SlateBuilding.LOGGER.warn("[Slate Building] shape at {} was given {} as its material; a shape cannot be made of a shape, left unset",
                    worldPosition, BuiltInRegistries.BLOCK.getKey(normalised.getBlock()));
            }
            normalised = null;
        }
        if (Objects.equals(normalised, material)) return false;
        material = normalised;
        return true;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (material != null) tag.put(TAG_MATERIAL, NbtUtils.writeBlockState(material));
        if (diagonals != 0) tag.putInt(TAG_DIAGONALS, diagonals);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // An unknown block id (mod removed) reads back as air, which clears the material: the shape shows "unset".
        final BlockState loaded = tag.contains(TAG_MATERIAL, Tag.TAG_COMPOUND)
            ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(TAG_MATERIAL))
            : null;
        final boolean known = material != null;
        final int arms = tag.getInt(TAG_DIAGONALS) & 0xF;
        final boolean armsChanged = arms != diagonals;
        diagonals = arms;
        if (replaceMaterial(loaded) || armsChanged) rerenderIfClient(known);
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
            rerenderIfClient(true);   // a local placement: the player is looking at it
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
        tag.remove(TAG_DIAGONALS);       // arms depend on the neighbours, never on the item
    }

    /**
     * Client: re-mesh the section for the new material. {@code urgent} (a known material changed, or a local placement)
     * rebuilds it this frame; otherwise (first fill of a fresh entity, e.g. a chunk streaming in) it is queued like any
     * other section update.
     */
    private void rerenderIfClient(final boolean urgent) {
        if (level != null && level.isClientSide()) {
            final BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, urgent ? RERENDER_NOW : 0);
        }
    }
}
