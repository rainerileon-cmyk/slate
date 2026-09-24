package dev.fallingcloud.slate.building.toolbox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.fallingcloud.slate.building.registry.BuildingRegistry;
import dev.fallingcloud.slate.building.registry.RegistryRef;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The container a toolbox's Supply Link upgrade pulls materials from: where it is and which block it was when linked
 * (for the tooltip, and to notice when the chest was replaced). Stored on the toolbox stack as the
 * {@code slate_building:supply_link} data component (set at runtime only, never in item properties).
 *
 * @param pos   dimension + position of the linked container
 * @param block id of the container block when it was linked
 */
public record SupplyLink(GlobalPos pos, ResourceLocation block) {

    public static final Codec<SupplyLink> CODEC = RecordCodecBuilder.create(i -> i.group(
        GlobalPos.CODEC.fieldOf("pos").forGetter(SupplyLink::pos),
        ResourceLocation.CODEC.fieldOf("block").forGetter(SupplyLink::block)
    ).apply(i, SupplyLink::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SupplyLink> STREAM_CODEC = StreamCodec.composite(
        GlobalPos.STREAM_CODEC, SupplyLink::pos,
        ResourceLocation.STREAM_CODEC, SupplyLink::block,
        SupplyLink::new);

    /** {@code slate_building:supply_link}, queued by {@link #init()} from {@code ToolboxSystem.init()}. */
    private static @Nullable RegistryRef<DataComponentType<SupplyLink>> type;

    static void init() {
        if (type != null) return;
        type = BuildingRegistry.register(Registries.DATA_COMPONENT_TYPE, "supply_link",
            () -> DataComponentType.<SupplyLink>builder().persistent(CODEC).networkSynchronized(STREAM_CODEC).build());
    }

    public static DataComponentType<SupplyLink> type() {
        if (type == null) throw new IllegalStateException("SupplyLink component used before ToolboxSystem.init()");
        return type.get();
    }

    public static @Nullable SupplyLink of(final ItemStack toolbox) {
        return type == null || !type.isBound() ? null : toolbox.get(type.get());
    }

    public static void set(final ItemStack toolbox, final @Nullable SupplyLink link) {
        if (link == null) toolbox.remove(type());
        else toolbox.set(type(), link);
    }

    /** "Chest" - the linked block's name. */
    public Component blockName() {
        return BuiltInRegistries.BLOCK.getOptional(block).map(b -> (Component) b.getName()).orElse(Component.literal(block.toString()));
    }

    /** "12, 64, -3". */
    public String coords() {
        return pos.pos().getX() + ", " + pos.pos().getY() + ", " + pos.pos().getZ();
    }
}
