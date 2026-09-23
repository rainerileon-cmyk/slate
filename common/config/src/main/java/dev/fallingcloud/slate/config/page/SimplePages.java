package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.resolver.CoreBindings;
import dev.fallingcloud.slate.config.resolver.Resolvers;
import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.module.Modules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The vanilla-option pages that are pure section lists: Chat and Online (Multiplayer tabs), Accessibility. */
public final class SimplePages {

    /** A Slate module's json fields as a section, or a hint row when the module has no file yet. */
    static Section slateModule(final String module, final String modId, final String titleKey) {
        final Section s = Section.of("slate_" + module, Component.translatable(titleKey));
        final List<OptionBinding> bs = Resolvers.slateModuleBindings(module);
        if (bs.isEmpty()) {
            s.add(Binding.of("slate:" + module + ":_none", OptionType.INFO, Component.translatable("slate_config.module.no_file"))
                .getter(() -> "config/slate/" + module + ".json").searchWords(modId));
        } else s.addAll(bs);
        return s;
    }

    /** Multiplayer › Chat: the chat box's look, how messages behave, Slate Chat's settings. */
    public static final class ChatPage extends OptionPageBase {
        public ChatPage() { super("chat", Component.translatable("slate_config.page.chat"), Icon.CHAT); }

        @Override
        protected List<Section> sections() {
            final List<Section> out = new ArrayList<>();
            out.add(Section.of("chat_box", Component.translatable("slate_config.chat.box"), VanillaOptions.all(
                "chatVisibility", "chatColors", "chatOpacity", "textBackgroundOpacity", "backgroundForChatOnly",
                "chatScale", "chatWidth", "chatHeightFocused", "chatHeightUnfocused", "chatLineSpacing")));
            out.add(Section.of("messages", Component.translatable("slate_config.chat.messages"), VanillaOptions.all(
                "chatLinks", "chatLinksPrompt", "chatDelay", "autoSuggestions", "hideMatchedNames", "onlyShowSecureChat", "notificationDisplayTime")));
            if (Modules.isLoaded("slate_chat")) out.add(slateModule("chat", "slate_chat", "slate_config.chat.slate"));
            return out;
        }
    }

    /** Multiplayer › Online: server list, Realms, telemetry, streaming privacy, Slate Multiplayer's settings. */
    public static final class OnlinePage extends OptionPageBase {
        public OnlinePage() { super("online", Component.translatable("slate_config.page.online"), Icon.WIFI); }

        @Override
        protected List<Section> sections() {
            final List<Section> out = new ArrayList<>();
            // hideMatchedNames / onlyShowSecureChat live on the Chat tab only.
            final Section online = Section.of("online", Component.translatable("slate_config.multiplayer.online"),
                VanillaOptions.all("realmsNotifications", "allowServerListing", "telemetryOptInExtra"));
            online.add(Binding.of("multiplayer:hide_server_address", OptionType.BOOLEAN, Component.translatable("slate_config.multiplayer.hide_address"))
                .tooltip(Component.translatable("slate_config.multiplayer.hide_address.tip"))
                .getter(() -> Minecraft.getInstance().options.hideServerAddress)
                .setter(v -> { Minecraft.getInstance().options.hideServerAddress = Boolean.TRUE.equals(v); Minecraft.getInstance().options.save(); })
                .def(Boolean.FALSE));
            online.add(Binding.of("multiplayer:skip_warning", OptionType.BOOLEAN, Component.translatable("slate_config.multiplayer.skip_warning"))
                .tooltip(Component.translatable("slate_config.multiplayer.skip_warning.tip"))
                .getter(() -> Minecraft.getInstance().options.skipMultiplayerWarning)
                .setter(v -> { Minecraft.getInstance().options.skipMultiplayerWarning = Boolean.TRUE.equals(v); Minecraft.getInstance().options.save(); })
                .def(Boolean.FALSE));
            out.add(online);
            if (Modules.isLoaded("slate_multiplayer")) out.add(slateModule("multiplayer", "slate_multiplayer", "slate_config.multiplayer.slate"));
            return out;
        }
    }

    /** Language &amp; Accessibility › Accessibility: reading aids, motion and effects, input helpers. */
    public static final class AccessibilityPage extends OptionPageBase {
        public AccessibilityPage() { super("accessibility", Component.translatable("slate_config.page.accessibility"), Icon.ACCESSIBILITY); }

        @Override
        protected List<Section> sections() {
            final List<Section> out = new ArrayList<>();
            // forceUnicodeFont lives on the Language tab (Font).
            out.add(Section.of("reading", Component.translatable("slate_config.accessibility.reading"), VanillaOptions.all(
                "narrator", "narratorHotkey", "showSubtitles", "highContrast", "textBackgroundOpacity", "backgroundForChatOnly", "chatOpacity",
                "chatLineSpacing", "chatDelay", "notificationDisplayTime", "menuBackgroundBlurriness")));
            final Section motion = Section.of("motion", Component.translatable("slate_config.accessibility.motion"), VanillaOptions.all(
                "screenEffectScale", "fovEffectScale", "darknessEffectScale", "damageTiltStrength", "glintSpeed", "glintStrength",
                "hideLightningFlashes", "bobView", "panoramaScrollSpeed", "darkMojangStudiosBackground", "hideSplashTexts"));
            motion.addAll(CoreBindings.all("motion", "transitions"));
            out.add(motion);
            out.add(Section.of("input", Component.translatable("slate_config.accessibility.input"), VanillaOptions.all("autoJump", "toggleSprint", "toggleCrouch")));
            return out;
        }
    }

    private SimplePages() {}
}
