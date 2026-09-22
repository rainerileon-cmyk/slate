package dev.fallingcloud.slate.chat.mixin;

import dev.fallingcloud.slate.chat.client.ChatChannels;
import dev.fallingcloud.slate.chat.client.ChatRenderState;
import dev.fallingcloud.slate.chat.client.ChatScreenUi;
import dev.fallingcloud.slate.chat.client.ChatSend;
import dev.fallingcloud.slate.chat.client.TypingIndicator;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat-screen entry points: Slate's chrome around vanilla's input, clicks on cards/actions/scrollbar,
 * image paste, Ctrl+F search, and DM routing of sent lines.
 *
 * <p>Extends {@link Screen} like Chatterbox's did, so overrides of the inherited mouse/char handlers can be
 * added (ChatScreen itself does not declare them, so they cannot be injected into). {@code render} is
 * replaced wholesale and re-issues vanilla's sequence (chat panel, input, widgets, suggestions at z 200,
 * tag tooltip / hover effect) with the Slate bar in between; {@code CommandSuggestions} is untouched.</p>
 */
@Mixin(value = ChatScreen.class, priority = 900)
public abstract class ChatScreenMixin extends Screen {

    @Shadow protected EditBox input;
    @Shadow CommandSuggestions commandSuggestions;

    @Shadow public abstract String normalizeChatMessage(String message);

    protected ChatScreenMixin(final Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void slate$init(final CallbackInfo ci) {
        ChatScreenUi.build((ChatScreen) (Object) this, this.input, this.width, this.height);
        ChatRenderState.markRead();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void slate$render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        final ChatScreenUi ui = ChatScreenUi.current();
        if (ui == null || ui.input() != this.input) return;                 // not built for this screen: vanilla path
        ui.render(g, mouseX, mouseY, partialTick, this.minecraft.gui.getGuiTicks());
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 200.0F);
        this.commandSuggestions.render(g, mouseX, mouseY);
        g.pose().popPose();
        final GuiMessageTag tag = this.minecraft.gui.getChat().getMessageTagAt(mouseX, mouseY);
        if (tag != null && tag.text() != null) {
            g.renderTooltip(this.font, this.font.split(tag.text(), 210), mouseX, mouseY);
        } else {
            final Style style = this.minecraft.gui.getChat().getClickedComponentStyleAt(mouseX, mouseY);
            if (style != null && style.getHoverEvent() != null) g.renderComponentHoverEffect(this.font, style, mouseX, mouseY);
        }
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void slate$mouseClicked(final double mouseX, final double mouseY, final int button, final CallbackInfoReturnable<Boolean> cir) {
        final ChatScreenUi ui = ChatScreenUi.current();
        if (ui != null && ui.mouseClicked(mouseX, mouseY, button)) cir.setReturnValue(true);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        final ChatScreenUi ui = ChatScreenUi.current();
        if (ui != null && ui.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) {
        final ChatScreenUi ui = ChatScreenUi.current();
        if (ui != null && ui.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void slate$keyPressed(final int keyCode, final int scanCode, final int modifiers, final CallbackInfoReturnable<Boolean> cir) {
        final ChatScreenUi ui = ChatScreenUi.current();
        if (ui != null && ui.keyPressed(keyCode, scanCode, modifiers)) cir.setReturnValue(true);
    }

    @Override
    public boolean charTyped(final char codePoint, final int modifiers) {
        final ChatScreenUi ui = ChatScreenUi.current();
        if (ui != null && ui.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    /** DM/group tabs route plain lines to the Multiplayer thread; commands always reach the server. */
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void slate$handleInput(final String message, final boolean addToRecentChat, final CallbackInfo ci) {
        TypingIndicator.stopped();
        if (!ChatChannels.isThread()) return;
        final String line = this.normalizeChatMessage(message);
        if (line.isEmpty() || line.startsWith("/")) return;
        ChatSend.text(line);
        ci.cancel();
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void slate$removed(final CallbackInfo ci) {
        TypingIndicator.stopped();
        ChatScreenUi.clear();
    }
}
