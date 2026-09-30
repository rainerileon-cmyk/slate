package dev.fallingcloud.slate.profile.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.media.FilePicker;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.stage.StageWidget;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSearchField;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import dev.fallingcloud.slate.profile.Slot;
import dev.fallingcloud.slate.profile.client.AppliedLooks;
import dev.fallingcloud.slate.profile.client.Cosmetic;
import dev.fallingcloud.slate.profile.client.Cosmetics;
import dev.fallingcloud.slate.profile.client.ProfileClient;
import dev.fallingcloud.slate.profile.client.ProfileStore;
import dev.fallingcloud.slate.profile.client.SkinComposer;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The profile screen: the player's looks, and the player's profile.
 *
 * <p>The Overhaul layout (docs/LAYOUTS.md, 5) is a stage. The looks stand on it side by side, five in sight: the
 * one in view in the middle under a lamp, two more to either side. The look that is worn has a brighter lamp, which
 * shines wherever in the row it stands. The arrows (and the arrow keys, and a click on a look at the side) go to
 * the next one; after the last look stands an empty place, where a new one is made. Under the stage stands the
 * name of the look in view, large, with what it is made of and a dot for every place of the row; then Select and
 * Edit. At the foot of the screen lies the profile's card: the picture, the account, the bio. The stage has all
 * the height those leave.</p>
 *
 * <p>Edit sends the other looks off the stage and brings the view closer. Down the left stand the slots of the
 * body (the skin, the arms and legs, the pet), down the right what is worn (hat, face, shirt, trousers, back). A
 * slot, once chosen, brings the view right up to its part of the body, and the row of boxes under the player shows
 * what there is for it, in 3D; the pointer on a box tries the thing on. The magnifier at the corner of the view
 * steps back from the close-up, and dragging over the view turns the player, to see a thing from every side.</p>
 *
 * <p>The Custom layout is the same looks and the same profile as lists: the looks down the left, one look in a
 * plain view, a row per slot with what is in it.</p>
 */
public final class ProfileScreen extends SlateScreen {

    private final boolean overhaul;
    private ProfileStore.Account account;
    @Nullable private LookStage looks;
    @Nullable private Shelf shelf;
    private boolean editing;
    @Nullable private Slot slot;
    private boolean closeUp = true;
    private String query = "", thingQuery = "";
    @Nullable private String viewedId;
    private boolean viewingBlank;
    private Rect stageRect = new Rect(0, 0, 0, 0);
    /** Where the words under the row of looks stand: the name (and how large it is written), what the look is made of. */
    private int nameY, captionY = -1;
    private float nameScale = 1f;
    /** The profile's card, and in the edit view where the two columns of slots have their headings. */
    private Rect cardRect = new Rect(0, 0, 0, 0);
    private int groupsY = -1, groupsW;
    /** The player is being turned by hand; and whether that was ever done, after which nothing says it can be. */
    private boolean turning;
    private static boolean turnedOnce;
    @Nullable private Textures.Loaded picture;
    @Nullable private Path pictureOf;
    @Nullable private MultiLineEditBox bio;
    private boolean bioDirty;
    private int bioTicks;
    @Nullable private Slot startWith;

    public ProfileScreen(@Nullable final Screen parent, final boolean overhaul) {
        super(Component.translatable("slate_profile.title"), parent);
        this.overhaul = overhaul;
        this.account = ProfileClient.account();
        final ProfileStore.Look worn = account.worn();
        this.viewedId = worn == null ? null : worn.id();
    }

    /** Opens on the edit view of the look that is worn (the harness, and links from elsewhere). */
    public ProfileScreen startEditing(@Nullable final Slot slot) {
        this.startWith = slot == null ? Slot.SKIN : slot;
        return this;
    }

    /** Opens on the empty place at the end of the row (the harness). */
    public ProfileScreen startBlank() {
        this.viewingBlank = true;
        return this;
    }

    private boolean own() { return account.uuid().equals(ProfileClient.self()); }

    @Override
    public Component getTitle() {
        if (!editing) return super.getTitle();
        final ProfileStore.Look look = viewed();
        final String of = look == null ? "" : look.name();
        return Component.translatable("slate_profile.title.edit", of, slot == null ? Component.translatable("slate_profile.slot.all") : slotName(slot));
    }

    static Component slotName(final Slot slot) {
        return Component.translatable("slate_profile.slot." + slot.key());
    }

    // ------------------------------------------------------------------ what is shown

    private List<ProfileStore.Look> listed() {
        final List<ProfileStore.Look> out = new ArrayList<>();
        final String q = query.trim().toLowerCase(Locale.ROOT);
        for (final ProfileStore.Look l : account.looks()) if (q.isEmpty() || l.name().toLowerCase(Locale.ROOT).contains(q)) out.add(l);
        return out;
    }

    @Nullable
    private ProfileStore.Look viewed() {
        if (looks != null && overhaul) {
            final LookStage.Stand st = looks.viewedStand();
            return st == null ? null : st.look;
        }
        return account.look(viewedId);
    }

    private void fillStage() {
        if (looks == null) return;
        if (overhaul) {
            // The empty place stays in view when it was: it is the last of the row.
            if (viewingBlank) looks.show(listed(), true, null, Integer.MAX_VALUE);
            else looks.show(listed(), query.isBlank(), viewedId);
        } else {
            final ProfileStore.Look one = account.look(viewedId);
            looks.show(one == null ? List.of() : List.of(one), false, viewedId);
        }
        final ProfileStore.Look worn = account.worn();
        looks.worn(worn == null ? "" : worn.id());
    }

