package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.layout.ElementType;
import dev.fallingcloud.slate.core.layout.ElementTypes;
import dev.fallingcloud.slate.core.layout.Placeholders;
import dev.fallingcloud.slate.core.layout.ScreenLayout;
import dev.fallingcloud.slate.core.layout.action.ActionType.Arg;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateAvatar;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateSeparator;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The built-in layout element types. */
public final class CoreElements {

    private static Component text(final ScreenLayout.Element e, final String key, final String def) {
        return Component.literal(Placeholders.apply(e.props.getOrDefault(key, def)));
    }

    private static Icon icon(final ScreenLayout.Element e, final String key, final Icon def) {
        try { return Icon.valueOf(e.props.getOrDefault(key, def.name()).toUpperCase(Locale.ROOT)); } catch (final IllegalArgumentException ex) { return def; }
    }

    static void registerAll() {
        ElementTypes.register(new Simple("slate:button", "Button", Icon.TOGGLE, new int[] { 100, 20 },
            List.of(Arg.text("label", Component.literal("Label"), "Button"),
                    Arg.choice("variant", Component.literal("Style"), "SECONDARY", List.of("PRIMARY", "SECONDARY", "GHOST", "DANGER")),
                    Arg.text("icon", Component.literal("Icon (name or empty)"), ""),
                    Arg.text("tooltip", Component.literal("Tooltip"), "")),
            (screen, e, x, y, w, h, run) -> {
                final SlateButton b = new SlateButton(x, y, w, h, text(e, "label", "Button"), run);
                try { b.variant(SlateButton.Variant.valueOf(e.props.getOrDefault("variant", "SECONDARY").toUpperCase(Locale.ROOT))); } catch (final IllegalArgumentException ignored) {}
                final String ic = e.props.getOrDefault("icon", "");
                if (!ic.isEmpty()) b.icon(icon(e, "icon", Icon.DOT));
                final String tip = e.props.getOrDefault("tooltip", "");
                if (!tip.isEmpty()) b.tip(text(e, "tooltip", ""));
                return b;
            }));
        ElementTypes.register(new Simple("slate:icon_button", "Icon button", Icon.STAR, new int[] { 20, 20 },
            List.of(Arg.text("icon", Component.literal("Icon"), "STAR"), Arg.text("tooltip", Component.literal("Tooltip"), "")),
            (screen, e, x, y, w, h, run) -> new SlateIconButton(x, y, Math.min(w, h), icon(e, "icon", Icon.STAR), text(e, "tooltip", ""), run)));
        ElementTypes.register(new Simple("slate:label", "Text", Icon.TEXT, new int[] { 120, 10 },
            List.of(Arg.multiline("text", Component.literal("Text ({player}, {fps}...)"), "Hello {player}"),
                    Arg.choice("style", Component.literal("Style"), "BODY", List.of("HEADING", "TITLE", "BODY", "MUTED", "CAPTION")),
                    Arg.choice("align", Component.literal("Align"), "LEFT", List.of("LEFT", "CENTER", "RIGHT")),
                    Arg.text("color", Component.literal("Colour (#hex, empty = theme)"), ""),
                    Arg.bool("wrap", Component.literal("Wrap"), false)),
            (screen, e, x, y, w, h, run) -> {
                final SlateLabel l = new LiveLabel(x, y, w, e);
                try { l.style(SlateLabel.Style.valueOf(e.props.getOrDefault("style", "BODY").toUpperCase(Locale.ROOT))); } catch (final IllegalArgumentException ignored) {}
                try { l.align(SlateLabel.Align.valueOf(e.props.getOrDefault("align", "LEFT").toUpperCase(Locale.ROOT))); } catch (final IllegalArgumentException ignored) {}
                final int c = Colors.fromHex(e.props.getOrDefault("color", ""), 0);
                if (c != 0) l.color(c);
                l.wrap(Boolean.parseBoolean(e.props.getOrDefault("wrap", "false")));
                return l;
            }) { @Override public boolean clickable() { return false; } });
        ElementTypes.register(new Simple("slate:image", "Image", Icon.IMAGE, new int[] { 64, 64 },
            List.of(Arg.text("source", Component.literal("File (game dir) or namespace:path"), "config/slate/images/logo.png"),
                    Arg.choice("fit", Component.literal("Fit"), "CONTAIN", List.of("CONTAIN", "COVER", "STRETCH")),
                    Arg.number("alpha", Component.literal("Opacity 0-1"), "1")),
            (screen, e, x, y, w, h, run) -> new ImageElement(x, y, w, h, e, run)));
        ElementTypes.register(new Simple("slate:panel", "Panel", Icon.PANEL, new int[] { 120, 80 },
            List.of(Arg.text("fill", Component.literal("Fill (#hex, empty = surface)"), ""),
                    Arg.text("border", Component.literal("Border (#hex, empty = theme)"), ""),
                    Arg.number("alpha", Component.literal("Opacity 0-1"), "1")),
            (screen, e, x, y, w, h, run) -> new PanelElement(x, y, w, h, e)) { @Override public boolean clickable() { return false; } });
        ElementTypes.register(new Simple("slate:separator", "Separator", Icon.MINUS, new int[] { 120, 1 },
            List.of(Arg.text("caption", Component.literal("Caption"), ""), Arg.bool("vertical", Component.literal("Vertical"), false)),
            (screen, e, x, y, w, h, run) -> {
                final String cap = e.props.getOrDefault("caption", "");
                if (Boolean.parseBoolean(e.props.getOrDefault("vertical", "false"))) return new SlateSeparator(x, y, h, true);
                return cap.isEmpty() ? new SlateSeparator(x, y, w, false) : new SlateSeparator(x, y, w, Component.literal(cap));
            }) { @Override public boolean clickable() { return false; } });
        ElementTypes.register(new Simple("slate:player_head", "Player head", Icon.USER, new int[] { 32, 32 },
            List.of(Arg.text("player", Component.literal("Player name (empty = you)"), "")),
            (screen, e, x, y, w, h, run) -> {
                final String name = e.props.getOrDefault("player", "");
                final Minecraft mc = Minecraft.getInstance();
                final SlateAvatar a = name.isEmpty()
                    ? new SlateAvatar(x, y, Math.min(w, h), mc.getGameProfile())
                    : new SlateAvatar(x, y, Math.min(w, h), net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name), name);
                if (!e.actions.isEmpty()) a.onClick(run);
                return a;
            }));
        ElementTypes.register(new Simple("slate:link", "Link", Icon.LINK, new int[] { 100, 10 },
            List.of(Arg.text("label", Component.literal("Label"), "Website"), Arg.text("url", Component.literal("URL"), "https://")),
            (screen, e, x, y, w, h, run) -> {
                final SlateButton b = new SlateButton(x, y, w, h, text(e, "label", "Link"), () -> {
                    dev.fallingcloud.slate.core.layout.action.Actions.run("slate:open_url", java.util.Map.of("url", e.props.getOrDefault("url", ""), "confirm", "true"));
                    run.run();
                }).variant(SlateButton.Variant.GHOST).leftAligned();
                b.icon(Icon.EXTERNAL);
                return b;
            }));
        ElementTypes.register(new Simple("slate:spacer", "Spacer", Icon.MOVE, new int[] { 20, 20 }, List.of(),
            (screen, e, x, y, w, h, run) -> new PanelElement(x, y, w, h, e, true)) { @Override public boolean clickable() { return false; } });
    }

    // ------------------------------------------------------------------ helper types

    @FunctionalInterface
    interface Factory {
        AbstractWidget create(Screen screen, ScreenLayout.Element e, int x, int y, int w, int h, Runnable run);
    }

    static class Simple implements ElementType {
        private final String id;
        private final String label;
        private final Icon icon;
        private final int[] size;
        private final List<Arg> props;
        private final Factory factory;

        Simple(final String id, final String label, final Icon icon, final int[] size, final List<Arg> props, final Factory factory) {
            this.id = id; this.label = label; this.icon = icon; this.size = size; this.props = props; this.factory = factory;
        }

        @Override public String id() { return id; }
        @Override public Component label() { return Component.literal(label); }
        @Override public Icon icon() { return icon; }
        @Override public int[] defaultSize() { return size; }
        @Override public List<Arg> props() { return props; }
        @Override public AbstractWidget create(final Screen screen, final ScreenLayout.Element element, final int x, final int y, final int w, final int h, final Runnable runActions) {
            return factory.create(screen, element, x, y, w, h, runActions);
        }
    }

    /** A label whose placeholders refresh every frame ({fps}, {time}). */
    static final class LiveLabel extends SlateLabel {
        private final ScreenLayout.Element e;
        private String lastText = "";

        LiveLabel(final int x, final int y, final int w, final ScreenLayout.Element e) {
            super(x, y, w, Component.literal(Placeholders.apply(e.props.getOrDefault("text", ""))));
            this.e = e;
        }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            refresh();
            super.renderDark(g, mouseX, mouseY, partialTick);
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            refresh();
            super.renderVanilla(g, mouseX, mouseY, partialTick);
        }

        private void refresh() {
            final String t = Placeholders.apply(e.props.getOrDefault("text", ""));
            if (!t.equals(lastText)) { lastText = t; text(Component.literal(t)); }
        }
    }

    static final class PanelElement extends SlateWidget {
        private final ScreenLayout.Element e;
        private final boolean invisible;

        PanelElement(final int x, final int y, final int w, final int h, final ScreenLayout.Element e) { this(x, y, w, h, e, false); }

        PanelElement(final int x, final int y, final int w, final int h, final ScreenLayout.Element e, final boolean invisible) {
            super(x, y, w, h, Component.empty());
            this.e = e;
            this.invisible = invisible;
            this.active = false;
        }

        @Override public boolean mouseClicked(final double mouseX, final double mouseY, final int button) { return false; }

        @Override public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent event) { return null; }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            if (invisible) return;
            float alpha = 1f;
            try { alpha = Float.parseFloat(e.props.getOrDefault("alpha", "1")); } catch (final NumberFormatException ignored) {}
            final int fill = Colors.fromHex(e.props.getOrDefault("fill", ""), Theme.current().surface());
            final int border = Colors.fromHex(e.props.getOrDefault("border", ""), Theme.current().border());
            SlateDraw.panel(g, getX(), getY(), getWidth(), getHeight(), Colors.scaleAlpha(fill, alpha), Colors.scaleAlpha(border, alpha));
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            if (invisible) return;
            float alpha = 1f;
            try { alpha = Float.parseFloat(e.props.getOrDefault("alpha", "1")); } catch (final NumberFormatException ignored) {}
            final int fill = Colors.fromHex(e.props.getOrDefault("fill", ""), 0xFF000000);
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), Colors.scaleAlpha(Colors.withAlpha(fill, 0x90), alpha));
            SlateDraw.outline(g, getX(), getY(), getWidth(), getHeight(), Colors.scaleAlpha(0xFF8B8B8B, alpha), 0);
        }
    }

    static final class ImageElement extends SlateWidget {
        private final ScreenLayout.Element e;
        private final Runnable run;
        private Textures.Loaded loaded;
        private ResourceLocation resource;
        private boolean requested;

        ImageElement(final int x, final int y, final int w, final int h, final ScreenLayout.Element e, final Runnable run) {
            super(x, y, w, h, Component.empty());
            this.e = e;
            this.run = run;
            this.active = !e.actions.isEmpty();
            this.silent();
        }

        @Override
        public void onClick(final double mouseX, final double mouseY) { super.onClick(mouseX, mouseY); run.run(); }

        private void ensure() {
            if (requested) return;
            requested = true;
            final String src = e.props.getOrDefault("source", "");
            if (src.contains(":") && !src.contains("/") && !src.contains("\\") || src.matches("^[a-z0-9_.-]+:[a-z0-9_./-]+$")) {
                resource = ResourceLocation.tryParse(src);
                return;
            }
            final Path p = SlatePlatform.get().gameDir().resolve(src);
            Textures.load(p, l -> loaded = l);
        }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            ensure();
            float alpha = 1f;
            try { alpha = Float.parseFloat(e.props.getOrDefault("alpha", "1")); } catch (final NumberFormatException ignored) {}
            final int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1, 1, 1, alpha * effectiveAlpha());
            if (resource != null) {
                g.blit(resource, x, y, w, h, 0, 0, 256, 256, 256, 256);
            } else if (loaded != null) {
                final String fit = e.props.getOrDefault("fit", "CONTAIN").toUpperCase(Locale.ROOT);
                int dw = w, dh = h, dx = x, dy = y;
                if (!"STRETCH".equals(fit)) {
                    final float s = "COVER".equals(fit) ? Math.max((float) w / loaded.width(), (float) h / loaded.height())
                        : Math.min((float) w / loaded.width(), (float) h / loaded.height());
                    dw = Math.round(loaded.width() * s); dh = Math.round(loaded.height() * s);
                    dx = x + (w - dw) / 2; dy = y + (h - dh) / 2;
                    if ("COVER".equals(fit)) SlateDraw.scissor(g, x, y, w, h);
                }
                g.blit(loaded.id(), dx, dy, dw, dh, 0, 0, loaded.width(), loaded.height(), loaded.width(), loaded.height());
                if ("COVER".equals(fit)) SlateDraw.unscissor(g);
            } else {
                SlateDraw.outline(g, x, y, w, h, Theme.current().border(), 0);
            }
            g.setColor(1, 1, 1, 1);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            renderDark(g, mouseX, mouseY, partialTick);
        }
    }

    private CoreElements() {}
}
