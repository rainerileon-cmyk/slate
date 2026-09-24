package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.registry.BuildingItems;
import dev.fallingcloud.slate.building.registry.BuildingTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * In-game self-test of the toolbox logic, run by the dev harness scenario {@code toolbox} (registries and datapack
 * content cannot be bootstrapped in a unit test). Every check returns one {@code PASS ...} / {@code FAIL ...} line.
 * Only touches the test player's own inventory and the block positions the scenario prepared.
 */
public final class ToolboxSelfTest {

    private final List<String> out = new ArrayList<>();

    private void check(final String name, final boolean ok, final Object detail) {
        out.add((ok ? "PASS " : "FAIL ") + name + (ok ? "" : " (" + detail + ")"));
    }

    private static ItemStack tool(final ToolType t, final ToolTier tier) {
        return new ItemStack(BuildingItems.tool(t, tier).get());
    }

    private static ItemStack upgrade(final UpgradeType u) {
        return new ItemStack(BuildingItems.upgrade(u).get());
    }

    /** Pure capability rules: tiers, locks, limits with upgrades, slot rules. */
    public static List<String> capabilities(final BuildingServerSettings settings) {
        final ToolboxSelfTest t = new ToolboxSelfTest();
        final ServerOps ops = settings.ops();
        final NonNullList<ItemStack> items = NonNullList.withSize(ToolboxContents.SIZE, ItemStack.EMPTY);

        t.check("empty toolbox unlocks nothing", ToolboxAccess.Capabilities.of(items).tiers().isEmpty()
            && !ToolboxAccess.Capabilities.of(items).unlocked(BuildModes.FILL), ToolboxAccess.Capabilities.of(items));

        items.set(ToolboxContents.toolSlot(ToolType.TROWEL), tool(ToolType.TROWEL, ToolTier.IRON));
        items.set(ToolboxContents.toolSlot(ToolType.HAMMER), tool(ToolType.HAMMER, ToolTier.COPPER));
        ToolboxAccess.Capabilities caps = ToolboxAccess.Capabilities.of(items);
        t.check("iron trowel = tier 2", caps.tier(ToolType.TROWEL) == 2, caps.tiers());
        t.check("iron trowel unlocks hollow", caps.unlocked(BuildModes.HOLLOW_BOX), caps);
        t.check("iron trowel does not unlock sphere", !caps.unlocked(BuildModes.SPHERE), caps);
        final String reason = String.valueOf(caps.lockReason(BuildModes.SPHERE) == null ? null : caps.lockReason(BuildModes.SPHERE).getString());
        t.check("sphere lock reason names diamond trowel + current tier", reason.contains("Diamond") && reason.contains("Iron"), reason);
        t.check("copper hammer unlocks clear, not reshape", caps.unlocked(BuildModes.CLEAR) && !caps.unlocked(BuildModes.RESHAPE), caps);
        t.check("measure needs no tool", ToolboxAccess.Capabilities.NONE.unlocked(BuildModes.MEASURE), "locked");

        Limits l = caps.limits(settings);
        t.check("tier-2 limits", l.maxVolume() == ToolTier.index(ops.maxVolume, 2) && l.maxSpan() == ToolTier.index(ops.maxSpan, 2)
            && l.blocksPerTick() == ToolTier.index(ops.blocksPerTick, 2) && l.undoDepth() == ops.undoDepth, l);

        items.set(ToolboxContents.FIRST_UPGRADE, upgrade(UpgradeType.CAPACITY));
        items.set(ToolboxContents.FIRST_UPGRADE + 1, upgrade(UpgradeType.CAPACITY));
        items.set(ToolboxContents.FIRST_UPGRADE + 2, upgrade(UpgradeType.REACH));
        items.set(ToolboxContents.FIRST_UPGRADE + 3, upgrade(UpgradeType.SPEED));
        caps = ToolboxAccess.Capabilities.of(items);
        l = caps.limits(settings);
        final long vol = Math.min((long) ToolTier.index(ops.maxVolume, 2) << 2, Math.max(ToolTier.index(ops.maxVolume, 2), ops.creativeMaxVolume));
        t.check("capacity x2 x2", l.maxVolume() == vol && l.maxSpan() == ToolTier.index(ops.maxSpan, 2) * 2, l);
        t.check("reach x2 (floor 8)", l.reachBonus() == Math.max(ToolTier.index(ops.reachBonus, 2) << 1, 8), l);
        t.check("speed x2", l.blocksPerTick() == ToolTier.index(ops.blocksPerTick, 2) << 1, l);
        t.check("third capacity refused", !ToolboxContents.mayPlace(ToolboxContents.FIRST_UPGRADE + 3, upgrade(UpgradeType.CAPACITY), items), items);

        final NonNullList<ItemStack> copper = NonNullList.withSize(ToolboxContents.SIZE, ItemStack.EMPTY);
        copper.set(0, tool(ToolType.TROWEL, ToolTier.COPPER));
        copper.set(ToolboxContents.FIRST_UPGRADE, upgrade(UpgradeType.REACH));
        t.check("reach floor at tier 1", ToolboxAccess.Capabilities.of(copper).limits(settings).reachBonus()
            == Math.max(ToolTier.index(ops.reachBonus, 1) << 1, ToolboxAccess.REACH_PER_LEVEL), ToolboxAccess.Capabilities.of(copper).limits(settings));
        copper.set(ToolboxContents.FIRST_UPGRADE + 1, upgrade(UpgradeType.MEMORY));
        t.check("memory adds undo", ToolboxAccess.Capabilities.of(copper).limits(settings).undoDepth() == ops.undoDepth + ops.undoPerMemory, copper);

        t.check("tool slots are typed", !ToolboxContents.mayPlace(ToolboxContents.toolSlot(ToolType.TROWEL), tool(ToolType.HAMMER, ToolTier.IRON), items)
            && ToolboxContents.mayPlace(ToolboxContents.toolSlot(ToolType.HAMMER), tool(ToolType.HAMMER, ToolTier.IRON), items), "typed");
        t.check("pouch takes blocks, not shulker boxes or tools",
            ToolboxContents.isPouchMaterial(new ItemStack(Items.STONE_BRICKS)) && !ToolboxContents.isPouchMaterial(new ItemStack(Items.SHULKER_BOX))
                && !ToolboxContents.isPouchMaterial(tool(ToolType.CHISEL, ToolTier.IRON)) && !ToolboxContents.isPouchMaterial(new ItemStack(Items.DIAMOND)), "pouch");
        t.check("unlock tables", ToolUnlocks.of(ToolType.TROWEL).size() == 8 && ToolUnlocks.of(ToolType.CHISEL).size() == 2
            && ToolUnlocks.upTo(ToolType.BLUEPRINT, 2).size() == 3, ToolUnlocks.of(ToolType.BLUEPRINT));
        return t.out;
    }

