package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Builder's Toolbox ({@code slate_building:toolbox}): 6 tool slots (one per {@code ToolType}), 4 upgrade slots
 * and 9 pouch slots in vanilla {@code DataComponents.CONTAINER} ({@link ToolboxContents}).
 * <ul>
 *   <li>Use: opens the {@link ToolboxMenu}.</li>
 *   <li>Sneak-use on a storage block with a Supply Link upgrade installed: links it (again: unlinks).</li>
 *   <li>In an inventory, right-click a tool, upgrade or block onto the toolbox (or the carried toolbox onto one) to
 *       put it in, like a bundle.</li>
 *   <li>Tooltip: a picture of the tools (tier meters), upgrades and pouch; the link.</li>
 *   <li>Destroyed as an item entity (lava, cactus): drops what it held, like a shulker box.</li>
 * </ul>
 */
public class ToolboxItem extends Item {

    public ToolboxItem(final Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            ToolboxMenu.open(sp, hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(final UseOnContext ctx) {
        final Player player = ctx.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) return InteractionResult.PASS;
        final Level level = ctx.getLevel();
        final BlockPos pos = ctx.getClickedPos();
        if (ToolboxAccess.linkableContainer(level, pos) == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        final ItemStack stack = ctx.getItemInHand();
        if (ToolboxContents.installed(ToolboxContents.read(stack), UpgradeType.SUPPLY_LINK, -1) == 0) {
            player.displayClientMessage(Component.translatable("slate_building.toolbox.link.needs_upgrade").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.CONSUME;
        }
        final GlobalPos here = GlobalPos.of(level.dimension(), pos.immutable());
        final SupplyLink old = SupplyLink.of(stack);
        if (old != null && old.pos().equals(here)) {
            SupplyLink.set(stack, null);
            player.displayClientMessage(Component.translatable("slate_building.toolbox.link.removed"), true);
            level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.7f, 1.2f);
        } else {
            final SupplyLink link = new SupplyLink(here, BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
            SupplyLink.set(stack, link);
            player.displayClientMessage(Component.translatable("slate_building.toolbox.link.set", link.blockName(), link.coords())
                .withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(final ItemStack stack) {
        return Optional.of(new ToolboxTooltip(ToolboxContents.read(stack)));
    }

    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final List<Component> lines, final TooltipFlag flag) {
        final List<ItemStack> items = ToolboxContents.read(stack);
        final ToolboxAccess.Capabilities caps = ToolboxAccess.Capabilities.of(items);
        // Text summary (screen readers, search, and when a tooltip picture is not shown).
        lines.add(Component.translatable("slate_building.toolbox.summary", caps.toolCount(), ToolboxContents.TOOLS,
            ToolboxContents.upgrades(items).values().stream().mapToInt(Integer::intValue).sum(),
            ToolboxContents.pouchUsed(items), ToolboxContents.POUCH).withStyle(ChatFormatting.GRAY));
        final SupplyLink link = SupplyLink.of(stack);
        final boolean linkUpgrade = caps.upgrade(UpgradeType.SUPPLY_LINK) > 0;
        if (link != null) {
            final String dim = link.pos().dimension().location().getPath();
            final Component where = link.pos().dimension() == Level.OVERWORLD ? Component.literal(link.coords())
                : Component.translatable("slate_building.toolbox.link.where_dim", link.coords(), dim);
            lines.add(Component.translatable("slate_building.toolbox.link.tooltip", link.blockName(), where)
                .withStyle(linkUpgrade ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
            if (!linkUpgrade) lines.add(Component.translatable("slate_building.toolbox.link.inactive").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("slate_building.toolbox.hint.open").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (linkUpgrade) lines.add(Component.translatable("slate_building.toolbox.hint.link").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** Right-click a stack onto the toolbox in an inventory: tools, upgrades and blocks go in. */
    @Override
    public boolean overrideOtherStackedOnMe(final ItemStack toolbox, final ItemStack other, final Slot slot, final ClickAction action,
                                            final Player player, final SlotAccess carried) {
        if (action != ClickAction.SECONDARY || other.isEmpty() || !slot.allowModification(player)) return false;
        final List<ItemStack> items = ToolboxContents.read(toolbox);
        final int moved = ToolboxContents.insert(items, other, pouchAllowed(player));
        if (moved <= 0) return false;
        ToolboxContents.write(toolbox, items);
        playInsert(player);
        return true;
    }

    /** Right-click the carried toolbox onto a stack in an inventory: that stack goes in (as much as fits). */
    @Override
    public boolean overrideStackedOnOther(final ItemStack toolbox, final Slot slot, final ClickAction action, final Player player) {
        if (action != ClickAction.SECONDARY) return false;
        final ItemStack target = slot.getItem();
        if (target.isEmpty() || ToolboxAccess.isToolbox(target)) return false;
        final boolean pouch = pouchAllowed(player);
        final List<ItemStack> items = ToolboxContents.read(toolbox);
        final List<ItemStack> probe = new ArrayList<>();
        for (final ItemStack s : items) probe.add(s.copy());
        final int fits = ToolboxContents.insert(probe, target.copy(), pouch);
        if (fits <= 0) return false;
        final ItemStack taken = slot.safeTake(target.getCount(), fits, player);
        if (taken.isEmpty()) return false;
        ToolboxContents.insert(items, taken, pouch);
        if (!taken.isEmpty()) slot.safeInsert(taken);   // never happens (probed), but never lose items
        ToolboxContents.write(toolbox, items);
        playInsert(player);
        return true;
    }

    @Override
    public void onDestroyed(final ItemEntity entity) {
        final ItemContainerContents contents = entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (contents != null) ItemUtils.onContainerDestroyed(entity, contents.nonEmptyItemsCopy());
    }

    private static boolean pouchAllowed(final Player player) {
        return BuildingServerSettings.effective(player).toolbox().allowPouch;
    }

    private static void playInsert(final Player player) {
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
    }
}
