package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.net.OpResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

/**
 * The text of {@link OpResult}s. The server sends a lang key plus string arguments; an argument written
 * {@code "lang:<key>"} is itself translated on the client (block names, tool names), so every line reads in the
 * player's language even from a dedicated server. The HUD shows {@link #describe}.
 *
 * <p>Keys: {@code slate_building.result.<verb>} for finished operations (args: count, missing, skipped), with a
 * {@code .short} variant when materials ran out and {@code .skipped} when positions were left alone;
 * {@code slate_building.result.stopped} (done, total) for cancelled ones; {@code slate_building.error.*} and
 * {@code slate_building.plan.*} for refusals ({@link #isError}).
 */
public final class OpMessages {

    /** Prefix of arguments that are lang keys. */
    public static final String LANG = "lang:";

    /** The line to show for {@code r}. */
    public static Component describe(final OpResult r) {
        final Object[] args = new Object[r.args().size()];
        for (int i = 0; i < args.length; i++) {
            final String a = r.args().get(i);
            args[i] = a.startsWith(LANG) ? Component.translatable(a.substring(LANG.length())) : a;
        }
        return Component.translatable(r.messageKey(), args);
    }

    /** Whether {@code r} reports a refused operation rather than a finished one. */
    public static boolean isError(final OpResult r) {
        return r.messageKey().startsWith("slate_building.error.") || r.messageKey().startsWith("slate_building.plan.")
            || r.messageKey().startsWith("slate_building.lock.");
    }

    /** A refusal: {@code reason} flattened into key + arguments (nested translations become {@code lang:} arguments). */
    public static OpResult error(final int op, final String mode, final @Nullable Component reason) {
        if (reason != null && reason.getContents() instanceof TranslatableContents tc) {
            final List<String> args = new ArrayList<>();
            for (final Object a : tc.getArgs()) args.add(arg(a));
            return new OpResult(op, mode, 0, 0, 0, tc.getKey(), args);
        }
        return new OpResult(op, mode, 0, 0, 0, "slate_building.plan.failed", List.of());
    }

    /**
     * A finished (or stopped) operation. {@code kind} is APPLY, UNDO or REDO; counts are positions placed into free
     * space, replaced, removed, left alone (changed since, protected, unsupported) and not paid for.
     */
    public static OpResult result(final int op, final BuildMode mode, final String kind, final int placed, final int replaced,
                                  final int removed, final int skipped, final int missing, final boolean stopped, final int done,
                                  final int total) {
        final int changed = placed + replaced + removed;
        if (stopped) {
            return new OpResult(op, mode.id(), placed + replaced, removed, skipped + missing, "slate_building.result.stopped",
                List.of(String.valueOf(changed), String.valueOf(total)));
        }
        final String base = "slate_building.result." + verb(mode, kind);
        final String key = changed == 0 && missing == 0 && skipped == 0 ? "slate_building.result.nothing"
            : missing > 0 ? base + ".short" : skipped > 0 ? base + ".skipped" : base;
        return new OpResult(op, mode.id(), placed + replaced, removed, skipped + missing, key,
            List.of(String.valueOf(changed), String.valueOf(missing), String.valueOf(skipped)));
    }

    private static String verb(final BuildMode mode, final String kind) {
        if ("UNDO".equals(kind)) return "undone";
        if ("REDO".equals(kind)) return "redone";
        return switch (mode.id()) {
            case "replace" -> "replaced";
            case "clear" -> "removed";
            case "reshape" -> "reshaped";
            case "paste" -> "pasted";
            case "cut" -> "cut";
            case "stack" -> "stacked";
            case "move" -> "moved";
            default -> "placed";
        };
    }

    private static String arg(final Object a) {
        if (a instanceof Component c) {
            return c.getContents() instanceof TranslatableContents tc && tc.getArgs().length == 0 ? LANG + tc.getKey() : c.getString();
        }
        return String.valueOf(a);
    }

    private OpMessages() {}
}