    /**
     * Server side, on the test player: which toolbox is used, tool wear (Efficiency halves it), the pouch refs (set and
     * in-place writes) and the Supply Link (in range / out of range). {@code chest} must be a chest the scenario placed.
     */
    public static List<String> server(final ServerPlayer p, final BlockPos chest) {
        final ToolboxSelfTest t = new ToolboxSelfTest();
        // Data: recipes, tags, repair, enchanting, fire resistance.
        final var recipes = p.server.getRecipeManager();
        t.check("recipes loaded", recipes.byKey(SlateBuilding.id("toolbox")).isPresent() && recipes.byKey(SlateBuilding.id("iron_trowel")).isPresent()
            && recipes.byKey(SlateBuilding.id("netherite_chisel")).isPresent() && recipes.byKey(SlateBuilding.id("magnet_upgrade")).isPresent(), "missing");
        final ItemStack ironHammer = tool(ToolType.HAMMER, ToolTier.IRON);
        t.check("tags", new ItemStack(BuildingItems.TOOLBOX.get()).is(BuildingTags.TOOLBOXES) && ironHammer.is(BuildingTags.TOOLS)
            && upgrade(UpgradeType.SPEED).is(BuildingTags.UPGRADES)
            && ironHammer.is(TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("enchantable/durability"))), ironHammer.getTags().toList());
        t.check("anvil repair with the tier material", ironHammer.getItem().isValidRepairItem(ironHammer, new ItemStack(Items.IRON_INGOT))
            && !ironHammer.getItem().isValidRepairItem(ironHammer, new ItemStack(Items.DIAMOND)), "repair");
        t.check("tools are enchantable", ironHammer.isEnchantable(), "not enchantable");
        t.check("netherite tools resist fire", tool(ToolType.HAMMER, ToolTier.NETHERITE).has(DataComponents.FIRE_RESISTANT)
            && !ironHammer.has(DataComponents.FIRE_RESISTANT), "fire");

        final ToolboxAccess.Located at = ToolboxAccess.locate(p);
        t.check("finds the hotbar toolbox", at != null && at.slot() == 0, at);
        if (at == null) return t.out;

        final ItemStack spare = new ItemStack(BuildingItems.TOOLBOX.get());
        p.getInventory().offhand.set(0, spare);
        final ToolboxAccess.Located off = ToolboxAccess.locate(p);
        t.check("offhand toolbox wins over hotbar", off != null && off.slot() == Inventory.SLOT_OFFHAND && off.stack() == spare, off);
        p.getInventory().offhand.set(0, ItemStack.EMPTY);

