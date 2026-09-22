package dev.fallingcloud.slate.core.layout.editor;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Translation helper for the editor's own namespace ({@code assets/slate_editor/lang}). */
final class EditorText {

    static MutableComponent t(final String key) {
        return Component.translatable("slate_editor." + key);
    }

    static MutableComponent t(final String key, final Object... args) {
        return Component.translatable("slate_editor." + key, args);
    }

    private EditorText() {}
}
