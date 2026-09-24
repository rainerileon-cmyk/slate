package dev.fallingcloud.slate.building.fabric;

import com.mojang.authlib.GameProfile;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric implementation of {@link BuildingPlatform} (ServiceLoader, see this jar's {@code META-INF/services}).
 *
 * <p>Protection. Fabric API has a break event ({@code PlayerBlockBreakEvents.BEFORE}, which Fabric claim mods hook) but
 * no block-place event, so operations and symmetry ask everything Fabric does offer before they write:
 * <ul>
 *   <li>breaks: game-master blocks (vanilla's rule), the Common Protection API when installed (Flan, GOML, ...), then
 *       {@code PlayerBlockBreakEvents.BEFORE};</li>
 *   <li>placements: game-master blocks, spawn protection and the world border ({@code mayInteract}), adventure-mode
 *       placement rules ({@code mayUseItemAt}), the Common Protection API's {@code canPlaceBlock} when installed, and
 *       {@code PlayerBlockBreakEvents.BEFORE} for the position as it is now (air or the replaceable block being
 *       overwritten; claim mods refuse that for claimed land). Not {@code UseBlockCallback}: its listeners act.</li>
 * </ul>
 * The limit: a claim mod that protects placements only by patching {@code BlockItem} itself, or only through
 * {@code UseBlockCallback}, and implements neither the break event nor the Common Protection API, is not asked.
 */
public final class FabricBuildingPlatform implements BuildingPlatform {

    @Override
    public boolean canBreak(final ServerPlayer p, final ServerLevel level, final BlockPos pos, final BlockState state) {
        if (state.getBlock() instanceof GameMasterBlock && !p.canUseGameMasterBlocks()) return false;   // vanilla's rule, as on NeoForge
        if (!CommonProtection.allows(CommonProtection.BREAK, level, pos, p)) return false;
        // A double slab is shown as a single one: KleeSlabs (through Balm) splits it inside this event otherwise.
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, p, pos, BuildingPlatform.permissionProbe(state), level.getBlockEntity(pos));
    }

    @Override
    public boolean tryPlace(final ServerPlayer p, final ServerLevel level, final BlockPos pos, final BlockState state,
                            final Direction face, final int flags) {
        if (!mayPlace(p, level, pos, state, face)) return false;
        return level.setBlock(pos, state, flags);
    }

    private static boolean mayPlace(final ServerPlayer p, final ServerLevel level, final BlockPos pos, final BlockState state, final Direction face) {
        if (state.getBlock() instanceof GameMasterBlock && !p.canUseGameMasterBlocks()) return false;
        if (!level.mayInteract(p, pos)) return false;
        if (!p.mayUseItemAt(pos, face, new ItemStack(state.getBlock().asItem()))) return false;
        if (!CommonProtection.allows(CommonProtection.PLACE, level, pos, p)) return false;
        // The break event as the claim check for this position. Callers already asked canBreak before overwriting a
        // different solid block; ask here for what they did not: air, replaceable blocks and merges (slab onto slab).
        final BlockState existing = level.getBlockState(pos);
        if (!existing.isAir() && !existing.canBeReplaced() && existing.getBlock() != state.getBlock()) return true;
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, p, pos, existing, level.getBlockEntity(pos));
    }

    @Override
    public void openMenu(final ServerPlayer p, final MenuProvider provider) {
        p.openMenu(provider);
    }

    @Override
    public boolean isFakePlayer(final Player p) {
        return p instanceof FakePlayer;
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return FabricItemGroup.builder();
    }

    /**
     * Patbox's Common Protection API ({@code eu.pb4.common.protection.api.CommonProtection}), looked up reflectively so it
     * stays an optional dependency: claim mods implement it for exactly this kind of check.
     */
    private static final class CommonProtection {

        static final String BREAK = "canBreakBlock";
        static final String PLACE = "canPlaceBlock";

        private static final @Nullable MethodHandle CAN_BREAK = find(BREAK);
        private static final @Nullable MethodHandle CAN_PLACE = find(PLACE);

        static boolean allows(final String what, final Level level, final BlockPos pos, final ServerPlayer p) {
            final MethodHandle h = what.equals(PLACE) ? CAN_PLACE : CAN_BREAK;
            if (h == null) return true;
            try {
                return (boolean) h.invoke(level, pos, p.getGameProfile(), (Player) p);
            } catch (final Throwable t) {
                SlateBuilding.LOGGER.warn("[Slate Building] Common Protection API {} failed; refusing that position", what, t);
                return false;
            }
        }

        private static @Nullable MethodHandle find(final String name) {
            try {
                final Class<?> api = Class.forName("eu.pb4.common.protection.api.CommonProtection");
                return MethodHandles.publicLookup().findStatic(api, name,
                    MethodType.methodType(boolean.class, Level.class, BlockPos.class, GameProfile.class, Player.class));
            } catch (final ReflectiveOperationException | LinkageError e) {
                return null;   // not installed (or another version): the break event still applies
            }
        }

        private CommonProtection() {}
    }
}
