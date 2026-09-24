package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.registry.BuildingMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The toolbox container menu ({@code BuildingMenus.TOOLBOX}): the tool, upgrade and pouch slots of one toolbox stack
 * ({@link ToolboxContents} layout) plus the player inventory.
 *
 * <p>Server side the toolbox slots are a {@link ToolboxInventory}, a live view on the stack's {@code CONTAINER}
 * component, so nothing is ever copied out of the toolbox while it is open; client side they are a plain container the
 * menu sync fills. The toolbox being edited is locked: its inventory slot takes no clicks, number-key or offhand swaps
 * (its index travels to the client in a data slot so the screen can mark it). The menu closes if that stack leaves its
 * slot. Works the same for a toolbox in BetterInventory's toolbox slot ({@link ToolboxAccess#SLOT_BETTER_INVENTORY}).
 *
 * <p>Shift-click: toolbox → inventory; tools → their slot (a different tool of that type already there is swapped
 * out); upgrades → a free upgrade slot; blocks → the pouch; anything else between main inventory and hotbar.
 */
public class ToolboxMenu extends AbstractContainerMenu {

    // Layout (menu = screen coordinates of each slot's 18x18 frame; the item sits 1 px inside).
    public static final int MAIN_W = 176;
    public static final int HEIGHT = 212;
    public static final int TOOL_X = 14, TOOL_DX = 26, TOOL_Y = 20;
    public static final int UPGRADE_X = 8, UPGRADE_Y = 60;
    public static final int POUCH_X = 8, POUCH_Y = 96;
    public static final int INV_X = 8, INV_Y = 130, HOTBAR_Y = 188;

    public static final int TOOLBOX_START = 0;
    public static final int TOOLBOX_END = ToolboxContents.SIZE;          // exclusive
    public static final int INV_START = TOOLBOX_END;
    public static final int HOTBAR_START = INV_START + 27;
    public static final int INV_END = HOTBAR_START + 9;                   // exclusive

    /** Data slot value when no inventory slot holds the edited toolbox (BetterInventory slot / client default). */
    public static final int NOT_LOCKED = -1;

    private final Container toolbox;
    private final Player player;
    private final DataSlot lockedSlot = DataSlot.standalone();
    private final int hostSlot;
    private @Nullable ItemStack bound;

