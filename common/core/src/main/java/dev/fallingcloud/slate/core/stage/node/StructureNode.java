package dev.fallingcloud.slate.core.stage.node;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.stage.StageLevel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A structure-block template ({@code .nbt}, the file a structure block saves) placed in the stage level and meshed
 * once. Reads the template format directly (size, palette or palettes, blocks with state, pos and block-entity nbt)
 * so chests, signs and other block entities inside come alive. Entities in the template are ignored. Positions
 * listed in {@code hide} (template-relative) are left empty: scene anchors marked with signs use this.
 */
public class StructureNode extends MeshedBlocksNode {

    public StructureNode(final StageLevel level, final CompoundTag template) {
        this(level, template, null);
    }

    public StructureNode(final StageLevel level, final CompoundTag template, @Nullable final Set<BlockPos> hide) {
        super(level);
        load(template, hide);
        named("structure");
    }

    private void load(final CompoundTag tag, @Nullable final Set<BlockPos> hide) {
        final ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
        final Vec3i sz = sizeTag.size() == 3 ? new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2)) : Vec3i.ZERO;
        ListTag palette;
        if (tag.contains("palettes", Tag.TAG_LIST)) {
            final ListTag palettes = tag.getList("palettes", Tag.TAG_LIST);
            palette = palettes.isEmpty() ? new ListTag() : palettes.getList(0);
        } else {
            palette = tag.getList("palette", Tag.TAG_COMPOUND);
        }
        final BlockState[] states = new BlockState[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            try {
                states[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i));
            } catch (final Exception e) {
                states[i] = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
        }
        final ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        int placed = 0;
        int maxX = sz.getX() - 1, maxY = sz.getY() - 1, maxZ = sz.getZ() - 1;
        for (int i = 0; i < blocks.size(); i++) {
            final CompoundTag b = blocks.getCompound(i);
            final ListTag pos = b.getList("pos", Tag.TAG_INT);
            if (pos.size() != 3) continue;
            final int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            final int stateIndex = b.getInt("state");
            if (stateIndex < 0 || stateIndex >= states.length) continue;
            final BlockState state = states[stateIndex];
            if (state.isAir()) continue;
            if (hide != null && hide.contains(new BlockPos(x, y, z))) continue;
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            set(x, y, z, state, b.contains("nbt", Tag.TAG_COMPOUND) ? b.getCompound("nbt") : null);
            placed++;
        }
        finishFill(new Vec3i(maxX + 1, maxY + 1, maxZ + 1));
        Slate.LOGGER.debug("[Slate] stage: structure {}x{}x{}, {} blocks", size.getX(), size.getY(), size.getZ(), placed);
    }

    /** Reads a gzip-compressed template from a resource ({@code assets/<ns>/<path>}), or null when absent. */
    @Nullable
    public static CompoundTag readResource(final ResourceLocation location) {
        try {
            final Resource res = Minecraft.getInstance().getResourceManager().getResource(location).orElse(null);
            if (res == null) return null;
            try (InputStream in = res.open()) {
                return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            }
        } catch (final IOException e) {
            Slate.LOGGER.warn("[Slate] stage: cannot read structure {}: {}", location, e.toString());
            return null;
        }
    }

    /** Reads a gzip-compressed template from disk, or null when absent or unreadable. */
    @Nullable
    public static CompoundTag readFile(final Path file) {
        if (!Files.isRegularFile(file)) return null;
        try (InputStream in = Files.newInputStream(file)) {
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (final IOException e) {
            Slate.LOGGER.warn("[Slate] stage: cannot read structure {}: {}", file, e.toString());
            return null;
        }
    }
}
