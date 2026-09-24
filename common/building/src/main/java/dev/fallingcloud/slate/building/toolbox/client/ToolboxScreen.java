package dev.fallingcloud.slate.building.toolbox.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.compat.BetterInventoryBridge;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.BuildingToolItem;
import dev.fallingcloud.slate.building.toolbox.SupplyLink;
import dev.fallingcloud.slate.building.toolbox.ToolTier;
import dev.fallingcloud.slate.building.toolbox.ToolUnlocks;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.toolbox.ToolboxContents;
import dev.fallingcloud.slate.building.toolbox.ToolboxMenu;
import dev.fallingcloud.slate.building.toolbox.UpgradeType;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Builder's Toolbox screen, in both Slate skins.
 *
 * <p>Left: the toolbox - six typed tool slots with silhouettes of the missing tools and animated tier meters, four
 * upgrade slots with a readout of what each installed upgrade does, the pouch, and the inventory (the open toolbox's own
 * slot is locked and marked). Right: the unlock panel - per tool, every building mode and ability it unlocks, lit when
 * this toolbox unlocks it (new unlocks light up in a cascade), and the operation limits with upgrade-boosted values
 * highlighted. The panel scrolls, and follows the tool under the cursor (or the one just put in).
 *
 * <p>Dark skin: Slate surfaces, wells and accent. Vanilla skin: the classic light-grey container look, so it sits with
 * vanilla's own inventories. Container screens are outside Slate's reskin, so every tooltip here is vanilla's.
 */
public class ToolboxScreen extends AbstractContainerScreen<ToolboxMenu> {

    static final ResourceLocation UPGRADE_GHOST = SlateBuilding.id("textures/gui/toolbox/slot_upgrade.png");
    static final ResourceLocation POUCH_GHOST = SlateBuilding.id("textures/gui/toolbox/slot_pouch.png");
    private static final Map<ToolType, ResourceLocation> TOOL_GHOSTS = new EnumMap<>(ToolType.class);
    static {
        for (final ToolType t : ToolType.values()) TOOL_GHOSTS.put(t, SlateBuilding.id("textures/gui/toolbox/slot_" + t.id() + ".png"));
    }

    private static final int SIDE_GAP = 4, SIDE_W = 150;
    private static final int SIDE_PAD = 6, SIDE_HEADER = 20, SIDE_FOOTER = 36;
    private static final int CHIP_H = 11, CHIP_GAP = 2, ICON_COL = 20, BLOCK_GAP = 4;
    private static final int STAT_COL = 72;
    private static final int ENTER_SLIDE = 8;
    private static final int STAGGER_MS = 45, FLASH_MS = 700, FOCUS_AFTER_CHANGE_MS = 1500;
    private static final int PIP_W = 3, PIP_H = 2;
    /** Above slot items (which render at z 100 + 150) and their counts. */
    private static final float OVER_ITEMS_Z = 320f;

    /** Dev harness: a menu slot index to treat as hovered (tooltip screenshots). */
    private static @Nullable Integer debugHover;

    private final Anim open = new Anim(0f, 300, Ease.OUT_CUBIC);
    private final Map<Integer, Anim> hover = new HashMap<>();
    private final Anim[] pips = new Anim[ToolboxContents.TOOLS];
    private final Anim[] focusGlow = new Anim[ToolboxContents.TOOLS];
    private final int[] lastTier = new int[ToolboxContents.TOOLS];
    private final long[] tierChangedAt = new long[ToolboxContents.TOOLS];
    private final int[] blockTop = new int[ToolboxContents.TOOLS];
    private final Map<String, Anim> unlock = new HashMap<>();
    private final Map<String, Long> flashAt = new HashMap<>();
    private final Anim scroll = new Anim(0f, 220, Ease.OUT_CUBIC);
    private final List<Hit> hits = new ArrayList<>();
    private float scrollTarget;
    private int sideContentH;
    private int baseTop;
    private boolean started;
    private @Nullable ToolType focused;