        final ToolboxInventory inv = ToolboxAccess.inventory(p, at);
        final int trowel = ToolboxContents.toolSlot(ToolType.TROWEL);
        final int before = inv.getItem(trowel).getDamageValue();
        final int per = Math.max(1, BuildingServerSettings.effective(p).ops().durabilityPerBlocks);
        ToolboxAccess.damageTool(p, ToolType.TROWEL, per * 100);
        final int worn = ToolboxContents.read(at.stack()).get(trowel).getDamageValue();
        t.check("wear: 1 point per " + per + " blocks", worn - before == 100, worn - before);
        inv.setItem(ToolboxContents.FIRST_UPGRADE + 3, upgrade(UpgradeType.EFFICIENCY));
        ToolboxAccess.damageTool(p, ToolType.TROWEL, per * 100);
        final int worn2 = ToolboxContents.read(at.stack()).get(trowel).getDamageValue();
        t.check("efficiency halves wear", worn2 - worn == 50, worn2 - worn);
        inv.setItem(ToolboxContents.FIRST_UPGRADE + 3, ItemStack.EMPTY);

        final List<SlotRef> pouch = ToolboxAccess.pouch(p);
        t.check("pouch has 9 refs", pouch.size() == ToolboxContents.POUCH, pouch.size());
        if (pouch.size() == ToolboxContents.POUCH) {
            final ItemStack first = pouch.get(0).get();
            pouch.get(0).set(first.copyWithCount(Math.max(0, first.getCount() - 4)));
            t.check("pouch set() writes the toolbox", ToolboxContents.read(at.stack()).get(ToolboxContents.FIRST_POUCH).getCount() == first.getCount() - 4,
                ToolboxContents.read(at.stack()).get(ToolboxContents.FIRST_POUCH));
            final int count = pouch.get(1).get().getCount();
            pouch.get(1).get().shrink(2);
            ToolboxInventory.flushPending();
            t.check("pouch in-place change flushed at tick end", ToolboxContents.read(at.stack()).get(ToolboxContents.FIRST_POUCH + 1).getCount() == count - 2,
                ToolboxContents.read(at.stack()).get(ToolboxContents.FIRST_POUCH + 1));
        }

        // Two live views of one toolbox: disjoint in-place changes merge; a slot both touched never gains items.
        final ToolboxInventory a = ToolboxAccess.inventory(p, at), b = ToolboxAccess.inventory(p, at);
        final int planks = a.getItem(ToolboxContents.FIRST_POUCH).getCount();
        final ItemStack spareTool = b.getItem(ToolboxContents.toolSlot(ToolType.HAMMER)).copy();
        a.getItem(ToolboxContents.FIRST_POUCH).shrink(2);                                   // A: in place, not written
        b.setItem(ToolboxContents.toolSlot(ToolType.HAMMER), ItemStack.EMPTY);             // B: written
        a.flush();
        final List<ItemStack> merged = ToolboxContents.read(at.stack());
        t.check("views merge disjoint changes", merged.get(ToolboxContents.FIRST_POUCH).getCount() == planks - 2
            && merged.get(ToolboxContents.toolSlot(ToolType.HAMMER)).isEmpty(), merged);
        b.setItem(ToolboxContents.toolSlot(ToolType.HAMMER), spareTool);
        final ItemStack bricks = a.getItem(ToolboxContents.FIRST_POUCH + 1).copy();
        a.getItem(ToolboxContents.FIRST_POUCH + 1).shrink(1);                               // A: takes one, in place
        final ItemStack taken = b.removeItem(ToolboxContents.FIRST_POUCH + 1, 64);          // B: takes the whole stack
        a.flush();
        t.check("a slot both changed never gains items", ToolboxContents.read(at.stack()).get(ToolboxContents.FIRST_POUCH + 1).isEmpty()
            && taken.getCount() == bricks.getCount(), ToolboxContents.read(at.stack()).get(ToolboxContents.FIRST_POUCH + 1));
        b.setItem(ToolboxContents.FIRST_POUCH + 1, bricks.copyWithCount(bricks.getCount() - 1));

