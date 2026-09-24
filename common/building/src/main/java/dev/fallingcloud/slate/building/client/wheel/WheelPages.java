package dev.fallingcloud.slate.building.client.wheel;

import dev.fallingcloud.slate.building.chisel.ChiselGroups;
import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.WheelSettings;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.Shape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the wheel pages for a {@link WheelTarget} from the wheel settings (design §5): each configured wheel
 * becomes a page of its available shapes (unavailable ones hidden or greyed, {@code hideUnavailable}), split into
 * several pages when longer than {@code maxSlices}; then one page per chisel group of the material (each provider's
 * group is its own page, never merged), when chisel is unlocked. Locks follow the server rules and the toolbox.
 */
public final class WheelPages {

    /** Everything a wheel shows for one target. */
    public record WheelSet(WheelTarget target, WheelSlice full, List<WheelPage> pages) {
        public boolean isEmpty() {
            return pages.isEmpty();
        }
    }

    /** The swap wheel's pages: the configured shape wheels, then the chisel groups when enabled and unlocked. */
    public static WheelSet overlay(final WheelTarget t) {
        final List<WheelPage> pages = new ArrayList<>(shapePages(t, WheelConfig.wheel().hideUnavailable));
        if (WheelConfig.wheel().includeChiselPage && chiselLock(t) == null) pages.addAll(chiselPages(t));
        return new WheelSet(t, fullSlice(t), pages);
    }

    /** The FULL block of the target's material (the wheel's centre). */
    public static WheelSlice fullSlice(final WheelTarget t) {
        final ItemStack stack = WheelSources.get().stackFor(t.material(), Shape.FULL, t.count());
        return new WheelSlice(WheelSlice.Kind.SHAPE, Shape.FULL, t.material(), stack, BuildingIcons.SHAPE_FULL,
            label(stack, t.material(), Shape.FULL), true, shapeLock(t), t.shape() == Shape.FULL);
    }

    /** The target as it is (the centre of chisel pages: picking it changes nothing). */
    public static WheelSlice currentSlice(final WheelTarget t) {
        ItemStack stack = WheelSources.get().stackFor(t.material(), t.shape(), t.count());
        if (stack.isEmpty() && t.shape() == Shape.FULL) stack = new ItemStack(t.material(), Math.max(1, t.count()));
        return new WheelSlice(WheelSlice.Kind.CHISEL, t.shape(), t.material(), stack, t.shape().icon(),
            label(stack, t.material(), t.shape()), true, null, true);
    }

    /** One page per configured wheel (split by {@code maxSlices}); empty wheels are skipped. */
    public static List<WheelPage> shapePages(final WheelTarget t, final boolean hideUnavailable) {
        final WheelSettings ws = WheelConfig.wheel();
        final int max = WheelConfig.maxSlices();
        final Component lock = shapeLock(t);
        final List<WheelPage> out = new ArrayList<>();
        for (int w = 0; w < ws.wheels.size(); w++) {
            final WheelSettings.Wheel wheel = ws.wheels.get(w);
            final List<WheelSlice> slices = new ArrayList<>();
            for (final Shape shape : WheelConfig.shapesOf(wheel)) {
                final boolean available = WheelSources.get().isAvailable(t.material(), shape);
                if (!available && hideUnavailable) continue;
                final ItemStack stack = available ? WheelSources.get().stackFor(t.material(), shape, t.count()) : ItemStack.EMPTY;
                slices.add(new WheelSlice(WheelSlice.Kind.SHAPE, shape, t.material(), stack, shape.icon(),
                    label(stack, t.material(), shape), available, lock, shape == t.shape()));
            }
            final String name = wheel.name.isBlank() ? Component.translatable("slate_building.ui.wheel.unnamed", w + 1).getString() : wheel.name;
            split(out, "wheel:" + w, name, WheelSlice.Kind.SHAPE, slices, max);
        }
        return out;
    }

