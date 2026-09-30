package dev.fallingcloud.slate.core.module;

import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * The static catalogue of every Slate module: id, name, icon, one-line pitch and where to get it. Unlike
 * {@link Modules}, which lists what is loaded, this also knows the modules that are <em>not</em> installed, so a
 * locked feature can say "Install Slate Profile to get this feature" and the settings tables can name the module a
 * menu slot is waiting for. Names come from Core's language file ({@code slate.module.<id>}).
 */
public final class KnownModules {

    public static final String CORE = "slate";
    /** The UI module (Slate Menu's id; shown as "Slate UI"): its presence enables the Overhaul layouts. */
    public static final String UI = "slate_menu";
    public static final String CONFIG = "slate_config";
    public static final String MULTIPLAYER = "slate_multiplayer";
    public static final String CHAT = "slate_chat";
    public static final String BUILDING = "slate_building";
    /** The player customization module (accounts, looks, cosmetics, profile). */
    public static final String PROFILE = "slate_profile";

    private static final String URL = "https://github.com/rainerileon-cmyk/slate";

    private static final List<KnownModule> ALL = List.of(
        entry(CORE, Icon.SLATE),
        entry(UI, Icon.HOME),
        entry(CONFIG, Icon.SLIDERS),
        entry(MULTIPLAYER, Icon.FRIENDS),
        entry(CHAT, Icon.CHAT),
        entry(BUILDING, Icon.BLOCK),
        entry(PROFILE, Icon.USER));

    private static KnownModule entry(final String id, final Icon icon) {
        return new KnownModule(id, Component.translatable("slate.module." + id), icon, Component.translatable("slate.module." + id + ".pitch"), URL);
    }

    /** Every module of the suite, Core first, in the order the settings tables show them. */
    public static List<KnownModule> all() { return ALL; }

    /** The feature modules: everything but Core. */
    public static List<KnownModule> features() { return ALL.subList(1, ALL.size()); }

    public static Optional<KnownModule> get(final String id) {
        for (final KnownModule m : ALL) if (m.id().equals(id)) return Optional.of(m);
        return Optional.empty();
    }

    /** The module's name, or its id for a module the catalogue does not know (another mod building on Core). */
    public static Component name(final String id) {
        return get(id).map(KnownModule::name).orElseGet(() -> Modules.get(id).map(SlateModule::displayName).orElse(Component.literal(id)));
    }

    /** Whether the module is loaded right now. */
    public static boolean installed(final String id) { return Modules.isLoaded(id); }

    private KnownModules() {}
}