        final Container chestInv = ToolboxAccess.linkableContainer(p.serverLevel(), chest);
        t.check("chest is linkable", chestInv != null, p.serverLevel().getBlockState(chest));
        t.check("furnace-like blocks are not", ToolboxAccess.linkableContainer(p.serverLevel(), chest.above()) == null, p.serverLevel().getBlockState(chest.above()));
        SupplyLink.set(at.stack(), new SupplyLink(GlobalPos.of(p.serverLevel().dimension(), chest), BuiltInRegistries.BLOCK.getKey(Blocks.CHEST)));
        t.check("link inactive without the upgrade", ToolboxAccess.supplyLink(p) == null, "active");
        inv.setItem(ToolboxContents.FIRST_UPGRADE + 3, upgrade(UpgradeType.SUPPLY_LINK));
        final LinkedContainer linked = ToolboxAccess.supplyLink(p);
        t.check("supply link resolves", linked != null && linked.container().getContainerSize() >= 27 && linked.slots().size() == linked.container().getContainerSize(), linked);
        final int range = BuildingServerSettings.effective(p).toolbox().supplyLinkRange;
        BuildingServerSettings.effective(p).toolbox().supplyLinkRange = 1;
        t.check("supply link out of range", ToolboxAccess.supplyLink(p) == null, "resolved");
        BuildingServerSettings.effective(p).toolbox().supplyLinkRange = range;
        return t.out;
    }

    /**
     * With the toolbox menu open on the server: shift-click routing (tool to its slot, upgrade, blocks to the pouch,
     * tool upgrade swap) and the lock on the open toolbox's own slot.
     */
    public static List<String> menu(final ServerPlayer p) {
        final ToolboxSelfTest t = new ToolboxSelfTest();
        if (!(p.containerMenu instanceof ToolboxMenu m)) {
            t.check("toolbox menu open", false, p.containerMenu);
            return t.out;
        }
        final Inventory inv = p.getInventory();
        final int hotbar = ToolboxMenu.HOTBAR_START;

        m.clicked(hotbar + 1, 0, ClickType.QUICK_MOVE, p);   // iron brush
        t.check("shift-click tool → its slot", m.toolbox().getItem(ToolboxContents.toolSlot(ToolType.BRUSH)).is(BuildingItems.tool(ToolType.BRUSH, ToolTier.IRON).get())
            && inv.getItem(1).isEmpty(), m.toolbox().getItem(ToolboxContents.toolSlot(ToolType.BRUSH)));
        m.clicked(hotbar + 2, 0, ClickType.QUICK_MOVE, p);   // speed upgrade
        t.check("shift-click upgrade → upgrade slot", ToolboxContents.installed(itemsOf(m), UpgradeType.SPEED, -1) == 1, itemsOf(m));
        final int bricksBefore = countPouch(m, Items.STONE_BRICKS);
        m.clicked(hotbar + 3, 0, ClickType.QUICK_MOVE, p);   // 64 stone bricks
        t.check("shift-click blocks → pouch", countPouch(m, Items.STONE_BRICKS) == bricksBefore + 64 && inv.getItem(3).isEmpty(), countPouch(m, Items.STONE_BRICKS));
        m.clicked(ToolboxMenu.INV_START, 0, ClickType.QUICK_MOVE, p);   // copper square in inventory.0
        t.check("shift-click from main inventory", !m.toolbox().getItem(ToolboxContents.toolSlot(ToolType.SQUARE)).isEmpty(), itemsOf(m));

        inv.setItem(4, tool(ToolType.HAMMER, ToolTier.IRON));
        m.clicked(hotbar + 4, 0, ClickType.QUICK_MOVE, p);
        t.check("better tool swaps in, old one comes out", m.toolbox().getItem(ToolboxContents.toolSlot(ToolType.HAMMER)).is(BuildingItems.tool(ToolType.HAMMER, ToolTier.IRON).get())
            && inv.getItem(4).is(BuildingItems.tool(ToolType.HAMMER, ToolTier.COPPER).get()), inv.getItem(4));

        final ItemStack box = inv.getItem(0);
        m.clicked(hotbar, 0, ClickType.PICKUP, p);
        m.clicked(hotbar, 0, ClickType.QUICK_MOVE, p);
        m.clicked(ToolboxContents.FIRST_POUCH + 8, 0, ClickType.SWAP, p);   // number key 1 onto a pouch slot
        m.clicked(hotbar, 0, ClickType.THROW, p);
        t.check("open toolbox stays put", inv.getItem(0) == box && m.getCarried().isEmpty(), inv.getItem(0));
        t.check("menu still valid", m.stillValid(p), "closed");
        m.broadcastChanges();
        return t.out;
    }

    private static List<ItemStack> itemsOf(final ToolboxMenu m) {
        final List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < ToolboxContents.SIZE; i++) items.add(m.toolbox().getItem(i));
        return items;
    }

    private static int countPouch(final ToolboxMenu m, final net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = ToolboxContents.FIRST_POUCH; i < ToolboxContents.SIZE; i++) {
            final ItemStack s = m.toolbox().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private ToolboxSelfTest() {}
}
