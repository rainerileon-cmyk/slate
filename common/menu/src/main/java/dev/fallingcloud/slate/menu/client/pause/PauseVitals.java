package dev.fallingcloud.slate.menu.client.pause;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * How the player is doing, in one line, in the game's own signs: a heart, a chestplate and a drumstick from the HUD,
 * each with its count as the HUD would show it (in hearts, not in points), and the experience level. In Creative and
 * Spectator none of it means anything, and the line is empty.
 */
public final class PauseVitals {

    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_HARDCORE = ResourceLocation.withDefaultNamespace("hud/heart/hardcore_full");
    private static final ResourceLocation ARMOR = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD = ResourceLocation.withDefaultNamespace("hud/food_full");
    private static final int ICON = 9, GAP = 3, BETWEEN = 9;
    /** The green of the experience level over the hotbar. */
    private static final int LEVEL = 0xFF80FF20;

    private record Item(@Nullable ResourceLocation under, @Nullable ResourceLocation sprite, Component text, int color) {}

    private static List<Item> items() {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer p = mc.player;
        final List<Item> out = new ArrayList<>();
        if (p == null || !PauseActions.survives()) return out;
        final boolean hardcore = mc.level != null && mc.level.getLevelData().isHardcore();
        final float extra = p.getAbsorptionAmount();
        final String health = halves(p.getHealth()) + (extra > 0.01f ? " +" + halves(extra) : "");
        out.add(new Item(HEART_CONTAINER, hardcore ? HEART_HARDCORE : HEART, Fonts.heading(health), 0));
        if (p.getArmorValue() > 0) out.add(new Item(null, ARMOR, Fonts.heading(halves(p.getArmorValue())), 0));
        out.add(new Item(FOOD_EMPTY, FOOD, Fonts.heading(halves(p.getFoodData().getFoodLevel())), 0));
        if (p.experienceLevel > 0) out.add(new Item(null, null, Fonts.heading(Component.translatable("slate_menu.pause.level", p.experienceLevel).getString()), LEVEL));
        return out;
    }

    /** Points as the HUD counts them: two to a heart, the half said as ".5". */
    private static String halves(final float points) {
        final int half = Math.max(0, (int) Math.ceil(points));
        return half % 2 == 0 ? Integer.toString(half / 2) : String.format(Locale.ROOT, "%d.5", half / 2);
    }

    /** How wide the line is; 0 when there is nothing to say. */
    public static int width() {
        final Font font = SlateDraw.font();
        int w = 0;
        for (final Item it : items()) w += (w > 0 ? BETWEEN : 0) + (it.sprite() == null ? 0 : ICON + GAP) + font.width(it.text());
        return w;
    }

    /** Draws the line with its left end at {@code x} and returns its width. {@code y} is the top of the text. */
    public static int draw(final GuiGraphics g, final int x, final int y, final float alpha, final int textColor) {
        final Font font = SlateDraw.font();
        final boolean shadow = Theme.current().isVanilla();
        int cx = x;
        for (final Item it : items()) {
            if (cx > x) cx += BETWEEN;
            if (it.sprite() != null) {
                RenderSystem.enableBlend();
                g.setColor(1f, 1f, 1f, alpha);
                if (it.under() != null) g.blitSprite(it.under(), cx, y - 1, ICON, ICON);
                g.blitSprite(it.sprite(), cx, y - 1, ICON, ICON);
                g.setColor(1f, 1f, 1f, 1f);
                cx += ICON + GAP;
            }
            g.drawString(font, it.text(), cx, y, Colors.scaleAlpha(it.color() != 0 ? it.color() : textColor, alpha), shadow);
            cx += font.width(it.text());
        }
        return cx - x;
    }

    private PauseVitals() {}
}
