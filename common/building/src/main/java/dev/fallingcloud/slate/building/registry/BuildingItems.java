package dev.fallingcloud.slate.building.registry;

import dev.fallingcloud.slate.building.item.ShapeBlockItem;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.BuildingToolItem;
import dev.fallingcloud.slate.building.toolbox.ToolTier;
import dev.fallingcloud.slate.building.toolbox.ToolboxItem;
import dev.fallingcloud.slate.building.toolbox.UpgradeItem;
import dev.fallingcloud.slate.building.toolbox.UpgradeType;
import dev.fallingcloud.slate.building.variant.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Slate Building's items:
 * <ul>
 *   <li>one {@link ShapeBlockItem} per shape block, same id as the block ({@code slate_building:stairs}, ...);</li>
 *   <li>{@code slate_building:toolbox} (the Builder's Toolbox);</li>
 *   <li>24 tools {@code slate_building:<tier>_<tool>} ({@link ToolTier} x {@link ToolType}), unstackable, with the
 *       tier's durability;</li>
 *   <li>7 upgrades {@code slate_building:<upgrade>_upgrade}.</li>
 * </ul>
 * Never put a {@code slate_building} data component into {@code Item.Properties} (component and item registration
 * order differs between loaders); set them on stacks at runtime.
 */
public final class BuildingItems {

    private static final List<RegistryRef<? extends Item>> ALL = new ArrayList<>();
    private static final Map<Shape, RegistryRef<ShapeBlockItem>> SHAPE_ITEMS = new EnumMap<>(Shape.class);
    private static final Map<ToolType, Map<ToolTier, RegistryRef<BuildingToolItem>>> TOOLS = new EnumMap<>(ToolType.class);
    private static final Map<UpgradeType, RegistryRef<UpgradeItem>> UPGRADES = new EnumMap<>(UpgradeType.class);

    static {
        for (final RegistryRef<? extends Block> block : BuildingBlocks.all()) {
            final Shape shape = shapeOf(block);
            final RegistryRef<ShapeBlockItem> item = add(BuildingRegistry.register(Registries.ITEM, block.id().getPath(),
                () -> new ShapeBlockItem(block.get(), new Item.Properties())));
            SHAPE_ITEMS.put(shape, item);
        }
    }

    public static final RegistryRef<ToolboxItem> TOOLBOX = add(BuildingRegistry.register(Registries.ITEM, "toolbox",
        () -> new ToolboxItem(new Item.Properties().stacksTo(1))));

    static {
        for (final ToolTier tier : ToolTier.values()) {
            for (final ToolType type : ToolType.values()) {
                final RegistryRef<BuildingToolItem> tool = add(BuildingRegistry.register(Registries.ITEM, tier.id() + "_" + type.id(),
                    () -> new BuildingToolItem(type, tier, new Item.Properties().stacksTo(1).durability(tier.durability()))));
                TOOLS.computeIfAbsent(type, t -> new EnumMap<>(ToolTier.class)).put(tier, tool);
            }
        }
        for (final UpgradeType type : UpgradeType.values()) {
            UPGRADES.put(type, add(BuildingRegistry.register(Registries.ITEM, type.itemPath(),
                () -> new UpgradeItem(type, new Item.Properties().stacksTo(16)))));
        }
    }

    /** The item of a shape block; null for {@link Shape#FULL}. */
    public static @Nullable RegistryRef<ShapeBlockItem> forShape(final Shape shape) {
        return SHAPE_ITEMS.get(shape);
    }

    public static RegistryRef<BuildingToolItem> tool(final ToolType type, final ToolTier tier) {
        return TOOLS.get(type).get(tier);
    }

    public static RegistryRef<UpgradeItem> upgrade(final UpgradeType type) {
        return UPGRADES.get(type);
    }

    /** Every item in registration order (shape items, toolbox, tools by tier, upgrades). */
    public static List<RegistryRef<? extends Item>> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static <T extends Item> RegistryRef<T> add(final RegistryRef<T> ref) {
        ALL.add(ref);
        return ref;
    }

    private static Shape shapeOf(final RegistryRef<? extends Block> block) {
        for (final Shape s : Shape.values()) if (BuildingBlocks.forShape(s) == block) return s;
        throw new IllegalStateException("No shape for " + block);
    }

    /** Forces class initialisation (queues the entries). Called by {@link BuildingRegistry#bootstrap()}. */
    static void init() {}

    private BuildingItems() {}
}
