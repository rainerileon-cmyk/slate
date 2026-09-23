package dev.fallingcloud.slate.building.client.render;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Translucent "ghost" blocks: the placement preview and the planned result of a building mode (design §6).
 * Immediate mode: submit what should be visible THIS frame (before the world renders, e.g. from a client tick or
 * the render-frame hook) and it is drawn once in {@code SlateRenderEvents.AFTER_TRANSLUCENT}. Large plans use
 * {@link #submitCached}, which keeps a vertex buffer until the version changes. Opacity, saturation, outline and
 * pulse come from {@code PreviewSettings}; above {@code maxBlocks} only outlines are drawn.
 *
 * <p>Owner: B (render). Skeleton stub with the final API: submissions are accepted and dropped.
 */
public final class GhostRenderer {

    /** How a ghost reads. */
    public enum Style {
        /** Normal placement. */
        PLACE,
        /** Replaces an existing block (amber tint). */
        REPLACE,
        /** Will be removed (red, cross-hatched / outlined). */
        REMOVE,
        /** Cannot be placed here (red outline only). */
        INVALID
    }

    /**
     * One ghost block.
     *
     * @param pos      where
     * @param state    the block state to show (for our shape blocks: the shape's state)
     * @param material the material rendered through the shape (our shape blocks only), else null
     * @param style    how it reads
     */
    public record Ghost(BlockPos pos, BlockState state, @Nullable BlockState material, Style style) {
        public Ghost {
            pos = pos.immutable();
        }

        public static Ghost of(final BlockPos pos, final BlockState state, final Style style) {
            return new Ghost(pos, state, null, style);
        }
    }

    /** Shows {@code ghost} this frame. */
    public static void submit(final Ghost ghost) {
    }

    /**
     * Shows a (large) set of ghosts this frame, rebuilding the cached vertex buffer for {@code key} only when
     * {@code version} differs from the cached one ({@code supplier} is not called otherwise). Buffers not submitted
     * for a frame are released.
     */
    public static void submitCached(final Object key, final int version, final Supplier<List<Ghost>> supplier) {
    }

    private GhostRenderer() {}
}
