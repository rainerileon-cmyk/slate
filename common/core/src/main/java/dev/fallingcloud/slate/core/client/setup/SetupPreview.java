package dev.fallingcloud.slate.core.client.setup;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The setup screen's live mocks, painted from primitives (and vanilla's own sprites, logo and item renderer) so they
 * show exactly the looks the switches produce without opening any screen: a title screen in either layout and
 * either style, and the survival inventory in either container style. Each mock is drawn in its own virtual
 * coordinate space, scaled and clipped into the given rect.
 */
final class SetupPreview {

    private static final ResourceLocation INVENTORY = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final int MENU_W = 300, MENU_H = 200;
    private static final int INV_W = 176, INV_H = 166;

    private static final LogoRenderer LOGO = new LogoRenderer(false);
    private static final ItemStack[] HOTBAR = {
        new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.STONE_BRICKS), new ItemStack(Items.DIAMOND_PICKAXE),
        new ItemStack(Items.TORCH), new ItemStack(Items.BREAD), new ItemStack(Items.OAK_STAIRS)};
    private static final ItemStack[] ROW = {new ItemStack(Items.COBBLESTONE), new ItemStack(Items.OAK_LOG), new ItemStack(Items.GLASS)};

    private SetupPreview() {}

    private static Palette dark() {
        return Palette.dark(Colors.fromHex(Slate.config().accent, Palette.DEFAULT_ACCENT));
    }

    private static Font font() {
        return SlateDraw.font();
    }

    // ------------------------------------------------------------------ menus

    /** A title screen: Slate's layout (nav column, continue card) or vanilla's (centred stack), dark or stone style. */
    static void menus(final GuiGraphics g, final Rect r, final boolean customLayout, final boolean customStyle, final float alpha) {
        if (r.w() < 20 || r.h() < 20) return;
        final float s = Math.min(r.w() / (float) MENU_W, r.h() / (float) MENU_H);
        final int vw = Math.max(MENU_W, Math.round(r.w() / s)), vh = Math.max(MENU_H, Math.round(r.h() / s));
        g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        g.pose().pushPose();
        g.pose().translate(r.x(), r.y(), 0);
        g.pose().scale(s, s, 1f);
        sky(g, vw, vh, customStyle, alpha);
        if (customLayout) slateTitle(g, vw, vh, customStyle, alpha);
        else vanillaTitle(g, vw, vh, customStyle, alpha);
        g.pose().popPose();
        g.disableScissor();
    }

    /** A panorama stand-in: sky over distant hills, veiled and vignetted the way the dark skin treats the real one. */
    private static void sky(final GuiGraphics g, final int vw, final int vh, final boolean customStyle, final float alpha) {
        SlateDraw.vgradient(g, 0, 0, vw, vh, Colors.scaleAlpha(0xFF5A8FCF, alpha), Colors.scaleAlpha(0xFF9FC4E8, alpha));
        final int horizon = vh * 3 / 5;
        SlateDraw.vgradient(g, 0, horizon, vw, vh - horizon, Colors.scaleAlpha(0xFF4E7F45, alpha), Colors.scaleAlpha(0xFF2E4D2A, alpha));
        SlateDraw.pixelCircle(g, vw * 3 / 4, vh / 4, 9, Colors.scaleAlpha(0xFFFFF2C2, alpha));
        for (int i = 0; i < 6; i++) {
            final int hx = i * vw / 5 - 20, hh = 18 + (i * 7) % 16;
            SlateDraw.pixelRound(g, hx, horizon - hh / 2, vw / 4, hh, Colors.scaleAlpha(0xFF3F6C3A, alpha), 6);
        }
        if (customStyle) {
            SlateDraw.rect(g, 0, 0, vw, vh, Colors.scaleAlpha(Colors.withAlpha(0x0E0E0D, 0x70), alpha));
            SlateDraw.vignette(g, 0, 0, vw, vh, 0.5f * alpha);
        }
    }

    private static void slateTitle(final GuiGraphics g, final int vw, final int vh, final boolean customStyle, final float alpha) {
        final Palette p = dark();
        final Font f = font();
        LOGO.renderLogo(g, vw, alpha, 10);
        final int navX = 16, navW = 104, top = 72, rowH = 22, gap = 4;
        final String[] labels = {"Singleplayer", "Multiplayer", "Screenshots", "Options", "Quit"};
        final Icon[] icons = {Icon.SINGLEPLAYER, Icon.MULTIPLAYER, Icon.CAMERA, Icon.SETTINGS, Icon.QUIT};
        // The nav column rests on a translucent plate, the way the custom layout groups it.
        if (customStyle) {
            SlateDraw.pixelRound(g, navX - 6, top - 6, navW + 12, labels.length * (rowH + gap) - gap + 12, Colors.scaleAlpha(Colors.withAlpha(p.bg(), 0x8C), alpha), 3);
        }
        for (int i = 0; i < labels.length; i++) {
            final int y = top + i * (rowH + gap);
            final boolean danger = i == labels.length - 1;
            final boolean hot = i == 0;
            if (customStyle) {
                final int fill = hot ? Colors.withAlpha(p.surfaceHover(), 0xF0) : Colors.withAlpha(p.surface(), 0xB4);
                SlateDraw.pixelRound(g, navX, y, navW, rowH, Colors.scaleAlpha(fill, alpha), 3);
                SlateDraw.outline(g, navX, y, navW, rowH, Colors.scaleAlpha(hot ? p.borderStrong() : Colors.withAlpha(p.border(), 0xB0), alpha), 3);
                if (hot) SlateDraw.rect(g, navX + 2, y + 5, 2, rowH - 10, Colors.scaleAlpha(p.accent(), alpha));
                final int ic = danger ? p.danger() : hot ? p.accent() : p.textMuted();
                Icons.draw(g, icons[i], navX + 8, y + (rowH - 12) / 2, 12, Colors.scaleAlpha(ic, alpha));
                g.drawString(f, labels[i], navX + 26, y + (rowH - 8) / 2, Colors.scaleAlpha(danger ? p.danger() : p.text(), alpha), false);
            } else {
                SlateDraw.vanillaButton(g, navX, y, navW, rowH, hot ? 1f : 0f, true, alpha);
                final int fg = danger ? 0xFFFF6A6A : 0xFFFFFFFF;
                Icons.draw(g, icons[i], navX + 8, y + (rowH - 12) / 2, 12, Colors.scaleAlpha(fg, alpha));
                g.drawString(f, labels[i], navX + 26, y + (rowH - 8) / 2, Colors.scaleAlpha(fg, alpha), true);
            }
        }
        // Continue card + account card on the right.
        final int cx = navX + navW + 14, cw = vw - cx - 16;
        card(g, cx, top, cw, 54, customStyle, alpha, p);
        g.drawString(f, "CONTINUE", cx + 8, top + 6, Colors.scaleAlpha(customStyle ? p.textDim() : 0xFFA0A0A0, alpha), !customStyle);
        SlateDraw.pixelRound(g, cx + 8, top + 18, 28, 28, Colors.scaleAlpha(customStyle ? p.bg2() : 0x80000000, alpha), 3);
        Icons.draw(g, Icon.WORLD, cx + 14, top + 24, 16, Colors.scaleAlpha(customStyle ? p.textMuted() : 0xFFC0C0C0, alpha));
        g.drawString(f, "My World", cx + 42, top + 19, Colors.scaleAlpha(customStyle ? p.text() : 0xFFFFFFFF, alpha), !customStyle);
        g.drawString(f, "Singleplayer · last played 2h ago", cx + 42, top + 31, Colors.scaleAlpha(customStyle ? p.textMuted() : 0xFFC0C0C0, alpha), !customStyle);
        SlateDraw.chip(g, Component.literal("Survival"), cx + 42, top + 40, p.accent(), alpha);
        final int playX = cx + cw - 26, playY = top + 54 - 26;
        SlateDraw.pixelRound(g, playX, playY, 18, 18, Colors.scaleAlpha(p.accent(), alpha), 3);
        Icons.draw(g, Icon.PLAY, playX + 3, playY + 3, 12, Colors.scaleAlpha(p.accentText(), alpha));
        final int ay = top + 54 + 8;
        card(g, cx, ay, cw, 30, customStyle, alpha, p);
        SlateDraw.pixelRound(g, cx + 8, ay + 7, 16, 16, Colors.scaleAlpha(0xFF6B8E5A, alpha), 2);
        SlateDraw.pixelRound(g, cx + 11, ay + 10, 10, 10, Colors.scaleAlpha(0xFFC49A6C, alpha), 1);
        g.drawString(f, "Steve", cx + 32, ay + 6, Colors.scaleAlpha(customStyle ? p.text() : 0xFFFFFFFF, alpha), !customStyle);
        g.drawString(f, "Online · Microsoft account", cx + 32, ay + 17, Colors.scaleAlpha(customStyle ? p.textMuted() : 0xFFC0C0C0, alpha), !customStyle);
        // Footer.
        g.drawString(f, "Minecraft 1.21.1 · 142 mods · Slate", navX, vh - 12, Colors.scaleAlpha(customStyle ? p.textMuted() : 0xFFFFFFFF, alpha), !customStyle);
    }

    private static void card(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean customStyle, final float alpha, final Palette p) {
        if (customStyle) {
            SlateDraw.pixelRound(g, x + 2, y + h - 3, w, 5, Colors.scaleAlpha(p.shadow(), alpha), 3);
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(p.surface(), alpha), 3);
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(p.border(), alpha), 3);
        } else {
            SlateDraw.rect(g, x, y, w, h, Colors.scaleAlpha(0x60000000, alpha));
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(0xFF404040, alpha), 0);
        }
    }

    private static void vanillaTitle(final GuiGraphics g, final int vw, final int vh, final boolean customStyle, final float alpha) {
        final Palette p = dark();
        final Font f = font();
        LOGO.renderLogo(g, vw, alpha, 12);
        final int j = vh / 4 + 48, bw = 200, bx = (vw - bw) / 2;
        final String[] labels = {"Singleplayer", "Multiplayer", "Minecraft Realms"};
        for (int i = 0; i < labels.length; i++) button(g, bx, j + i * 24, bw, 20, labels[i], customStyle, i == 0, alpha, p);
        button(g, bx, j + 72, 98, 20, "Options...", customStyle, false, alpha, p);
        button(g, bx + 102, j + 72, 98, 20, "Quit Game", customStyle, false, alpha, p);
        square(g, bx - 24, j + 72, Icon.LANGUAGE, customStyle, alpha, p);
        square(g, bx + bw + 4, j + 72, Icon.ACCESSIBILITY, customStyle, alpha, p);
        // The Slate button the vanilla layout gets, so Slate stays one click away.
        square(g, bx + bw + 28, j + 72, Icon.SLATE, customStyle, alpha, p);
        g.drawString(f, "Minecraft 1.21.1", 2, vh - 10, Colors.scaleAlpha(0xFFFFFFFF, alpha), true);
    }

    private static void button(final GuiGraphics g, final int x, final int y, final int w, final int h, final String label, final boolean customStyle,
                               final boolean hot, final float alpha, final Palette p) {
        if (customStyle) {
            final int fill = hot ? p.surfaceHover() : p.surface();
            SlateDraw.pixelRound(g, x + 2, y + h - 3, w, 5, Colors.scaleAlpha(p.shadow(), 0.35f * alpha), 3);
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(fill, alpha), 3);
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(hot ? p.borderStrong() : p.border(), alpha), 3);
            SlateDraw.textCentered(g, Component.literal(label), x + w / 2, SlateDraw.textY(y, h), Colors.scaleAlpha(p.text(), alpha));
        } else {
            SlateDraw.vanillaButton(g, x, y, w, h, hot ? 1f : 0f, true, alpha);
            SlateDraw.textCentered(g, Component.literal(label), x + w / 2, SlateDraw.textY(y, h), Colors.scaleAlpha(0xFFFFFFFF, alpha), true);
        }
    }

    private static void square(final GuiGraphics g, final int x, final int y, final Icon icon, final boolean customStyle, final float alpha, final Palette p) {
        if (customStyle) {
            SlateDraw.pixelRound(g, x, y, 20, 20, Colors.scaleAlpha(p.surface(), alpha), 3);
            SlateDraw.outline(g, x, y, 20, 20, Colors.scaleAlpha(p.border(), alpha), 3);
            Icons.draw(g, icon, x + 4, y + 4, 12, Colors.scaleAlpha(icon == Icon.SLATE ? p.accent() : p.textMuted(), alpha));
        } else {
            SlateDraw.vanillaButton(g, x, y, 20, 20, 0f, true, alpha);
            Icons.draw(g, icon, x + 4, y + 4, 12, Colors.scaleAlpha(0xFFFFFFFF, alpha));
        }
    }

    // ------------------------------------------------------------------ containers

    /** The survival inventory: vanilla's texture, or Slate's panel with slot wells, an accent hover ring and light labels. */
    static void container(final GuiGraphics g, final Rect r, final boolean custom, final float alpha) {
        if (r.w() < 20 || r.h() < 20) return;
        final float s = Math.min(r.w() / (float) INV_W, r.h() / (float) INV_H);
        final int dw = Math.round(INV_W * s), dh = Math.round(INV_H * s);
        final int ox = r.x() + (r.w() - dw) / 2, oy = r.y() + (r.h() - dh) / 2;
        g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(s, s, 1f);
        final Font f = font();
        if (custom) {
            ReskinDraw.containerPanel(g, 0, 0, INV_W, INV_H);
            for (int i = 0; i < 4; i++) ReskinDraw.containerSlot(g, 7, 7 + i * 18);              // armour
            ReskinDraw.containerSlot(g, 76, 61);                                                    // off hand
            for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) ReskinDraw.containerSlot(g, 97 + col * 18, 17 + row * 18);
            ReskinDraw.containerSlot(g, 153, 27);                                                   // result
            for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) ReskinDraw.containerSlot(g, 7 + col * 18, 83 + row * 18);
            for (int col = 0; col < 9; col++) ReskinDraw.containerSlot(g, 7 + col * 18, 141);
            ReskinDraw.containerInset(g, 25, 7, 51, 72);
            ReskinDraw.containerArrow(g, 133, 30);
            g.drawString(f, "Crafting", 97, 8, dark().text(), false);
        } else {
            RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, alpha);
            g.blit(INVENTORY, 0, 0, 0, 0, INV_W, INV_H, 256, 256);
            g.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
            g.drawString(f, "Crafting", 97, 8, 0xFF404040, false);
        }
        // A silhouette where the player model stands.
        final int body = custom ? Colors.withAlpha(dark().textDim(), 0x50) : 0x30000000;
        SlateDraw.pixelRound(g, 44, 14, 12, 12, body, 2);
        SlateDraw.pixelRound(g, 42, 28, 16, 24, body, 2);
        SlateDraw.rect(g, 44, 54, 5, 20, body);
        SlateDraw.rect(g, 51, 54, 5, 20, body);
        // A few items, and the hovered slot.
        for (int i = 0; i < HOTBAR.length; i++) g.renderItem(HOTBAR[i], 8 + i * 18, 142);
        for (int i = 0; i < ROW.length; i++) g.renderItem(ROW[i], 8 + i * 18, 84);
        g.renderItem(new ItemStack(Items.IRON_CHESTPLATE), 8, 26);
        g.renderItem(new ItemStack(Items.SHIELD), 77, 62);
        if (custom) ReskinDraw.containerSlotHighlight(g, 44, 142, 0);
        else SlateDraw.rect(g, 44, 142, 16, 16, Colors.scaleAlpha(0x80FFFFFF, alpha));
        g.pose().popPose();
        g.disableScissor();
    }
}
