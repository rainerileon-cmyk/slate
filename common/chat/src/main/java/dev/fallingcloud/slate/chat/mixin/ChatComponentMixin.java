package dev.fallingcloud.slate.chat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.chat.client.ChatAccess;
import dev.fallingcloud.slate.chat.client.ChatChannels;
import dev.fallingcloud.slate.chat.client.ChatClient;
import dev.fallingcloud.slate.chat.client.ChatLayout;
import dev.fallingcloud.slate.chat.client.ChatMeta;
import dev.fallingcloud.slate.chat.client.ChatRenderState;
import dev.fallingcloud.slate.chat.client.ChatRenderer;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The HUD chat hook (Chatterbox's strategy, extended): meta + grouping decided while messages are wrapped,
 * Slate's own rows inserted around the wrapped text, rendering replaced at HEAD, scrolling and hit-tests
 * re-derived on the real row heights.
 *
 * <p><b>Row building.</b> {@code addMessageToDisplayQueue} wraps a message into lines and inserts each at
 * index 0 of {@code trimmedMessages}, so the wrap list's FIRST element ends up top-most and its LAST
 * bottom-most. Header/gap markers therefore go first, attachment rows last. This is also the resize path
 * ({@code refreshTrimmedMessages} re-runs it), so re-wrapping keeps everything without extra bookkeeping.</p>
 *
 * <p>Priority 900 applies this mixin before mods that wrap the whole render body (ImmediatelyFast,
 * chat_heads); nothing here captures locals, and vanilla's records are untouched.</p>
 */
@Mixin(value = ChatComponent.class, priority = 900)
public abstract class ChatComponentMixin implements ChatAccess {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;

    @Shadow public abstract int getWidth();
    @Shadow public abstract int getHeight();
    @Shadow public abstract double getScale();
    @Shadow public abstract boolean isChatFocused();
    @Shadow public abstract void rescaleChat();
    @Shadow protected abstract int getLineHeight();
    @Shadow protected abstract void addMessageToDisplayQueue(GuiMessage message);

    @Unique private GuiMessage slate$wrapping;
    @Unique private ChatMeta.Meta slate$wrappingMeta;
    @Unique private boolean slate$wrappingStart;

    // ------------------------------------------------------------------ ChatAccess

    @Override public List<GuiMessage.Line> slate$lines() { return this.trimmedMessages; }
    @Override public List<GuiMessage> slate$all() { return this.allMessages; }
    @Override public int slate$scroll() { return this.chatScrollbarPos; }
    @Override public void slate$setScroll(final int rows) { this.chatScrollbarPos = Math.max(0, rows); }
    @Override public boolean slate$newSinceScroll() { return this.newMessageSinceScroll; }
    @Override public void slate$setNewSinceScroll(final boolean flag) { this.newMessageSinceScroll = flag; }
    @Override public int slate$width() { return this.getWidth(); }
    @Override public int slate$height() { return this.getHeight(); }
    @Override public double slate$scale() { return this.getScale(); }
    @Override public int slate$lineHeight() { return this.getLineHeight(); }
    @Override public boolean slate$focused() { return this.isChatFocused(); }
    @Override public void slate$refresh() { this.rescaleChat(); }
    @Override public void slate$addToDisplay(final GuiMessage message) { this.addMessageToDisplayQueue(message); }

    // ------------------------------------------------------------------ size overrides (config beats the vanilla option)

    @Inject(method = "getWidth()I", at = @At("HEAD"), cancellable = true)
    private void slate$width(final CallbackInfoReturnable<Integer> cir) {
        final int w = ChatConfig.get().width;
        if (w > 0) cir.setReturnValue(Math.max(80, w));
    }

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void slate$height(final CallbackInfoReturnable<Integer> cir) {
        final ChatConfig cfg = ChatConfig.get();
        final int h = this.isChatFocused() ? cfg.height : cfg.heightUnfocused;
        if (h > 0) cir.setReturnValue(Math.max(20, h));
    }

    // ------------------------------------------------------------------ incoming

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"))
    private void slate$incoming(final Component message, final MessageSignature signature, final GuiMessageTag tag, final CallbackInfo ci) {
        ChatClient.onIncoming(message, tag, this.isChatFocused());
    }

    // ------------------------------------------------------------------ wrap step

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void slate$beforeWrap(final GuiMessage message, final CallbackInfo ci) {
        final ChatMeta.Meta meta = ChatMeta.bind(message);
        if (!ChatChannels.accepts(meta)) {
            this.slate$wrapping = null;
            ci.cancel();
            return;
        }
        this.slate$wrapping = message;
        this.slate$wrappingMeta = meta;
        this.slate$wrappingStart = ChatLayout.groupStart(meta);
    }

    /** The text vanilla lays out: sender prefix dropped, links shortened, emotes swapped. Wire text untouched. */
    @ModifyArg(method = "addMessageToDisplayQueue",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
        index = 0)
    private FormattedText slate$displayText(final FormattedText original) {
        if (this.slate$wrapping == null) return original;
        return ChatLayout.displayText(this.slate$wrapping, this.slate$wrappingMeta, this.slate$wrappingStart, ChatLayout.Geometry.of(this.getLineHeight()));
    }

    /** Text under a head is indented, so it wraps narrower. */
    @ModifyArg(method = "addMessageToDisplayQueue",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
        index = 1)
    private int slate$wrapWidth(final int width) {
        if (this.slate$wrapping == null) return width;
        return Math.max(20, width - ChatLayout.Geometry.of(this.getLineHeight()).indentFor(this.slate$wrappingMeta));
    }

    @ModifyExpressionValue(method = "addMessageToDisplayQueue",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"))
    private List<FormattedCharSequence> slate$rows(final List<FormattedCharSequence> wrapped) {
        final GuiMessage message = this.slate$wrapping;
        this.slate$wrapping = null;
        if (message == null) return wrapped;
        return ChatLayout.rows(message, this.slate$wrappingMeta, wrapped, this.slate$wrappingStart, ChatLayout.Geometry.of(this.getLineHeight()));
    }

    /**
     * Headers and cards eat lines: raise vanilla's 100-line cap. Chained modifiers rather than {@code @ModifyConstant}
     * (here and below): longer-chat-history mods modify these same constants, and a constant takes one
     * {@code @ModifyConstant} only.
     */
    @ModifyExpressionValue(method = "addMessageToDisplayQueue", at = @At(value = "CONSTANT", args = "intValue=100"))
    private int slate$maxLines(final int vanilla) {
        return Math.max(vanilla, ChatClient.maxLines());
    }

    /** More messages kept than vanilla's 100 (the history size). */
    @ModifyExpressionValue(method = "addMessageToQueue", at = @At(value = "CONSTANT", args = "intValue=100"))
    private int slate$maxMessages(final int vanilla) {
        return Math.max(vanilla, ChatConfig.get().historySize);
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"))
    private void slate$refreshStart(final CallbackInfo ci) {
        ChatLayout.resetGrouping();
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void slate$cleared(final boolean clearSentHistory, final CallbackInfo ci) {
        ChatLayout.resetGrouping();
        ChatMeta.clear();
        ChatRenderState.reset();
    }

    // ------------------------------------------------------------------ render + scroll + hit tests

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void slate$render(final GuiGraphics g, final int tickCount, final int mouseX, final int mouseY, final boolean focused, final CallbackInfo ci) {
        ChatRenderer.render(this, g, tickCount, mouseX, mouseY, focused);
        ci.cancel();
    }

    @Inject(method = "scrollChat", at = @At("HEAD"), cancellable = true)
    private void slate$scroll(final int amount, final CallbackInfo ci) {
        ChatLayout.scroll(this, amount);
        ci.cancel();
    }

    @Inject(method = "getClickedComponentStyleAt", at = @At("HEAD"), cancellable = true)
    private void slate$styleAt(final double mouseX, final double mouseY, final CallbackInfoReturnable<Style> cir) {
        cir.setReturnValue(this.isChatFocused() ? ChatRenderer.styleAt(mouseX, mouseY) : null);
    }

    @Inject(method = "getMessageTagAt", at = @At("HEAD"), cancellable = true)
    private void slate$tagAt(final double mouseX, final double mouseY, final CallbackInfoReturnable<GuiMessageTag> cir) {
        cir.setReturnValue(this.isChatFocused() ? ChatRenderer.tagAt(mouseX, mouseY) : null);
    }
}