    private void view(final int index) {
        if (looks == null) return;
        looks.view(index);
        final LookStage.Stand st = looks.viewedStand();
        viewingBlank = st != null && st.look == null;
        viewedId = st == null || st.look == null ? viewedId : st.look.id();
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        if (looks == null || looks.stage.isClosed()) {
            looks = new LookStage(this, overhaul);
            looks.onPick(i -> { if (looks != null && i == looks.viewed()) { if (!editing) edit(true); } else view(i); });
            if (viewedId == null && !account.looks().isEmpty()) viewedId = account.looks().get(0).id();
            fillStage();
        }
        if (startWith != null) {
            final Slot s = startWith;
            startWith = null;
            if (viewed() != null) {
                editing = true;
                slot = s;
                looks.edit(true);
                looks.focus(closeUp ? s : null);
            }
        }
        bio = null;
        if (!overhaul) buildLists();
        else if (editing) buildEdit();
        else buildBrowse();
    }

    private void stageWidget(final Rect r) {
        stageRect = r;
        if (looks == null) return;
        looks.shape(r.w() / (float) Math.max(1, r.h()));
        final StageWidget view = new StageWidget(r.x(), r.y(), r.w(), r.h(), looks.stage, getTitle());
        addRenderableOnly(view);
        // Asked last: the buttons lying over the stage take their own clicks.
        deferred = view;
    }

    @Nullable private StageWidget deferred;

    private void finishStage() {
        if (deferred != null) addWidget(deferred);
        deferred = null;
    }

    // ---- the row of looks

    private void buildBrowse() {
        final ProfileStore.Look look = viewed();
        final SlateSearchField search = new SlateSearchField(0, 0, width >= 520 ? 140 : 96, q -> {
            if (q.equals(query)) return;
            query = q;
            fillStage();
        });
        search.setValue(query);
        addHeaderAction(search);
        slashFocusTarget = search;

        // From the foot of the screen up: the profile's card, the buttons, the dots, what the look is made of, its
        // name. The stage has what is left, which is most of the screen.
        final int mid = width / 2;
        // Never so low that the bio has no line to itself.
        final int cardH = Mth.clamp(Math.round(height * 0.17f), 56, 78);
        final int cardY = height - PAD - cardH;
        final int by = cardY - 8 - SlateButton.HEIGHT;
        final int places = looks == null ? 0 : looks.stands().size();
        int y = by;
        if (height >= 280 && places >= 2 && places <= 24) {
            y -= 13;
            add(new LookDots(mid, y, places));
        }
        captionY = -1;
        if (height >= 300) {
            y -= 13;
            captionY = y;
        }
        nameScale = height >= 400 ? 2f : height >= 300 ? 1.5f : 1f;
        y -= Math.round(9 * nameScale) + 4;
        nameY = y;
        final int top = HEADER_H;
        final int stageH = Math.max(60, nameY - 3 - top);
        stageWidget(new Rect(0, top, width, stageH));
        final int arrow = height >= 330 ? 24 : 20;
        add(new SlateIconButton(PAD, top + stageH / 2 - arrow / 2, arrow, Icon.CHEVRON_LEFT, Component.translatable("slate_profile.previous"), () -> step(-1)));
        add(new SlateIconButton(width - PAD - arrow, top + stageH / 2 - arrow / 2, arrow, Icon.CHEVRON_RIGHT, Component.translatable("slate_profile.next"), () -> step(1)));

        if (look == null) {
            final int w = Math.min(150, width - PAD * 2);
            add(new SlateButton(mid - w / 2, by, w, Component.translatable("slate_profile.create"), this::create).variant(SlateButton.Variant.PRIMARY).icon(Icon.PLUS).enabled(own()));
        } else {
            final boolean worn = account.worn() != null && account.worn().id().equals(look.id());
            final int bw = Mth.clamp((width - PAD * 2 - 80) / 2, 56, 110);
            final SlateButton select = new SlateButton(mid - bw - 2, by, bw, Component.translatable(worn ? "slate_profile.worn" : "slate_profile.select"),
                () -> { ProfileClient.wear(worn ? null : look); if (looks != null) looks.worn(worn ? "" : look.id()); rebuildWidgets(); })
                .variant(worn ? SlateButton.Variant.SECONDARY : SlateButton.Variant.PRIMARY).icon(worn ? Icon.CHECK : null);
            select.tip(Component.translatable(!own() ? "slate_profile.not_signed_in" : worn ? "slate_profile.worn.tip" : "slate_profile.select.tip"));
            select.active = own();
            add(select);
            add(new SlateButton(mid + 2, by, bw, Component.translatable("slate_profile.edit"), () -> edit(true)).icon(Icon.EDIT));
            final int moreX = mid + bw + 6;
            add(new SlateIconButton(moreX, by, 20, Icon.DOTS, Component.translatable("slate_profile.more"), () -> lookMenu(look, moreX, by)));
        }
        profile(new Rect(PAD, cardY, width - PAD * 2, cardH));
        finishStage();
    }

    /** What a look is made of, in a line: the things it has; its skin, when it has none. */
    private Component madeOf(@Nullable final ProfileStore.Look look) {
        if (look == null) return Component.translatable("slate_profile.new_look.caption");
        final List<Cosmetic> things = look.items();
        if (things.isEmpty()) return thingName(Slot.SKIN, look.skin());
        final MutableComponent line = Component.empty();
        for (int k = 0; k < things.size(); k++) {
            if (k > 0) line.append(" \u00B7 ");
            line.append(things.get(k).name());
        }
        return line;
    }

    /**
     * A dot for every place of the row, the one in view marked: where in the row the view is, and a way straight to
     * any place. The look that is worn has a ring; the empty place at the end is a plus.
     */
    private final class LookDots extends SlateWidget {
        private static final int STEP = 9;
        private final int count;

        LookDots(final int centreX, final int y, final int count) {
            super(centreX - count * STEP / 2, y, count * STEP, 9, Component.translatable("slate_profile.places"));
            this.count = count;
        }

        @Override
        public void onClick(final double mouseX, final double mouseY) {
            super.onClick(mouseX, mouseY);
            view(Mth.clamp((int) ((mouseX - getX()) / STEP), 0, count - 1));
        }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            draw(g, mouseX, false);
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            draw(g, mouseX, true);
        }