    /** One page per chisel group of the material (members keep the current shape). */
    public static List<WheelPage> chiselPages(final WheelTarget t) {
        final List<WheelPage> out = new ArrayList<>();
        final List<ChiselGroups.Group> groups = WheelSources.get().chiselGroups(t.material());
        final int max = WheelConfig.maxSlices();
        for (int gi = 0; gi < groups.size(); gi++) {
            final ChiselGroups.Group group = groups.get(gi);
            final List<WheelSlice> slices = new ArrayList<>();
            for (final Block member : group.members()) {
                if (member == null) continue;
                ItemStack stack = WheelSources.get().stackFor(member, t.shape(), t.count());
                if (stack.isEmpty()) stack = new ItemStack(member, Math.max(1, t.count()));
                slices.add(new WheelSlice(WheelSlice.Kind.CHISEL, t.shape(), member, stack, BuildingIcons.CHISEL,
                    label(stack, member, t.shape()), true, null, member == t.material()));
            }
            if (slices.size() < 2) continue;                    // a group of one is nothing to chisel to
            split(out, "chisel:" + group.source() + ":" + gi, group.name().getString(), WheelSlice.Kind.CHISEL, slices, max);
        }
        return out;
    }

    private static void split(final List<WheelPage> out, final String key, final String name, final WheelSlice.Kind kind,
                              final List<WheelSlice> slices, final int max) {
        if (slices.isEmpty()) return;
        final int parts = (slices.size() + max - 1) / max;
        for (int p = 0; p < parts; p++) {
            final List<WheelSlice> part = slices.subList(p * max, Math.min(slices.size(), (p + 1) * max));
            final Component title = parts == 1 ? Component.literal(name)
                : Component.translatable("slate_building.ui.wheel.part", name, p + 1, parts);
            out.add(new WheelPage(key + ":" + p, title, kind, part));
        }
    }

    /** Why the target's shape cannot be changed right now, or null. */
    public static @Nullable Component shapeLock(final WheelTarget t) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) return null;
        if (t.held()) {
            if (!BuildingServerSettings.effective(player).variants().swapNeedsTool) return null;
            return ToolboxAccess.of(player).tier(ToolType.HAMMER) > 0 ? null : needs(ToolType.HAMMER);
        }
        return ToolboxAccess.of(player).tier(ToolType.HAMMER) > 0 ? null : needs(ToolType.HAMMER);
    }

    /** Why chiselling the target is not possible right now (disabled on the server, no chisel / too low a tier), or null. */
    public static @Nullable Component chiselLock(final WheelTarget t) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) return Component.translatable("slate_building.ui.chisel.disabled");
        final BuildingServerSettings rules = BuildingServerSettings.effective(player);
        if (!rules.chisel().enabled || (!t.held() && !rules.chisel().inWorld)) return Component.translatable("slate_building.ui.chisel.disabled");
        final int need = t.held() ? 1 : 2;
        if (ToolboxAccess.of(player).tier(ToolType.CHISEL) >= need) return null;
        return need == 1 ? needs(ToolType.CHISEL)
            : Component.translatable("slate_building.lock.needs_tool", Component.translatable("slate_building.tool_tiered",
                dev.fallingcloud.slate.building.toolbox.ToolTier.byLevel(need).displayName(), ToolType.CHISEL.displayName()));
    }

    private static Component needs(final ToolType tool) {
        return Component.translatable("slate_building.lock.needs_tool", tool.displayName());
    }

    private static Component label(final ItemStack stack, final Block material, final Shape shape) {
        return stack.isEmpty() ? variantName(material, shape) : stack.getHoverName();
    }

    /** "Oak Planks Stairs"-style fallback name; FULL is the material's own name. */
    public static Component variantName(final Block material, final Shape shape) {
        if (shape == Shape.FULL) return material.getName();
        final ItemStack stack = WheelSources.get().stackFor(material, shape, 1);
        if (!stack.isEmpty()) return stack.getHoverName();
        return Component.translatable("slate_building.ui.variant_name", material.getName(), shape.displayName());
    }

    /** Registry id of a block (for payloads). */
    public static net.minecraft.resources.ResourceLocation id(final Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    private WheelPages() {}
}