    /** A hover target drawn by the screen itself (chips, tool headers, stats, upgrade lines). */
    private record Hit(int x, int y, int w, int h, List<Component> lines) {
        boolean contains(final double mx, final double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    /** The colours of one skin. */
    private record Look(boolean vanilla, int panel, int border, int well, int wellBorder, int wellHover, int text, int muted, int dim,
                        int accent, int boosted) {
        static Look current() {
            final Theme t = Theme.current();
            final Palette p = t.palette();
            if (t.isVanilla()) {
                return new Look(true, 0xFFC6C6C6, 0xFF000000, 0xFF8B8B8B, 0xFF373737, 0xFFFFFFFF, 0xFF404040, 0xFF555555, 0xFF7E7E7E,
                    p.accent(), 0xFF1E7A2E);
            }
            return new Look(false, p.surface(), p.borderStrong(), p.bg(), p.border(), p.surfaceActive(), p.text(), p.textMuted(), p.textDim(),
                p.accent(), p.accent());
        }

        /** Tier colour readable on this skin's panel (vanilla needs darker copper/diamond and a white iron). */
        int tier(final int level) {
            if (!vanilla) return ToolTier.byLevel(level).color();
            return switch (ToolTier.byLevel(level)) {
                case COPPER -> 0xFFB9582D;
                case IRON -> 0xFFF4F4F4;
                case DIAMOND -> 0xFF14A596;
                case NETHERITE -> 0xFF3B2F35;
            };
        }

        int emptyPip() {
            return vanilla ? 0xFF8B8B8B : wellBorder;
        }
    }

    public ToolboxScreen(final ToolboxMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        imageWidth = ToolboxMenu.MAIN_W + SIDE_GAP + SIDE_W;
        imageHeight = ToolboxMenu.HEIGHT;
        for (int i = 0; i < pips.length; i++) {
            pips[i] = new Anim(0f, 320, Ease.OUT_CUBIC);
            focusGlow[i] = new Anim(0f, 180, Ease.OUT_CUBIC);
        }
    }

    /**
     * Harness hook for unattended screenshots: the real cursor is ignored and menu slot {@code slot} is drawn as hovered
     * (with its tooltip); -1 parks the cursor off the screen; null restores normal input.
     */
    public static void debugHover(final @Nullable Integer slot) {
        debugHover = slot;
    }

    static ResourceLocation toolGhost(final ToolType type) {
        return TOOL_GHOSTS.get(type);
    }

    @Override
    protected void init() {
        super.init();
        baseTop = topPos;
        if (!started) {
            started = true;
            open.snap(0f);
            open.set(1f);
            final ToolboxAccess.Capabilities caps = capabilities();
            for (final ToolType t : ToolType.values()) {
                lastTier[t.ordinal()] = caps.tier(t);
                pips[t.ordinal()].snap(caps.tier(t));
            }
        }
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        topPos = baseTop + Math.round((1f - open.get()) * ENTER_SLIDE);
        hits.clear();
        if (debugHover != null) {
            super.render(g, -1000, -1000, partialTick);
            if (debugHover >= 0 && debugHover < menu.slots.size()) {
                hoveredSlot = menu.slots.get(debugHover);
                renderTooltip(g, leftPos + hoveredSlot.x + 12, topPos + hoveredSlot.y + 4);
            }
            return;
        }
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        if (t.isVanilla()) renderTransparentBackground(g);
        else g.fill(0, 0, width, height, Colors.scaleAlpha(t.palette().overlay(), 0.8f * Math.min(1f, open.get() * 1.5f)));
        renderBg(g, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final Look look = Look.current();
        final int x = leftPos, y = topPos;
        panel(g, look, x, y, ToolboxMenu.MAIN_W, imageHeight);
        panel(g, look, x + ToolboxMenu.MAIN_W + SIDE_GAP, y, SIDE_W, imageHeight);

        final ToolboxAccess.Capabilities caps = capabilities();
        final boolean pouchOn = menu.isPouchEnabled();
        for (final Slot s : menu.slots) {
            final int fx = x + s.x - 1, fy = y + s.y - 1;
            final boolean locked = menu.isLocked(s);
            final float h = hoverAnim(s.index, !locked && (isHovering(s.x, s.y, 16, 16, mouseX, mouseY) || (debugHover != null && debugHover == s.index)));
            final boolean disabled = s.index >= ToolboxContents.FIRST_POUCH && s.index < ToolboxMenu.TOOLBOX_END && !pouchOn;
            int tint = 0;
            if (s.index < ToolboxContents.TOOLS && s.getItem().getItem() instanceof BuildingToolItem tool) tint = look.tier(tool.level());
            slotFrame(g, look, fx, fy, h, disabled, tint);
            if (!s.hasItem() && s.index < ToolboxMenu.TOOLBOX_END && !disabled) {
                final ResourceLocation ghost = s.index < ToolboxContents.TOOLS ? toolGhost(ToolboxContents.toolType(s.index))
                    : s.index < ToolboxContents.FIRST_POUCH ? UPGRADE_GHOST : POUCH_GHOST;
                ghost(g, ghost, fx + 1, fy + 1, look.vanilla() ? Colors.withAlpha(0x373737, 0x70) : Colors.withAlpha(look.dim(), 0x66));
            }
        }
        toolMeters(g, look, caps);
        sidePanel(g, look, caps);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        final Look look = Look.current();
        final ToolboxAccess.Capabilities caps = capabilities();
        final Component title = look.vanilla() ? this.title : Fonts.heading(this.title);
        g.drawString(font, SlateDraw.truncate(title, ToolboxMenu.MAIN_W - 60), 8, 6, look.text(), false);
        final Component count = Component.translatable("slate_building.toolbox.screen.tools", caps.toolCount(), ToolboxContents.TOOLS);
        g.drawString(font, count, ToolboxMenu.MAIN_W - 8 - font.width(count), 6, look.muted(), false);

        g.drawString(font, Component.translatable("slate_building.toolbox.screen.upgrades"), 8, ToolboxMenu.UPGRADE_Y - 10, look.muted(), false);
        upgradeReadout(g, look, caps);

        g.drawString(font, Component.translatable("slate_building.toolbox.screen.pouch"), 8, ToolboxMenu.POUCH_Y - 10, look.muted(), false);
        final Component pouchInfo = menu.isPouchEnabled()
            ? Component.translatable("slate_building.toolbox.screen.pouch_used", pouchUsed(), ToolboxContents.POUCH)
            : Component.translatable("slate_building.toolbox.screen.pouch_off");
        g.drawString(font, pouchInfo, ToolboxMenu.MAIN_W - 8 - font.width(pouchInfo), ToolboxMenu.POUCH_Y - 10,
            menu.isPouchEnabled() ? look.dim() : Theme.current().palette().warning(), false);
        g.drawString(font, playerInventoryTitle, 8, ToolboxMenu.INV_Y - 10, look.muted(), false);

        // Over the items: the open toolbox's own slot, and vanilla's white hover wash in the vanilla skin.
        g.pose().pushPose();
        g.pose().translate(0, 0, OVER_ITEMS_Z);
        for (final Slot s : menu.slots) {
            if (menu.isLocked(s)) {
                lockedOverlay(g, look, s.x - 1, s.y - 1);
            } else if (look.vanilla()) {
                final Anim a = hover.get(s.index);
                final float h = a == null ? 0f : a.get();
                if (h > 0.01f) g.fill(s.x, s.y, s.x + 16, s.y + 16, Colors.withAlpha(0xFFFFFF, Math.round(0x80 * h)));
            }
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ main panel pieces

    private void panel(final GuiGraphics g, final Look look, final int x, final int y, final int w, final int h) {
        if (look.vanilla()) {
            vanillaPanel(g, x, y, w, h);
            return;
        }
        final int r = Theme.current().radius();
        SlateDraw.shadow(g, x, y, w, h, 0.6f * open.get());
        SlateDraw.pixelRound(g, x, y, w, h, look.panel(), r);
        SlateDraw.outline(g, x, y, w, h, look.border(), r);
    }

    /** The classic container panel: black rim with stepped corners, 2 px white/grey bevel, #C6C6C6 body. */
    private static void vanillaPanel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final int black = 0xFF000000, body = 0xFFC6C6C6, light = 0xFFFFFFFF, dark = 0xFF555555;
        g.fill(x + 2, y, x + w - 2, y + 1, black);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, black);
        g.fill(x, y + 2, x + 1, y + h - 2, black);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, black);
        g.fill(x + 1, y + 1, x + 2, y + 2, black);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, black);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, black);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, black);
        g.fill(x + 1, y + 2, x + w - 1, y + h - 2, body);
        g.fill(x + 2, y + 1, x + w - 2, y + h - 1, body);
        g.fill(x + 2, y + 1, x + w - 3, y + 3, light);
        g.fill(x + 1, y + 2, x + 3, y + h - 3, light);
        g.fill(x + 3, y + h - 3, x + w - 2, y + h - 1, dark);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 2, dark);
        g.fill(x + w - 3, y + 1, x + w - 2, y + 2, body);
        g.fill(x + 1, y + h - 3, x + 2, y + h - 2, body);
    }

    private static void slotFrame(final GuiGraphics g, final Look look, final int x, final int y, final float hover, final boolean disabled, final int tint) {
        if (look.vanilla()) {
            g.fill(x, y, x + 18, y + 18, disabled ? 0xFFA3A3A3 : 0xFF8B8B8B);
            g.fill(x, y, x + 17, y + 1, 0xFF373737);
            g.fill(x, y, x + 1, y + 17, 0xFF373737);
            g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
            g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
            return;
        }
        final int r = Math.min(2, Theme.current().radius());
        int fill = Colors.lerp(look.well(), look.wellHover(), hover * 0.85f);
        int border = look.wellBorder();
        if (tint != 0) border = Colors.mix(border, tint, 0.45f);
        border = Colors.lerp(border, Colors.mix(border, look.accent(), 0.85f), hover);
        if (disabled) {
            fill = Colors.scaleAlpha(fill, 0.45f);
            border = Colors.scaleAlpha(border, 0.5f);
        }
        SlateDraw.pixelRound(g, x, y, 18, 18, fill, r);
        SlateDraw.outline(g, x, y, 18, 18, border, r);
    }

    private static void ghost(final GuiGraphics g, final ResourceLocation tex, final int x, final int y, final int argb) {
        RenderSystem.enableBlend();
        g.setColor(Colors.red(argb) / 255f, Colors.green(argb) / 255f, Colors.blue(argb) / 255f, Colors.alpha(argb) / 255f);
        g.blit(tex, x, y, 0, 0, 16, 16, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Four pips under each tool slot, filling to the tool's tier (animated when a tool goes in or out). */
    private void toolMeters(final GuiGraphics g, final Look look, final ToolboxAccess.Capabilities caps) {
        final long now = Clock.nowMs();
        for (final ToolType t : ToolType.values()) {
            final int i = t.ordinal();
            final int tier = caps.tier(t);
            if (tier != lastTier[i]) {
                lastTier[i] = tier;
                tierChangedAt[i] = now;
            }
            pips[i].set(tier);
            final float v = pips[i].get();
            final int color = look.tier(Math.max(1, tier == 0 ? Math.round(v) : tier));
            final int px = leftPos + ToolboxMenu.TOOL_X + i * ToolboxMenu.TOOL_DX + 2;
            final int py = topPos + ToolboxMenu.TOOL_Y + 18 + 2;
            for (int p = 0; p < ToolTier.MAX; p++) {
                final float fill = Math.max(0f, Math.min(1f, v - p));
                g.fill(px + p * (PIP_W + 1), py, px + p * (PIP_W + 1) + PIP_W, py + PIP_H, Colors.lerp(look.emptyPip(), color, fill));
            }
        }
    }

    /**
     * Right of the upgrade slots: one line per installed upgrade type, with its card colour - "Area ×4", "Undo +20",
     * the Supply Link's target, ... (hover a line for the full description).
     */
    private void upgradeReadout(final GuiGraphics g, final Look look, final ToolboxAccess.Capabilities caps) {
        final int x0 = ToolboxMenu.UPGRADE_X + ToolboxContents.UPGRADES * 18 + 8;
        final int maxW = ToolboxMenu.MAIN_W - 8 - x0 - 6;
        int y = ToolboxMenu.UPGRADE_Y - 10;
        int lines = 0;
        for (final UpgradeType u : UpgradeType.values()) {
            final int n = caps.upgrade(u);
            if (n == 0) continue;
            if (lines++ == 4) break;
            final int color = look.vanilla() ? Colors.scale(u.color(), 0.75f) : u.color();
            g.fill(x0, y + 2, x0 + 3, y + 6, color);
            g.drawString(font, SlateDraw.truncate(effect(u, n), maxW), x0 + 6, y, look.vanilla() ? look.text() : look.muted(), false);
            final List<Component> tip = new ArrayList<>();
            tip.add(u.displayName().copy().append(n > 1 ? " ×" + n : ""));
            tip.add(u.description().copy().withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("slate_building.upgrade." + u.id() + ".effect").withStyle(ChatFormatting.BLUE));
            if (u == UpgradeType.SUPPLY_LINK) {
                final SupplyLink link = SupplyLink.of(editedToolbox());
                if (link != null) tip.add(Component.translatable("slate_building.toolbox.link.tooltip", link.blockName(), link.coords()).withStyle(ChatFormatting.AQUA));
            }
            hits.add(new Hit(leftPos + x0, topPos + y, maxW + 6, 9, tip));
            y += 9;
        }
        if (lines == 0) {
            g.drawString(font, Component.translatable("slate_building.toolbox.screen.no_upgrades"), x0, ToolboxMenu.UPGRADE_Y + 5, look.dim(), false);
        }
    }

    private Component effect(final UpgradeType u, final int n) {
        final String key = "slate_building.toolbox.effect." + u.id();
        return switch (u) {
            case REACH, CAPACITY, SPEED -> Component.translatable(key, 1 << n);
            case MEMORY -> Component.translatable(key, n * BuildingServerSettings.effective(minecraft.player).ops().undoPerMemory);
            case SUPPLY_LINK -> {
                final SupplyLink link = SupplyLink.of(editedToolbox());
                yield link != null ? Component.translatable(key, link.blockName()) : Component.translatable(key + ".none");
            }
            default -> Component.translatable(key);
        };
    }

    private void lockedOverlay(final GuiGraphics g, final Look look, final int x, final int y) {
        if (look.vanilla()) {
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x78000000);
            SlateDraw.outline(g, x, y, 18, 18, 0xFFFFFFFF, 0);
            Icons.draw(g, Icon.LOCK, x + 9, y + 9, 8, 0xFFFFFFFF);
        } else {
            final int r = Math.min(2, Theme.current().radius());
            SlateDraw.pixelRound(g, x + 1, y + 1, 16, 16, Colors.withAlpha(look.well(), 0xA0), r);
            SlateDraw.outline(g, x, y, 18, 18, look.accent(), r);
            Icons.draw(g, Icon.LOCK, x + 9, y + 9, 8, look.accent());
        }
    }

    // ------------------------------------------------------------------ unlock panel

    private void sidePanel(final GuiGraphics g, final Look look, final ToolboxAccess.Capabilities caps) {
        final int sx = leftPos + ToolboxMenu.MAIN_W + SIDE_GAP;
        final int x = sx + SIDE_PAD, w = SIDE_W - SIDE_PAD * 2;
        final int top = topPos + SIDE_HEADER, bottom = topPos + imageHeight - SIDE_FOOTER;
        final long now = Clock.nowMs();

        final Component heading = look.vanilla() ? Component.translatable("slate_building.toolbox.screen.unlocks")
            : Fonts.heading(Component.translatable("slate_building.toolbox.screen.unlocks"));
        g.drawString(font, heading, x + 2, topPos + 6, look.text(), false);
        if (creativeBypass()) {
            // Creative players are not limited by any toolbox: say so where the tally would be.
            final Component c = Component.translatable("slate_building.toolbox.screen.creative");
            final int cw = font.width(c) + 8;
            final int cxp = sx + SIDE_W - SIDE_PAD - cw, cyp = topPos + 4;
            if (look.vanilla()) g.fill(cxp, cyp, cxp + cw, cyp + CHIP_H, 0xFF555555);
            else SlateDraw.pixelRound(g, cxp, cyp, cw, CHIP_H, look.accent(), Math.min(2, Theme.current().radius()));
            g.drawString(font, c, cxp + 4, cyp + 2, look.vanilla() ? 0xFFFFFFFF : Colors.readableOn(look.accent()), false);
            hits.add(new Hit(cxp, cyp, cw, CHIP_H, List.of(c, Component.translatable("slate_building.toolbox.screen.creative.tip").withStyle(ChatFormatting.GRAY))));
        } else {
            final Component tally = Component.literal(countUnlocked(caps) + "/" + countAll());
            g.drawString(font, tally, sx + SIDE_W - SIDE_PAD - 2 - font.width(tally), topPos + 6, look.muted(), false);
        }
        hits.add(new Hit(x, topPos + 4, w, 12, List.of(Component.translatable("slate_building.toolbox.screen.unlocks.tip"))));

        followFocus(now, bottom - top, caps);
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, sideContentH - (bottom - top))));
        scroll.set(scrollTarget);
        final int offset = Math.round(scroll.get());
        SlateDraw.scissor(g, sx + 1, top, SIDE_W - 2, bottom - top);
        int yy = top - offset;
        for (final ToolType t : ToolType.values()) {
            final int i = t.ordinal();
            final int tier = caps.tier(t);
            final int rowTop = yy;
            blockTop[i] = yy + offset - top;
            final float glow = focusGlow[i].get();
            final int blockH = measureBlock(t, w, caps);
            if (glow > 0.01f) {
                final int tintColor = look.vanilla() ? Colors.withAlpha(0xFFFFFF, Math.round(0x70 * glow)) : Colors.withAlpha(look.wellHover(), Math.round(0xB0 * glow));
                SlateDraw.pixelRound(g, x - 3, rowTop - 2, w + 6, blockH + 3, tintColor, Math.min(2, Theme.current().radius()));
            }
            final ItemStack tool = menu.slots.get(ToolboxContents.toolSlot(t)).getItem();
            if (tier > 0 && !tool.isEmpty()) g.renderItem(tool, x, yy);
            else ghost(g, toolGhost(t), x, yy, look.vanilla() ? Colors.withAlpha(0x555555, 0x80) : Colors.withAlpha(look.dim(), 0x80));
            final int tx = x + ICON_COL;
            g.drawString(font, t.displayName(), tx, yy, tier > 0 ? look.text() : look.dim(), false);
            meter(g, look, x + w - 15, yy + 3, tier);
            addHit(tx, yy, x + w - tx, 9, top, bottom, toolLines(t, tier));

            int cx = tx, cy = yy + 11;
            final List<ToolUnlocks.Unlock> unlocks = ToolUnlocks.of(t);
            for (int k = 0; k < unlocks.size(); k++) {
                final ToolUnlocks.Unlock u = unlocks.get(k);
                final boolean on = u.unlockedBy(caps);
                final String key = t.id() + ":" + u.id();
                final Anim a = unlock.computeIfAbsent(key, kk -> {
                    final Anim fresh = new Anim(on ? 1f : 0f, 240, Ease.OUT_CUBIC);
                    fresh.snap(on ? 1f : 0f);
                    return fresh;
                });
                if (a.target() != (on ? 1f : 0f) && now >= tierChangedAt[i] + (long) k * STAGGER_MS) {
                    a.set(on ? 1f : 0f);
                    if (on) flashAt.put(key, now);
                }
                final Long flashStart = flashAt.get(key);
                final float flash = flashStart == null ? 0f : Math.max(0f, 1f - (now - flashStart) / (float) Theme.current().ms(FLASH_MS));
                final Component name = u.name();
                final int cw = chipWidth(u, on);
                if (cx > tx && cx + cw > x + w) {
                    cx = tx;
                    cy += CHIP_H + 1;
                }
                chip(g, look, cx, cy, cw, name, a.get(), flash, u.tier());
                addHit(cx, cy, cw, CHIP_H, top, bottom, unlockLines(u, caps));
                cx += cw + CHIP_GAP;
            }
            yy = rowTop + blockH + BLOCK_GAP;
        }
        SlateDraw.unscissor(g);
        sideContentH = (yy + offset) - top - BLOCK_GAP;

        final int view = bottom - top;
        if (sideContentH > view) {
            final int fade = look.vanilla() ? 0xFFC6C6C6 : look.panel();
            if (offset > 0) SlateDraw.vgradient(g, sx + 1, top, SIDE_W - 2, 8, fade, Colors.withAlpha(fade, 0));
            if (offset < sideContentH - view) SlateDraw.vgradient(g, sx + 1, bottom - 8, SIDE_W - 2, 8, Colors.withAlpha(fade, 0), fade);
            final int barH = Math.max(12, view * view / sideContentH);
            final int barY = top + Math.round((view - barH) * (offset / (float) Math.max(1, sideContentH - view)));
            g.fill(sx + SIDE_W - 4, barY, sx + SIDE_W - 2, barY + barH, look.vanilla() ? 0xFF8B8B8B : look.wellBorder());
        }
        footer(g, look, caps, sx, bottom);
    }

    /** Height of one tool's block (header + wrapped chips), laid out exactly as it is drawn. */
    private int measureBlock(final ToolType t, final int w, final ToolboxAccess.Capabilities caps) {
        final int tx = ICON_COL;
        int cx = tx, rows = 1;
        for (final ToolUnlocks.Unlock u : ToolUnlocks.of(t)) {
            final int cw = chipWidth(u, u.unlockedBy(caps));
            if (cx > tx && cx + cw > w) {
                cx = tx;
                rows++;
            }
            cx += cw + CHIP_GAP;
        }
        return Math.max(16, 11 + rows * (CHIP_H + 1) - 1);
    }

    private int chipWidth(final ToolUnlocks.Unlock u, final boolean on) {
        return font.width(u.name()) + 6 + (on ? 0 : 4);
    }

    /**
     * Scrolls the panel to the tool the player is dealing with: the tool slot or tool item under the cursor, a carried
     * tool, or a tool whose tier just changed. Edge-triggered, so the wheel stays free in between.
     */
    private void followFocus(final long now, final int view, final ToolboxAccess.Capabilities caps) {
        ToolType want = null;
        final Slot hs = debugHover != null && debugHover >= 0 && debugHover < menu.slots.size() ? menu.slots.get(debugHover) : hoveredSlot;
        if (menu.getCarried().getItem() instanceof BuildingToolItem carried) want = carried.type();
        else if (hs != null && hs.index < ToolboxContents.TOOLS) want = ToolboxContents.toolType(hs.index);
        else if (hs != null && hs.getItem().getItem() instanceof BuildingToolItem tool) want = tool.type();
        if (want == null) {
            for (final ToolType t : ToolType.values()) {
                if (tierChangedAt[t.ordinal()] > 0 && now - tierChangedAt[t.ordinal()] < FOCUS_AFTER_CHANGE_MS) want = t;
            }
        }
        if (want != focused) {
            focused = want;
            if (want != null) {
                final int blockH = measureBlock(want, SIDE_W - SIDE_PAD * 2, caps);
                final int topY = blockTop[want.ordinal()];
                final float cur = scroll.target();
                if (topY < cur + 2) scrollTarget = Math.max(0, topY - 2);
                else if (topY + blockH > cur + view - 2) scrollTarget = topY + blockH - view + 4;
            }
        }
        for (final ToolType t : ToolType.values()) focusGlow[t.ordinal()].set(t == focused ? 1f : 0f);
    }

    private void meter(final GuiGraphics g, final Look look, final int x, final int y, final int tier) {
        final int color = tier > 0 ? look.tier(tier) : look.emptyPip();
        for (int p = 0; p < ToolTier.MAX; p++) g.fill(x + p * 4, y, x + p * 4 + 3, y + 3, p < tier ? color : look.emptyPip());
    }

    private void chip(final GuiGraphics g, final Look look, final int x, final int y, final int w, final Component name,
                      final float on, final float flash, final int tier) {
        final int r = Math.min(2, Theme.current().radius());
        if (look.vanilla()) {
            if (on > 0.01f) g.fill(x, y, x + w, y + CHIP_H, Colors.withAlpha(0x8B8B8B, Math.round(255 * on)));
            if (on < 0.99f) SlateDraw.outline(g, x, y, w, CHIP_H, Colors.withAlpha(0x9A9A9A, Math.round(255 * (1 - on))), 0);
            if (flash > 0.01f) SlateDraw.outline(g, x, y, w, CHIP_H, Colors.withAlpha(0xFFFFFF, Math.round(255 * flash)), 0);
            g.drawString(font, name, x + 3, y + 2, Colors.lerp(0xFF7E7E7E, 0xFFFFFFFF, on), false);
        } else {
            final int fill = Colors.withAlpha(look.wellHover(), Math.round(255 * on));
            SlateDraw.pixelRound(g, x, y, w, CHIP_H, fill, r);
            int border = Colors.lerp(look.wellBorder(), look.wellHover(), on);
            border = Colors.lerp(border, look.accent(), flash);
            SlateDraw.outline(g, x, y, w, CHIP_H, border, r);
            g.drawString(font, name, x + 3, y + 2, Colors.lerp(look.dim(), look.text(), on), false);
        }
        if (on < 0.99f) {
            // The tier that unlocks it, as a dot in that tier's colour.
            g.fill(x + w - 5, y + 4, x + w - 3, y + 6, Colors.scaleAlpha(look.tier(tier), 1f - on));
        }
    }

    /** The operation limits of this toolbox in a two-column grid; values raised by upgrades are highlighted. */
    private void footer(final GuiGraphics g, final Look look, final ToolboxAccess.Capabilities caps, final int sx, final int bottom) {
        final int x = sx + SIDE_PAD + 2, w = SIDE_W - SIDE_PAD * 2 - 4;
        SlateDraw.hline(g, sx + SIDE_PAD, bottom, SIDE_W - SIDE_PAD * 2, look.vanilla() ? 0xFF8B8B8B : look.wellBorder());
        final int y0 = bottom + 4;
        if (caps.toolCount() == 0) {
            int yy = y0;
            for (final FormattedCharSequence line : font.split(Component.translatable("slate_building.toolbox.screen.empty_hint"), w)) {
                g.drawString(font, line, x, yy, look.dim(), false);
                yy += 10;
            }
        } else {
            final BuildingServerSettings settings = BuildingServerSettings.effective(minecraft.player);
            final Limits base = new ToolboxAccess.Capabilities(caps.tiers(), Map.of(), false).limits(settings);
            final Limits l = caps.limits(settings);
            final NumberFormat nf = NumberFormat.getIntegerInstance(Locale.ROOT);
            stat(g, look, x, y0, "area", Component.literal(nf.format(l.maxVolume())), l.maxVolume() > base.maxVolume(), UpgradeType.CAPACITY, caps);
            stat(g, look, x + STAT_COL, y0, "span", Component.literal(String.valueOf(l.maxSpan())), l.maxSpan() > base.maxSpan(), UpgradeType.CAPACITY, caps);
            stat(g, look, x, y0 + 10, "speed", Component.translatable("slate_building.toolbox.stat.speed.value", l.blocksPerTick()),
                l.blocksPerTick() > base.blocksPerTick(), UpgradeType.SPEED, caps);
            stat(g, look, x + STAT_COL, y0 + 10, "reach", Component.literal("+" + l.reachBonus()), l.reachBonus() > base.reachBonus(), UpgradeType.REACH, caps);
            stat(g, look, x, y0 + 20, "undo", Component.literal(String.valueOf(l.undoDepth())), l.undoDepth() > base.undoDepth(), UpgradeType.MEMORY, caps);
            if (caps.upgrade(UpgradeType.EFFICIENCY) > 0) {
                stat(g, look, x + STAT_COL, y0 + 20, "wear", Component.literal("½"), true, UpgradeType.EFFICIENCY, caps);
            }
        }
    }

    private void stat(final GuiGraphics g, final Look look, final int x, final int y, final String id, final Component value, final boolean boosted,
                      final UpgradeType by, final ToolboxAccess.Capabilities caps) {
        final Component label = Component.translatable("slate_building.toolbox.stat." + id);
        g.drawString(font, label, x, y, look.muted(), false);
        final int vx = x + font.width(label) + 4;
        g.drawString(font, value, vx, y, boosted ? look.boosted() : look.text(), false);
        final List<Component> tip = new ArrayList<>();
        tip.add(label);
        tip.add(Component.translatable("slate_building.toolbox.stat." + id + ".desc").withStyle(ChatFormatting.GRAY));
        if (boosted) {
            tip.add(Component.translatable("slate_building.toolbox.stat.boosted", by.displayName(), caps.upgrade(by)).withStyle(ChatFormatting.BLUE));
        }
        hits.add(new Hit(x, y - 1, vx + font.width(value) - x, 10, tip));
    }

    private void addHit(final int x, final int y, final int w, final int h, final int clipTop, final int clipBottom, final List<Component> lines) {
        final int y0 = Math.max(y, clipTop), y1 = Math.min(y + h, clipBottom);
        if (y1 > y0) hits.add(new Hit(x, y0, w, y1 - y0, lines));
    }

    private static int countAll() {
        int n = 0;
        for (final ToolType t : ToolType.values()) n += ToolUnlocks.of(t).size();
        return n;
    }

    private static int countUnlocked(final ToolboxAccess.Capabilities caps) {
        int n = 0;
        for (final ToolType t : ToolType.values()) for (final ToolUnlocks.Unlock u : ToolUnlocks.of(t)) if (u.unlockedBy(caps)) n++;
        return n;
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void renderTooltip(final GuiGraphics g, final int mouseX, final int mouseY) {
        if (!menu.getCarried().isEmpty()) return;
        if (hoveredSlot != null) {
            if (hoveredSlot.hasItem()) {
                super.renderTooltip(g, mouseX, mouseY);
                return;
            }
            if (hoveredSlot.index < ToolboxMenu.TOOLBOX_END) {
                g.renderComponentTooltip(font, emptySlotLines(hoveredSlot.index), mouseX, mouseY);
            }
            return;
        }
        for (final Hit h : hits) {
            if (h.contains(mouseX, mouseY)) {
                g.renderComponentTooltip(font, h.lines(), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(final ItemStack stack) {
        final List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (hoveredSlot != null && menu.isLocked(hoveredSlot)) {
            final List<Component> out = new ArrayList<>(lines);
            out.add(Component.translatable("slate_building.toolbox.screen.locked").withStyle(ChatFormatting.GOLD));
            return out;
        }
        return lines;
    }

    private List<Component> emptySlotLines(final int index) {
        final List<Component> lines = new ArrayList<>();
        switch (ToolboxContents.kind(index)) {
            case TOOL -> {
                final ToolType t = ToolboxContents.toolType(index);
                lines.add(Component.translatable("slate_building.toolbox.slot.tool", t.displayName()));
                lines.add(Component.translatable("slate_building.toolbox.slot.tool.desc", t.displayName()).withStyle(ChatFormatting.GRAY));
                BuildingToolItem.addNames(lines, Component.translatable("slate_building.tool.unlocks"), ToolUnlocks.of(t), ChatFormatting.DARK_GRAY);
            }
            case UPGRADE -> {
                lines.add(Component.translatable("slate_building.toolbox.slot.upgrade"));
                lines.add(Component.translatable("slate_building.toolbox.slot.upgrade.desc").withStyle(ChatFormatting.GRAY));
            }
            case POUCH -> {
                lines.add(Component.translatable("slate_building.toolbox.slot.pouch"));
                lines.add(Component.translatable("slate_building.toolbox.slot.pouch.desc").withStyle(ChatFormatting.GRAY));
                if (!menu.isPouchEnabled()) lines.add(Component.translatable("slate_building.toolbox.slot.pouch.off").withStyle(ChatFormatting.RED));
            }
        }
        return lines;
    }

    private static List<Component> toolLines(final ToolType t, final int tier) {
        final List<Component> lines = new ArrayList<>();
        lines.add(t.displayName());
        if (tier > 0) lines.add(BuildingToolItem.tierLine(tier));
        else lines.add(Component.translatable("slate_building.toolbox.screen.tool_missing").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static List<Component> unlockLines(final ToolUnlocks.Unlock u, final ToolboxAccess.Capabilities caps) {
        final List<Component> lines = new ArrayList<>();
        lines.add(u.name());
        lines.add(u.description().copy().withStyle(ChatFormatting.GRAY));
        if (u.unlockedBy(caps)) {
            lines.add(Component.translatable("slate_building.toolbox.screen.unlocked").withStyle(ChatFormatting.GREEN));
        } else if (u.mode() != null) {
            final Component reason = caps.lockReason(u.mode());
            if (reason != null) lines.add(reason.copy().withStyle(ChatFormatting.GOLD));
        } else {
            final MutableComponent needed = Component.translatable("slate_building.tool_tiered", ToolTier.byLevel(u.tier()).displayName(), u.tool().displayName());
            lines.add(Component.translatable("slate_building.lock.needs_tool", needed).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        final int sx = leftPos + ToolboxMenu.MAIN_W + SIDE_GAP;
        if (mouseX >= sx && mouseX < sx + SIDE_W && mouseY >= topPos && mouseY < topPos + imageHeight) {
            scrollTarget -= (float) scrollY * 20f;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ state

    private float hoverAnim(final int slot, final boolean hovered) {
        final Anim a = hover.computeIfAbsent(slot, k -> new Anim(0f, 110, Ease.OUT_CUBIC));
        a.set(hovered ? 1f : 0f);
        return a.get();
    }

    /** What the open toolbox unlocks, from the menu's live slots (no creative bypass: this is about the toolbox). */
    private ToolboxAccess.Capabilities capabilities() {
        final List<ItemStack> items = new ArrayList<>(ToolboxContents.SIZE);
        for (int i = 0; i < ToolboxContents.SIZE; i++) items.add(menu.toolbox().getItem(i));
        return ToolboxAccess.Capabilities.of(items);
    }

    /** Creative players skip the toolbox entirely (server rule {@code ops.creativeBypass}). */
    private boolean creativeBypass() {
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative()
            && BuildingServerSettings.effective(minecraft.player).ops().creativeBypass;
    }

    private int pouchUsed() {
        int n = 0;
        for (int i = ToolboxContents.FIRST_POUCH; i < ToolboxContents.SIZE; i++) if (!menu.toolbox().getItem(i).isEmpty()) n++;
        return n;
    }

    /** The toolbox stack being edited, as this client sees it (for its Supply Link). */
    private ItemStack editedToolbox() {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        final int slot = menu.lockedSlot();
        if (slot == ToolboxMenu.NOT_LOCKED) return BetterInventoryBridge.toolbox(minecraft.player);
        return minecraft.player.getInventory().getItem(slot);
    }
}
