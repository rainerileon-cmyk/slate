package dev.fallingcloud.slate.building.ops.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A placement context for planning: "{@code player} places {@code stack} exactly at {@code pos}, against its
 * {@code face.getOpposite()} side", whatever is at {@code pos} right now. Vanilla's own context would move the target
 * one block out when {@code pos} holds a solid block (a REPLACE) or pick the neighbour when it is replaceable; this
 * one always answers {@code pos}, so {@code getStateForPlacement} gives the orientation the player would get there
 * (stairs facing away from them, slabs on the bottom when placed on top of something, logs along the face axis).
 */
public final class PlanPlaceContext extends BlockPlaceContext {

    public PlanPlaceContext(final Level level, final Player player, final ItemStack stack, final BlockPos pos, final Direction face) {
        super(level, player, InteractionHand.MAIN_HAND, stack,
            new BlockHitResult(Vec3.atCenterOf(pos).relative(face.getOpposite(), 0.5), face, pos, false));
        this.replaceClicked = true;
    }
}