        private void draw(final GuiGraphics g, final int mouseX, final boolean van) {
            final float a = effectiveAlpha();
            if (a <= 0.004f || looks == null) return;
            final Palette p = Theme.current().palette();
            final int lit = van ? 0xFFFFFFFF : p.accent(), dim = van ? 0xFF909090 : p.textDim(), over = van ? 0xFFE0E0E0 : p.textMuted();
            final List<LookStage.Stand> stands = looks.stands();
            final ProfileStore.Look worn = account.worn();
            final int y = getY() + enterOffset() + 4;
            for (int k = 0; k < count && k < stands.size(); k++) {
                final int cx = getX() + k * STEP + STEP / 2;
                final boolean here = k == looks.viewed();
                final boolean pointed = isHovered() && mouseX >= getX() + k * STEP && mouseX < getX() + (k + 1) * STEP;
                final int ink = Colors.scaleAlpha(here ? lit : pointed ? over : dim, a);
                final ProfileStore.Look of = stands.get(k).look;
                if (of == null) {
                    SlateDraw.rect(g, cx - 2, y, 5, 1, ink);
                    SlateDraw.rect(g, cx, y - 2, 1, 5, ink);
                } else if (here) {
                    SlateDraw.pixelCircle(g, cx, y, 2, ink);
                } else if (worn != null && worn.id().equals(of.id())) {
                    SlateDraw.pixelRing(g, cx, y, 2, Colors.scaleAlpha(lit, a));
                } else {
                    SlateDraw.rect(g, cx - 1, y - 1, 3, 3, ink);
                }
            }
        }
    }

    private void step(final int by) {
        if (looks == null || looks.stands().isEmpty()) return;
        view(looks.viewed() + by);
    }

    private void create() {
        if (!own()) return;
        final int n = account.data().looks.size() + 1;
        final ProfileStore.Look made = account.create(Component.translatable("slate_profile.look_n", n).getString(), "account",
            SkinComposer.accountSlim(account.uuid(), account.name()));
        viewedId = made.id();
        viewingBlank = false;
        query = "";
        fillStage();
        edit(true);
    }

    private void lookMenu(final ProfileStore.Look look, final int x, final int y) {
        final List<MenuPopup.Item> items = new ArrayList<>();
        items.add(MenuPopup.Item.of(Component.translatable("slate_profile.rename"), Icon.EDIT, () ->
            SlateModal.prompt(Component.translatable("slate_profile.rename"), null, look.name(), name -> {
                if (!name.isBlank()) look.name(name.trim());
                fillStage();
                rebuildWidgets();
            })));
        items.add(MenuPopup.Item.of(Component.translatable("slate_profile.duplicate"), Icon.DUPLICATE, () -> {
            final ProfileStore.Look copy = account.duplicate(look, Component.translatable("slate_profile.copy_of", look.name()).getString());
            viewedId = copy.id();
            fillStage();
            rebuildWidgets();
        }));
        items.add(MenuPopup.Item.sep());
        items.add(MenuPopup.Item.danger(Component.translatable("slate_profile.delete"), Icon.TRASH, () ->
            SlateModal.confirmDanger(Component.translatable("slate_profile.delete"), Component.translatable("slate_profile.delete.body", look.name()),
                Component.translatable("slate_profile.delete"), () -> {
                    account.remove(look);
                    if (own()) ProfileClient.changed();
                    viewedId = account.looks().isEmpty() ? null : account.looks().get(0).id();
                    fillStage();
                    rebuildWidgets();
                })));
        SlateContextMenu.open(x, y - 4, items);
    }

    // ---- one look, edited

    private void edit(final boolean on) {
        if (on && viewed() == null) return;
        editing = on;
        if (!on) slot = null;
        if (looks != null) {
            looks.edit(on);
            looks.focus(on && closeUp ? slot : null);
            final LookStage.Stand st = looks.viewedStand();
            if (st != null) st.node.tryOn(null);
        }
        rebuildWidgets();
    }

    private void choose(final Slot s) {
        slot = s;
        thingQuery = "";
        if (looks != null) looks.focus(closeUp ? s : null);
        rebuildWidgets();
    }

    private void buildEdit() {
        final ProfileStore.Look look = viewed();
        if (look == null) { editing = false; buildBrowse(); return; }
        addHeaderAction(new SlateButton(0, 0, 64, Component.translatable("gui.done"), () -> edit(false)).variant(SlateButton.Variant.PRIMARY).icon(Icon.CHECK));

        // Two columns of slots, each under its heading where there is room for one; a slot that is high enough says
        // what is in it on a line of its own, so nothing has to be cut short.
        final int colW = Mth.clamp(Math.round(width * 0.24f), 78, 170);
        final int box = Shelf.boxFor(height);
        final int shelfY = height - PAD - (box + Shelf.LABEL) + 2;
        groupsY = height >= 300 ? HEADER_H + 6 : -1;
        groupsW = colW;
        final int top = groupsY >= 0 ? groupsY + 13 : HEADER_H + 5;
        final int rows = Math.max(Slot.body().size(), Slot.wear().size());
        final int rowH = Mth.clamp((shelfY - 6 - top) / rows - 2, 15, 30);
        int y = top;
        for (final Slot s : Slot.body()) { add(new SlotButton(PAD, y, colW, rowH, s, look)); y += rowH + 2; }
        y = top;
        for (final Slot s : Slot.wear()) { add(new SlotButton(width - PAD - colW, y, colW, rowH, s, look)); y += rowH + 2; }

        // Between the two columns the player; under the player the search, and for the skin the choice of model.
        final int sx = PAD + colW + 6, sw = width - 2 * (PAD + colW + 6);
        final int searchY = shelfY - 25;
        stageWidget(new Rect(sx, HEADER_H + 1, sw, Math.max(40, searchY - 3 - (HEADER_H + 1))));
        add(new SlateIconButton(sx + sw - 22, HEADER_H + 5, 18, Icon.SEARCH, Component.translatable(closeUp ? "slate_profile.zoom.off" : "slate_profile.zoom.on"), () -> {
            closeUp = !closeUp;
            if (looks != null) looks.focus(closeUp ? slot : null);
            rebuildWidgets();
        }).toggled(closeUp));
        final boolean skin = slot == Slot.SKIN;
        final int modelW = skin ? Math.min(96, sw / 2 - 2) : 0;
        final int fieldW = Math.min(150, sw - modelW - (skin ? 4 : 0));
        final int fx = sx + (sw - fieldW - modelW - (skin ? 4 : 0)) / 2;
        final SlateSearchField search = new SlateSearchField(fx, searchY, fieldW, q -> {
            if (q.equals(thingQuery)) return;
            thingQuery = q;
            fillShelf(look);
        });
        search.setValue(thingQuery);
        add(search);
        slashFocusTarget = search;
        if (skin) {
            add(new SlateSegmented<>(fx + fieldW + 4, searchY, modelW, List.of(Boolean.FALSE, Boolean.TRUE), look.slim(),
                slim -> Component.translatable(slim ? "slate_profile.model.slim" : "slate_profile.model.wide"),
                slim -> { look.slim(slim); touched(look); fillShelf(look); }));
        }

        if (shelf != null) shelf.close();
        shelf = add(new Shelf(this, PAD, shelfY, width - PAD * 2, box, e -> pick(look, e), e -> {
            final LookStage.Stand st = looks == null ? null : looks.viewedStand();
            if (st != null) st.node.tryOn(e == null ? null : e.cosmetic);
        }));
        fillShelf(look);
        finishStage();
    }

    private void fillShelf(final ProfileStore.Look look) {
        if (shelf == null) return;
        final List<Shelf.Entry> out = new ArrayList<>();
        final String q = thingQuery.trim().toLowerCase(Locale.ROOT);
        if (slot == null) {
            shelf.show(out);
            return;
        }
        if (slot == Slot.SKIN) {
            final Shelf.Entry bring = Shelf.sign("upload", Component.translatable("file".equals(look.skin()) ? "slate_profile.skin.yours" : "slate_profile.skin.upload"), Icon.UPLOAD);
            bring.chosen = "file".equals(look.skin());
            out.add(bring);
            final ResourceLocation own = Minecraft.getInstance().getSkinManager()
                .getInsecureSkin(new com.mojang.authlib.GameProfile(account.uuid(), account.name().isEmpty() ? "?" : account.name())).texture();
            final Shelf.Entry acc = Shelf.skin("account", Component.translatable("slate_profile.skin.account"), own, look.slim());
            acc.chosen = "account".equals(look.skin());
            out.add(acc);
            for (final String name : ProfileStore.Look.DEFAULTS) {
                final Component label = Component.literal(Character.toUpperCase(name.charAt(0)) + name.substring(1));
                if (!q.isEmpty() && !label.getString().toLowerCase(Locale.ROOT).contains(q)) continue;
                final Shelf.Entry e = Shelf.skin("default:" + name, label, SkinComposer.defaultSkin(name, look.slim()), look.slim());
                e.chosen = ("default:" + name).equals(look.skin());
                out.add(e);
            }
        } else {
            final Cosmetic in = look.in(slot);
            final Shelf.Entry none = Shelf.sign("none", Component.translatable("slate_profile.slot." + slot.key() + ".none"), Icon.CLOSE);
            none.chosen = in == null;
            out.add(none);
            for (final Cosmetic c : Cosmetics.of(slot)) {
                if (!q.isEmpty() && !c.name().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
                final Shelf.Entry e = c.isModel() ? Shelf.thing(c) : Shelf.painted(c, look.slim());
                e.chosen = in == c;
                out.add(e);
            }
        }
        shelf.show(out);
    }

    private void pick(final ProfileStore.Look look, final Shelf.Entry e) {
        if (slot == null) return;
        if (slot == Slot.SKIN) {
            if ("upload".equals(e.id)) { bringSkin(look); return; }
            look.skin(e.id);
            if ("account".equals(e.id)) look.slim(SkinComposer.accountSlim(account.uuid(), account.name()));
        } else {
            look.put(slot, e.cosmetic);
        }
        touched(look);
        fillShelf(look);
    }

    /** Something about a look changed: if it is the one worn, the player is dressed again. */
    private void touched(final ProfileStore.Look look) {
        final ProfileStore.Look worn = account.worn();
        if (own() && worn != null && worn.id().equals(look.id())) ProfileClient.changed();
    }

    private void bringSkin(final ProfileStore.Look look) {
        if (!FilePicker.available()) {
            SlateToasts.show(Component.translatable("slate_profile.skin.upload"), Component.translatable("slate_profile.skin.windowed"), Icon.WARNING);
            return;
        }
        FilePicker.pickFile(Component.translatable("slate_profile.skin.upload").getString(), List.of("*.png"), "Minecraft skins (PNG)", path -> {
            // A skin is 64 by 64 (or the old 64 by 32): anything else is not one.
            boolean fits = false;
            try (InputStream in = Files.newInputStream(path); NativeImage image = NativeImage.read(in)) {
                fits = image.getWidth() == 64 && (image.getHeight() == 64 || image.getHeight() == 32);
            } catch (final Exception ignored) {}
            if (!fits || !look.upload(path)) {
                SlateToasts.show(Component.translatable("slate_profile.skin.upload"), Component.translatable("slate_profile.skin.not_a_skin"), Icon.ERROR);
                return;
            }
            touched(look);
            if (slot == Slot.SKIN) fillShelf(look);
        });
    }

    /** One slot in the edit view: what it is, what is in it; chosen, it shows its things in the row below. */
    private final class SlotButton extends SlateWidget {
        private final Slot of;
        private final ProfileStore.Look look;

        SlotButton(final int x, final int y, final int w, final int h, final Slot of, final ProfileStore.Look look) {
            super(x, y, w, h, slotName(of));
            this.of = of;
            this.look = look;
        }

        @Override
        public void onClick(final double mouseX, final double mouseY) {
            super.onClick(mouseX, mouseY);
            choose(of);
        }

        private Component holds() {
            if (of == Slot.SKIN) {
                final String s = look.skin();
                if (s.startsWith("default:")) return Component.literal(Character.toUpperCase(s.charAt(8)) + s.substring(9));
                return Component.translatable("file".equals(s) ? "slate_profile.skin.yours" : "slate_profile.skin.account");
            }
            final Cosmetic c = look.in(of);
            return c == null ? Component.translatable("slate_profile.empty") : c.name();
        }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            draw(g, false);
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            draw(g, true);
        }

        private void draw(final GuiGraphics g, final boolean van) {
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final float a = effectiveAlpha();
            if (a <= 0.004f) return;
            final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
            final boolean sel = of == slot;
            final float hv = hover();
            final boolean filled = of == Slot.SKIN || look.in(of) != null;
            if (van) {
                g.fill(x, y, x + w, y + h, Colors.scaleAlpha(sel ? 0x90FFFFFF : Colors.lerp(0x60000000, 0x80303030, hv), a));
                SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(sel ? 0xFFFFFFFF : 0xFF000000, a), 0);
            } else {
                SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(Colors.withAlpha(p.surface(), 0xB0), Colors.withAlpha(p.surfaceHover(), 0xE8), hv), a), t.radius());
                if (sel) SlateDraw.hgradient(g, x + 1, y + 1, w - 2, h - 2, Colors.scaleAlpha(Colors.withAlpha(p.accent(), 0x58), a), Colors.withAlpha(p.accent(), 0));
                SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(sel ? p.accent() : Colors.lerp(Colors.withAlpha(p.border(), 0xB0), p.borderStrong(), hv), a), t.radius());
                if (sel) SlateDraw.rect(g, x + 1, y + 3, 2, h - 6, Colors.scaleAlpha(p.accent(), a));
            }
            final var font = SlateDraw.font();
            final int ink = van ? (sel ? 0xFF202020 : 0xFFFFFFFF) : p.text();
            final Component name = getMessage();
            final Component holds = holds();
            if (h >= 26) {
                // The slot, and under it what is in it: a line each, and a mark at the side when it holds something.
                final int lines = y + (h - 19) / 2;
                g.drawString(font, SlateDraw.truncate(name, w - 20), x + 8, lines, Colors.scaleAlpha(ink, a), van && !sel);
                final int second = van ? (sel ? 0xFF404040 : 0xFFB0B0B0) : filled ? Colors.lerp(p.textMuted(), p.accent(), sel ? 0.6f : 0f) : p.textDim();
                g.drawString(font, SlateDraw.truncate(holds, w - 20), x + 8, lines + 11, Colors.scaleAlpha(second, a), false);
                if (filled && of != Slot.SKIN) SlateDraw.rect(g, x + w - 9, y + h / 2 - 1, 3, 3, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.accent(), a));
                SlateDraw.focusRing(g, x, y, w, h, focus() * a);
                return;
            }
            final int nameW = font.width(name);
            final int ty = y + (h - 8) / 2;
            g.drawString(font, SlateDraw.truncate(name, w - 12), x + 7, ty, Colors.scaleAlpha(ink, a), van && !sel);
            // What is in it, where there is room beside the name.
            final int room = w - 7 - nameW - 12;
            if (room > 26) {
                final var cut = SlateDraw.truncate(holds, room);
                final int dim = van ? (sel ? 0xFF404040 : 0xFFB0B0B0) : filled ? p.textMuted() : p.textDim();
                g.drawString(font, cut, x + w - 6 - font.width(cut), ty, Colors.scaleAlpha(filled && !van ? Colors.lerp(dim, p.accent(), sel ? 0.5f : 0f) : dim, a), false);
            } else if (filled && of != Slot.SKIN) {
                SlateDraw.rect(g, x + w - 8, y + h / 2 - 1, 3, 3, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.accent(), a));
            }
            SlateDraw.focusRing(g, x, y, w, h, focus() * a);
        }
    }

    // ---- the Custom layout: lists

    private void buildLists() {
        final ProfileStore.Look look = account.look(viewedId);
        final int top = HEADER_H + 6;
        final int profileH = Mth.clamp(Math.round(height * 0.24f), 46, 70);
        final int bodyH = height - top - PAD - profileH - 6;
        final int listW = Mth.clamp(Math.round(width * 0.26f), 90, 150);

        // Down the left: the looks, and the button that makes a new one.
        final SlateList<ProfileStore.Look> list = new SlateList<ProfileStore.Look>(PAD, top, listW, Math.max(20, bodyH - 24), 22,
            (g, item, index, x, y, w, h, hovered, selected, mx, my) -> {
                final Theme t = Theme.current();
                final boolean worn = account.worn() != null && account.worn().id().equals(item.id());
                Icons.draw(g, worn ? Icon.CHECK : Icon.USER, x + 5, y + (h - 12) / 2, 12, worn ? t.accent() : t.muted());
                g.drawString(font, SlateDraw.truncate(Component.literal(item.name()), w - 26), x + 21, y + (h - 8) / 2, t.isVanilla() ? 0xFFFFFFFF : t.text(), t.isVanilla());
            }).gap(2).emptyText(Component.translatable("slate_profile.no_looks"));
        final List<ProfileStore.Look> all = account.looks();
        list.items(all);
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(viewedId)) list.select(i);
        list.onSelect(l -> { viewedId = l.id(); fillStage(); rebuildWidgets(); });
        add(list);
        add(new SlateButton(PAD, top + bodyH - 20, listW, Component.translatable("slate_profile.create"), () -> {
            if (!own()) return;
            final ProfileStore.Look made = account.create(Component.translatable("slate_profile.look_n", account.data().looks.size() + 1).getString(), "account",
                SkinComposer.accountSlim(account.uuid(), account.name()));
            viewedId = made.id();
            fillStage();
            rebuildWidgets();
        }).icon(Icon.PLUS).enabled(own()));

        // In the middle the look; to its right a row per slot.
        final int slotsW = Mth.clamp(Math.round(width * 0.36f), 120, 220);
        final int px = PAD + listW + 6, pw = width - PAD - slotsW - 6 - px;
        stageWidget(new Rect(px, top, Math.max(40, pw), Math.max(40, bodyH - 24)));
        if (look != null) {
            final boolean worn = account.worn() != null && account.worn().id().equals(look.id());
            final int bw = Math.max(40, (pw - 24) / 2);
            final SlateButton select = new SlateButton(px, top + bodyH - 20, bw, Component.translatable(worn ? "slate_profile.worn" : "slate_profile.select"),
                () -> { ProfileClient.wear(worn ? null : look); fillStage(); rebuildWidgets(); })
                .variant(worn ? SlateButton.Variant.SECONDARY : SlateButton.Variant.PRIMARY).icon(worn ? Icon.CHECK : null);
            select.active = own();
            add(select);
            final int moreX = px + pw - 20;
            add(new SlateIconButton(moreX, top + bodyH - 20, 20, Icon.DOTS, Component.translatable("slate_profile.more"), () -> lookMenu(look, moreX, top + bodyH - 20)));

            final SlateScrollPanel panel = new SlateScrollPanel(width - PAD - slotsW, top, slotsW, bodyH);
            final int inner = panel.innerWidth();
            int y = 0;
            for (final Slot s : Slot.values()) {
                final List<String> ids = new ArrayList<>();
                if (s == Slot.SKIN) {
                    ids.add("account");
                    for (final String d : ProfileStore.Look.DEFAULTS) ids.add("default:" + d);
                    if ("file".equals(look.skin())) ids.add("file");
                } else {
                    ids.add("");
                    for (final Cosmetic c : Cosmetics.of(s)) ids.add(c.id());
                }
                final String now = s == Slot.SKIN ? look.skin() : look.in(s) == null ? "" : look.in(s).id();
                final int labelW = Math.min(64, inner / 3);
                panel.add(new dev.fallingcloud.slate.core.widget.SlateLabel(0, 0, labelW, slotName(s)).style(dev.fallingcloud.slate.core.widget.SlateLabel.Style.MUTED), 0, y + 6);
                final int bring = s == Slot.SKIN ? 24 : 0;
                panel.add(new SlateDropdown<>(0, 0, inner - labelW - 4 - bring, ids, now, id -> thingName(s, id), id -> {
                    if (s == Slot.SKIN) {
                        if (!"file".equals(id)) look.skin(id);
                    } else look.put(s, Cosmetics.get(id));
                    touched(look);
                }), labelW + 4, y);
                if (s == Slot.SKIN) {
                    panel.add(new SlateIconButton(0, 0, 20, Icon.UPLOAD, Component.translatable("slate_profile.skin.upload"), () -> bringSkin(look)), inner - 20, y);
                }
                y += 23;
            }
            panel.setContentHeight(y);
            add(panel);
        }
        profile(new Rect(PAD, height - PAD - profileH, width - PAD * 2, profileH));
        finishStage();
    }

    private static Component thingName(final Slot s, final String id) {
        if (s == Slot.SKIN) {
            if (id.startsWith("default:")) return Component.literal(Character.toUpperCase(id.charAt(8)) + id.substring(9));
            return Component.translatable("file".equals(id) ? "slate_profile.skin.yours" : "slate_profile.skin.account");
        }
        final Cosmetic c = Cosmetics.get(id);
        return c == null ? Component.translatable("slate_profile.slot." + s.key() + ".none") : c.name();
    }

    // ------------------------------------------------------------------ the profile: account, picture, bio

    /** The profile's card: the picture down its left, beside it the account and under that the bio. */
    private void profile(final Rect r) {
        cardRect = r;
        addRenderableOnly((Renderable) (g, mouseX, mouseY, partialTick) -> SlateDraw.floatingPanel(g, r.x(), r.y(), r.w(), r.h(), 1f));
        final int pad = r.h() >= 60 ? 7 : 5;
        final int box = r.h() - pad * 2;
        add(new PictureBox(r.x() + pad, r.y() + pad, box));
        final int x = r.x() + pad + box + 8, w = r.right() - pad - x;
        // Whose profile this is: everyone who has played on this PC is in the list.
        final List<ProfileStore.KnownAccount> known = ProfileStore.known();
        ProfileStore.KnownAccount current = null;
        for (final ProfileStore.KnownAccount k : known) if (k.uuid.equalsIgnoreCase(account.uuid().toString())) current = k;
        final int labelW = Math.min(font.width(Component.translatable("slate_profile.account")) + 6, w / 2);
        final SlateDropdown<ProfileStore.KnownAccount> accounts = new SlateDropdown<>(x + labelW, r.y() + pad, Math.min(170, w - labelW), known, current,
            k -> Component.literal(k.name.isEmpty() ? k.uuid : k.name), k -> {
                try {
                    account = ProfileStore.account(UUID.fromString(k.uuid), k.name);
                } catch (final IllegalArgumentException e) {
                    return;
                }
                final ProfileStore.Look worn = account.worn();
                viewedId = worn != null ? worn.id() : account.looks().isEmpty() ? null : account.looks().get(0).id();
                viewingBlank = false;
                editing = false;
                slot = null;
                picture = null;
                pictureOf = null;
                if (looks != null) looks.edit(false);
                fillStage();
                rebuildWidgets();
            });
        accounts.tip(Component.translatable("slate_profile.account.tip"));
        add(accounts);
        // The bio has the card down to its foot: its end is level with the picture's.
        final int bioY = r.y() + pad + 23, bioH = r.bottom() - pad - bioY;
        if (bioH >= 18) {
            final MultiLineEditBox box2 = new BioBox(x, bioY, w, bioH);
            box2.setCharacterLimit(240);
            box2.setValue(account.bio());
            box2.setValueListener(text -> bioDirty = true);
            bio = add(box2);
        }
    }

    /** The game's own box for text of several lines, in the frame of the style that is on. */
    private final class BioBox extends MultiLineEditBox {

        BioBox(final int x, final int y, final int w, final int h) {
            super(ProfileScreen.this.font, x, y, w, h, Component.translatable("slate_profile.bio.hint"), Component.translatable("slate_profile.bio"));
        }

        @Override
        protected void renderBackground(final GuiGraphics g) {
            final Theme t = Theme.current();
            if (t.isVanilla()) { super.renderBackground(g); return; }
            final Palette p = t.palette();
            SlateDraw.pixelRound(g, getX(), getY(), getWidth(), getHeight(), Colors.withAlpha(p.bg2(), 0xF0), t.radius());
            SlateDraw.outline(g, getX(), getY(), getWidth(), getHeight(), isFocused() ? p.accent() : isHovered() ? p.borderStrong() : p.border(), t.radius());
        }

        /**
         * The game writes how much has been typed under the box, which here is under the card and, on a small window,
         * under the screen. The screen writes it on the card instead; of the game's own decorations only the scroll
         * bar is let through.
         */
        @Override
        protected void renderDecorations(final GuiGraphics g) {
            g.enableScissor(getX(), getY(), getX() + getWidth() + 9, getY() + getHeight());
            super.renderDecorations(g);
            g.disableScissor();
        }
    }

    /** The profile picture: the one chosen, or the face of the look that is worn. A click changes it. */
    private final class PictureBox extends SlateWidget {

        PictureBox(final int x, final int y, final int size) {
            super(x, y, size, size, Component.translatable("slate_profile.picture"));
            tip(Component.translatable("slate_profile.picture.tip"));
        }

        @Override
        public void onClick(final double mouseX, final double mouseY) {
            super.onClick(mouseX, mouseY);
            final List<MenuPopup.Item> items = new ArrayList<>();
            items.add(MenuPopup.Item.of(Component.translatable("slate_profile.picture.choose"), Icon.IMAGE, () -> {
                if (!FilePicker.available()) return;
                FilePicker.pickFile(Component.translatable("slate_profile.picture.choose").getString(), List.of("*.png", "*.jpg", "*.jpeg"), "Pictures", path -> {
                    account.picture(path);
                    picture = null;
                    pictureOf = null;
                });
            }));
            items.add(MenuPopup.Item.of(Component.translatable("slate_profile.picture.face"), Icon.USER, () -> {
                account.picture(null);
                picture = null;
                pictureOf = null;
            }));
            SlateContextMenu.open(getX(), getY() + getHeight() + 2, items);
        }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            draw(g, false);
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            draw(g, true);
        }

        private void draw(final GuiGraphics g, final boolean van) {
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final float a = effectiveAlpha();
            if (a <= 0.004f) return;
            final int x = getX(), y = getY() + enterOffset(), s = getWidth();
            SlateDraw.shadow(g, x, y, s, s, 0.4f * a);
            SlateDraw.pixelRound(g, x, y, s, s, Colors.scaleAlpha(van ? 0xFF101010 : p.bg2(), a), van ? 0 : t.radius());
            final Path chosen = account.picture();
            if (chosen != null) {
                if (!chosen.equals(pictureOf)) {
                    pictureOf = chosen;
                    picture = null;
                    Textures.invalidate(chosen);
                    Textures.load(chosen, loaded -> picture = loaded);
                }
                final Textures.Loaded pic = picture;
                if (pic != null) {
                    g.enableScissor(x + 1, y + 1, x + s - 1, y + s - 1);
                    SlateDraw.blitFit(g, pic.id(), x + 1, y + 1, s - 2, s - 2, pic.width(), pic.height(), true, a);
                    g.disableScissor();
                }
            } else {
                // The face of the look that is worn; of the account's own skin when none is.
                final AppliedLooks.Applied worn = own() ? AppliedLooks.of(account.uuid()) : null;
                final ResourceLocation skin = worn != null ? worn.texture() : DefaultPlayerSkin.get(account.uuid()).texture();
                SlateDraw.playerHead(g, skin, x + 3, y + 3, s - 6, a);
            }
            if (hover() > 0.01f) {
                SlateDraw.rect(g, x + 1, y + s - 13, s - 2, 12, Colors.scaleAlpha(0xC0000000, a * hover()));
                Icons.draw(g, Icon.EDIT, x + s / 2 - 5, y + s - 12, 10, Colors.scaleAlpha(0xFFFFFFFF, a * hover()));
            }
            SlateDraw.outline(g, x, y, s, s, Colors.scaleAlpha(van ? 0xFF000000 : Colors.lerp(p.border(), p.accent(), hover()), a), van ? 0 : t.radius());
            SlateDraw.focusRing(g, x, y, s, s, focus() * a);
        }
    }

    // ------------------------------------------------------------------ living

    @Override
    public void tick() {
        super.tick();
        if (bioDirty && ++bioTicks >= 20) saveBio();
    }

    private void saveBio() {
        bioDirty = false;
        bioTicks = 0;
        if (bio != null) account.bio(bio.getValue());
    }

    @Override
    public void back() {
        // Out of the edit view first; only then out of the screen.
        if (overhaul && editing) { edit(false); return; }
        super.back();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        final boolean typing = getFocused() instanceof net.minecraft.client.gui.components.EditBox || (bio != null && bio.isFocused())
            || (getFocused() instanceof net.minecraft.client.gui.components.events.ContainerEventHandler);
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (typing || !overhaul || editing) return false;
        if (keyCode == 263) { step(-1); return true; }
        if (keyCode == 262) { step(1); return true; }
        return false;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        // In the edit view a drag over the player turns them: nothing else on the stage takes a click there.
        if (overhaul && editing && button == 0 && stageRect.contains(mouseX, mouseY)) {
            turning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) {
        if (turning && looks != null) {
            looks.turnBy((float) dragX * 0.9f);
            turnedOnce = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        turning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (overhaul && !editing && scrollY != 0 && stageRect.contains(mouseX, mouseY)) {
            step(scrollY > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        if (bioDirty) saveBio();
        super.removed();
        Popups.closeAll();
        if (shelf != null) { shelf.close(); shelf = null; }
        if (looks != null) { looks.close(); looks = null; }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        final Theme t = Theme.current();
        if (!overhaul || t.isVanilla() || (minecraft != null && minecraft.level != null)) return;
        // A hall: black above, the floor catching a breath of the lamps.
        SlateDraw.vgradient(g, 0, HEADER_H, width, height - HEADER_H, 0xFF060709, 0xFF181614);
        SlateDraw.vignette(g, 0, HEADER_H, width, height - HEADER_H, 0.5f);
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        if (overhaul && !editing) {
            // Under the stage: the name of the look in view, large; that it is the one worn; what it is made of.
            final ProfileStore.Look look = viewed();
            final Component name = Fonts.heading(look == null ? Component.translatable("slate_profile.new_look") : Component.literal(look.name()));
            final boolean worn = look != null && account.worn() != null && account.worn().id().equals(look.id());
            final Component badge = Component.translatable("slate_profile.worn");
            final int badgeW = worn ? font.width(badge) + 8 + 6 : 0;
            final var cut = SlateDraw.truncate(name, Math.round((width - PAD * 2 - 80 - badgeW) / nameScale));
            final int nameW = Math.round(font.width(cut) * nameScale);
            final int nx = width / 2 - (nameW + badgeW) / 2;
            g.pose().pushPose();
            g.pose().translate(nx, nameY, 0f);
            g.pose().scale(nameScale, nameScale, 1f);
            g.drawString(font, cut, 0, 0, van ? 0xFFFFFFFF : p.text(), van);
            g.pose().popPose();
            if (worn) SlateDraw.chip(g, badge, nx + nameW + 6, nameY + Math.round((9 * nameScale - 12) / 2f), van ? 0xFFFFFFFF : p.accent(), 1f);
            if (captionY >= 0) {
                final var made = SlateDraw.truncate(madeOf(look), width - PAD * 2 - 80);
                g.drawString(font, made, width / 2 - font.width(made) / 2, captionY, van ? 0xFFC0C0C0 : p.textMuted(), van);
            }
        }
        if (overhaul && editing && groupsY >= 0) {
            // What the two columns are: what the player is made of, and what the player wears.
            SlateDraw.sectionRule(g, Component.translatable("slate_profile.group.body"), PAD, groupsY, groupsW, 1f);
            SlateDraw.sectionRule(g, Component.translatable("slate_profile.group.wear"), width - PAD - groupsW, groupsY, groupsW, 1f);
        }
        if (overhaul && editing && !van && !inWorld() && stageRect.h() >= 60) {
            // Up close the player is cut off where the view ends: the cut goes soft, into the hall's own dark.
            final int fy = stageRect.bottom() - 12;
            final int dark = Colors.lerp(0xFF060709, 0xFF181614, Mth.clamp((stageRect.bottom() - HEADER_H) / (float) Math.max(1, height - HEADER_H), 0f, 1f));
            SlateDraw.vgradient(g, stageRect.x(), fy, stageRect.w(), 12, Colors.withAlpha(dark, 0), dark);
        }
        if (overhaul && editing && !turnedOnce && stageRect.h() >= 90 && stageRect.w() >= 260) {
            // Until it has been done once: that the player can be turned.
            final Component hint = Component.translatable("slate_profile.turn.hint");
            Icons.draw(g, Icon.REFRESH, stageRect.x() + 5, stageRect.y() + 6, 10, van ? 0xFFC0C0C0 : p.textDim());
            g.drawString(font, hint, stageRect.x() + 18, stageRect.y() + 7, van ? 0xFFC0C0C0 : p.textDim(), van);
        }
        if (!overhaul && account.look(viewedId) == null) {
            SlateDraw.textCentered(g, Component.translatable("slate_profile.no_looks"), stageRect.centerX(), stageRect.centerY() - 4, van ? 0xFFC0C0C0 : p.textMuted());
        }
        // The account's label, beside its list.
        if (!editing || !overhaul) {
            for (final var child : children()) {
                if (child instanceof PictureBox box) {
                    g.drawString(font, Component.translatable("slate_profile.account"), box.getX() + box.getWidth() + 8, box.getY() + 6, van ? 0xFFC0C0C0 : p.textMuted(), van);
                    // How much of the bio's room is used, at the card's other end, while it is being written.
                    if (bio != null && bio.isFocused()) {
                        final int used = bio.getValue().length();
                        SlateDraw.textRight(g, Component.literal(used + " / 240"), cardRect.right() - 8, box.getY() + 6,
                            used >= 230 ? (van ? 0xFFFFAA00 : p.warning()) : van ? 0xFFC0C0C0 : p.textDim(), van);
                    }
                    if (!own()) {
                        final Component note = Component.translatable("slate_profile.not_signed_in");
                        final int nx = box.getX() + box.getWidth() + 8 + font.width(Component.translatable("slate_profile.account")) + 6 + 176;
                        if (nx + 40 < width - PAD) g.drawString(font, SlateDraw.truncate(note, width - PAD - nx), nx, box.getY() + 6, van ? 0xFFFFAA00 : p.warning(), van);
                    }
                }
            }
        }
        if (overhaul && editing && slot == null && shelf != null) {
            SlateDraw.textCentered(g, Component.translatable("slate_profile.choose_slot"), width / 2, shelf.getY() + shelf.getHeight() / 2 - 8, van ? 0xFFC0C0C0 : p.textMuted());
        }
    }
}