    /** Client factory used by the {@code MenuType}. */
    public ToolboxMenu(final int containerId, final Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(ToolboxContents.SIZE), Integer.MIN_VALUE, null);
    }

    private ToolboxMenu(final int containerId, final Inventory inv, final Container toolbox, final int hostSlot, final @Nullable ItemStack bound) {
        super(BuildingMenus.TOOLBOX.get(), containerId);
        this.toolbox = toolbox;
        this.player = inv.player;
        this.hostSlot = hostSlot;
        this.bound = bound;
        this.lockedSlot.set(hostSlot >= 0 ? hostSlot : NOT_LOCKED);
        checkContainerSize(toolbox, ToolboxContents.SIZE);

        for (int i = 0; i < ToolboxContents.TOOLS; i++) {
            addSlot(new ToolboxSlot(toolbox, i, TOOL_X + i * TOOL_DX + 1, TOOL_Y + 1));
        }
        for (int i = 0; i < ToolboxContents.UPGRADES; i++) {
            addSlot(new ToolboxSlot(toolbox, ToolboxContents.FIRST_UPGRADE + i, UPGRADE_X + i * 18 + 1, UPGRADE_Y + 1));
        }
        for (int i = 0; i < ToolboxContents.POUCH; i++) {
            addSlot(new ToolboxSlot(toolbox, ToolboxContents.FIRST_POUCH + i, POUCH_X + i * 18 + 1, POUCH_Y + 1));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(inv, 9 + row * 9 + col, INV_X + col * 18 + 1, INV_Y + row * 18 + 1));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new PlayerSlot(inv, col, INV_X + col * 18 + 1, HOTBAR_Y + 1));
        addDataSlot(lockedSlot);
    }

    /**
     * Opens the toolbox at {@code slot} (inventory index, 40 = offhand, {@link ToolboxAccess#SLOT_BETTER_INVENTORY})
     * for {@code player}; nothing when that slot holds no toolbox.
     */
    public static void open(final ServerPlayer player, final int slot) {
        final ItemStack stack = ToolboxAccess.stackAt(player, slot);
        if (!ToolboxAccess.isToolbox(stack)) return;
        final ToolboxAccess.Located at = new ToolboxAccess.Located(stack, slot);
        final ToolboxInventory view = ToolboxAccess.inventory(player, at);
        BuildingPlatform.get().openMenu(player, new SimpleMenuProvider(
            (id, inv, p) -> new ToolboxMenu(id, inv, view, slot, stack), stack.getHoverName()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.6f, 1.15f);
    }

    /** The toolbox's slots (19, {@link ToolboxContents} layout). */
    public Container toolbox() {
        return toolbox;
    }

    /** Inventory index of the edited toolbox (locked), or {@link #NOT_LOCKED}. Synced to the client. */
    public int lockedSlot() {
        return lockedSlot.get();
    }

    /** Whether menu slot {@code s} is the edited toolbox's own inventory slot. */
    public boolean isLocked(final Slot s) {
        return s instanceof PlayerSlot ps && ps.getContainerSlot() == lockedSlot.get();
    }

    @Override
    public void clicked(final int slotId, final int button, final ClickType type, final Player p) {
        final int locked = lockedSlot.get();
        if (locked != NOT_LOCKED) {
            if (slotId >= 0 && slotId < slots.size() && isLocked(slots.get(slotId))) return;
            if (type == ClickType.SWAP && button == locked) return;   // number key / offhand key onto another slot
        }
        super.clicked(slotId, button, type, p);
    }

    @Override
    public ItemStack quickMoveStack(final Player p, final int index) {
        final Slot slot = slots.get(index);
        if (!slot.hasItem() || isLocked(slot)) return ItemStack.EMPTY;
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();

        if (index < TOOLBOX_END) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (stack.getItem() instanceof BuildingToolItem tool) {
                final Slot target = slots.get(ToolboxContents.toolSlot(tool.type()));
                if (!target.hasItem()) {
                    moved = moveItemStackTo(stack, target.index, target.index + 1, false);
                } else if (target.getItem().getItem() != stack.getItem() && slot.mayPlace(target.getItem())) {
                    // Upgrade in place: the new tool goes in, the old one takes its inventory slot.
                    final ItemStack old = target.getItem().copy();
                    target.setByPlayer(stack.copy());
                    slot.setByPlayer(old);
                    return ItemStack.EMPTY;
                }
            } else if (stack.getItem() instanceof UpgradeItem) {
                moved = moveItemStackTo(stack, ToolboxContents.FIRST_UPGRADE, ToolboxContents.FIRST_POUCH, false);
            } else if (ToolboxContents.isPouchMaterial(stack) && pouchAllowed()) {
                moved = moveItemStackTo(stack, ToolboxContents.FIRST_POUCH, TOOLBOX_END, false);
            }
            if (!moved) {
                final boolean fromMain = index < HOTBAR_START;
                if (!moveItemStackTo(stack, fromMain ? HOTBAR_START : INV_START, fromMain ? INV_END : HOTBAR_START, false)) return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(p, stack);
        return original;
    }

    @Override
    public boolean stillValid(final Player p) {
        if (bound == null) return true;   // client
        // The toolbox must still be the very stack this menu edits, where it was opened from.
        return p.isAlive() && ToolboxAccess.stackAt(p, hostSlot) == bound;
    }

    @Override
    public void broadcastChanges() {
        if (toolbox instanceof ToolboxInventory view) view.flush();
        super.broadcastChanges();
    }

    @Override
    public void removed(final Player p) {
        super.removed(p);
        if (toolbox instanceof ToolboxInventory view) view.flush();
        if (!p.level().isClientSide()) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.45f, 1.3f);
        }
    }

    private boolean pouchAllowed() {
        return BuildingServerSettings.effective(player).toolbox().allowPouch;
    }

    /** Whether the pouch accepts new items under the rules of this side (the server may disable it). */
    public boolean isPouchEnabled() {
        return pouchAllowed();
    }

    /** A toolbox slot: typed tool, upgrade (per-type cap) or pouch slot. */
    public final class ToolboxSlot extends Slot {

        ToolboxSlot(final Container container, final int index, final int x, final int y) {
            super(container, index, x, y);
        }

        public ToolboxContents.Kind kind() {
            return ToolboxContents.kind(getContainerSlot());
        }

        @Override
        public boolean mayPlace(final ItemStack stack) {
            if (kind() == ToolboxContents.Kind.POUCH && !pouchAllowed()) return false;
            final java.util.List<ItemStack> items = new java.util.ArrayList<>(ToolboxContents.SIZE);
            for (int i = 0; i < ToolboxContents.SIZE; i++) items.add(container.getItem(i));
            return ToolboxContents.mayPlace(getContainerSlot(), stack, items);
        }

        @Override
        public int getMaxStackSize(final ItemStack stack) {
            return ToolboxContents.maxStack(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return kind() == ToolboxContents.Kind.POUCH ? 99 : 1;
        }

        @Override
        public boolean isHighlightable() {
            return false;   // ToolboxScreen draws its own animated hover
        }
    }

    /** A player inventory slot that refuses to move the edited toolbox. */
    public final class PlayerSlot extends Slot {

        PlayerSlot(final Container container, final int index, final int x, final int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(final Player p) {
            return getContainerSlot() != lockedSlot.get() && super.mayPickup(p);
        }

        @Override
        public boolean mayPlace(final ItemStack stack) {
            return getContainerSlot() != lockedSlot.get() && super.mayPlace(stack);
        }

        @Override
        public boolean isHighlightable() {
            return false;
        }
    }

}
