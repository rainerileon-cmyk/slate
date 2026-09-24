package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.ops.ToolType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * One building tool, e.g. {@code slate_building:iron_hammer}: a {@link ToolType} at a {@link ToolTier}. Tools unlock
 * building modes while they sit in a toolbox the player carries, lose durability as operations run
 * ({@link ToolboxAccess#damageTool}), take Unbreaking and Mending, are repaired in an anvil with the tier material and
 * upgraded to Netherite at a smithing table. Netherite tools survive fire and lava like vanilla netherite gear.
 */
public class BuildingToolItem extends Item {

    /** Mode names per tooltip line (tooltips do not wrap). */
    private static final int NAMES_PER_LINE = 3;

    private final ToolType type;
    private final ToolTier tier;

    public BuildingToolItem(final ToolType type, final ToolTier tier, final Item.Properties properties) {
        super(tier == ToolTier.NETHERITE ? properties.fireResistant() : properties);
        this.type = type;
        this.tier = tier;
    }

    public ToolType type() { return type; }

    public ToolTier tier() { return tier; }

    /** Shorthand for {@code tier().level()} (1..4). */
    public int level() { return tier.level(); }

    @Override
    public boolean isValidRepairItem(final ItemStack stack, final ItemStack repair) {
        return tier.isRepairMaterial(repair);
    }

    @Override
    public int getEnchantmentValue() {
        return tier.enchantability();
    }

    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final List<Component> lines, final TooltipFlag flag) {
        lines.add(tierLine(tier.level()));
        final List<ToolUnlocks.Unlock> unlocked = ToolUnlocks.upTo(type, tier.level());
        if (!unlocked.isEmpty()) {
            addNames(lines, Component.translatable("slate_building.tool.unlocks"), unlocked, ChatFormatting.GRAY);
        }
        boolean teased = false;
        for (int next = tier.level() + 1; next <= ToolTier.MAX && !teased; next++) {
            final List<ToolUnlocks.Unlock> more = ToolUnlocks.at(type, next);
            if (more.isEmpty()) continue;
            addNames(lines, Component.translatable("slate_building.tool.next_tier", ToolTier.byLevel(next).displayName()), more, ChatFormatting.DARK_GRAY);
            teased = true;
        }
        if (tier.level() == ToolTier.MAX) {
            lines.add(Component.translatable("slate_building.tool.max_tier").withStyle(ChatFormatting.DARK_GRAY));
        } else if (!teased) {
            lines.add(Component.translatable("slate_building.tool.bigger_areas").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("slate_building.tool.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** "■■□□ Iron" in the tier colour. */
    public static MutableComponent tierLine(final int level) {
        final ToolTier t = ToolTier.byLevel(level);
        final MutableComponent pips = Component.empty();
        for (int i = 1; i <= ToolTier.MAX; i++) {
            pips.append(Component.literal("■").withStyle(Style.EMPTY.withColor(i <= level ? TextColor.fromRgb(t.color() & 0xFFFFFF) : TextColor.fromRgb(0x4A4A4A))));
        }
        return pips.append(Component.literal(" ")).append(Component.translatable("slate_building.tool.tier", t.displayName(), level)
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(t.color() & 0xFFFFFF))));
    }

    /** Adds "label A, B, C," lines, three names per line (tooltips do not wrap). */
    public static void addNames(final List<Component> lines, final Component label, final List<ToolUnlocks.Unlock> unlocks, final ChatFormatting color) {
        final List<Component> names = new ArrayList<>();
        for (final ToolUnlocks.Unlock u : unlocks) names.add(u.name());
        for (int i = 0; i < names.size(); i += NAMES_PER_LINE) {
            final MutableComponent line = i == 0 ? label.copy().append(" ") : Component.literal("  ");
            for (int j = i; j < Math.min(names.size(), i + NAMES_PER_LINE); j++) {
                if (j > i) line.append(", ");
                line.append(names.get(j));
            }
            if (i + NAMES_PER_LINE < names.size()) line.append(",");
            lines.add(line.withStyle(color));
        }
    }
}
