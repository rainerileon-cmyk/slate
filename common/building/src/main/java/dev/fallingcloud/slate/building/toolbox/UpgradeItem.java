package dev.fallingcloud.slate.building.toolbox;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A toolbox upgrade, e.g. {@code slate_building:memory_upgrade}. Upgrades only act while inside a toolbox's upgrade
 * slots; several of one type count as levels up to {@link UpgradeType#maxLevel()} ({@code Capabilities.upgrades()}).
 */
public class UpgradeItem extends Item {

    private final UpgradeType type;

    public UpgradeItem(final UpgradeType type, final Item.Properties properties) {
        super(properties);
        this.type = type;
    }

    public UpgradeType type() { return type; }

    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final List<Component> lines, final TooltipFlag flag) {
        lines.add(type.description().copy().withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("slate_building.upgrade." + type.id() + ".effect").withStyle(ChatFormatting.BLUE));
        lines.add(type.maxLevel() > 1
            ? Component.translatable("slate_building.upgrade.stacks", type.maxLevel()).withStyle(ChatFormatting.DARK_GRAY)
            : Component.translatable("slate_building.upgrade.single").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("slate_building.upgrade.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
