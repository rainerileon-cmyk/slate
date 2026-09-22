package dev.fallingcloud.slate.config.file;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * A JSON (or json5-ish, read leniently) tree with RFC 6901 pointer access and a renderer that keeps
 * the file's indentation style and key order. Comments in json5 files are lost on save: Gson cannot
 * carry them, and the editor says so in its notice.
 */
public final class JsonDoc {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().setLenient().create();

    private JsonElement root;
    private final String indent;
    private final String newline;
    private final boolean hadComments;

    private JsonDoc(final JsonElement root, final String indent, final String newline, final boolean hadComments) {
        this.root = root;
        this.indent = indent;
        this.newline = newline;
        this.hadComments = hadComments;
    }

    public static JsonDoc parse(final String text) {
        final JsonReader r = new JsonReader(new StringReader(text));
        r.setLenient(true);
        final JsonElement root = JsonParser.parseReader(r);
        return new JsonDoc(root == null ? new JsonObject() : root, detectIndent(text), TextFile.lineSeparator(text), text.contains("//") || text.contains("/*"));
    }

    public static JsonDoc empty() {
        return new JsonDoc(new JsonObject(), "  ", "\n", false);
    }

    private static String detectIndent(final String text) {
        for (final String line : text.split("\\r?\\n")) {
            int i = 0;
            while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
            if (i > 0 && i < line.length() && (line.charAt(i) == '"' || line.charAt(i) == '\'' || Character.isLetter(line.charAt(i)))) return line.substring(0, i);
        }
        return "  ";
    }

    public JsonElement root() { return root; }

    public boolean hadComments() { return hadComments; }

    public String render() {
        final StringWriter sw = new StringWriter();
        final JsonWriter w = new JsonWriter(sw);
        w.setIndent(indent);
        w.setLenient(true);
        GSON.toJson(root, w);
        String s = sw.toString();
        if (!newline.equals("\n")) s = s.replace("\n", newline);
        return s + newline;
    }

    // ------------------------------------------------------------------ pointers

    /** Split an RFC 6901 pointer ({@code /a/b/0}) into unescaped tokens. A dotted path ({@code a.b}) also works. */
    public static List<String> tokens(final String pointer) {
        final List<String> out = new ArrayList<>();
        if (pointer == null || pointer.isEmpty() || pointer.equals("/")) return out;
        if (pointer.startsWith("/")) {
            for (final String t : pointer.substring(1).split("/", -1)) out.add(t.replace("~1", "/").replace("~0", "~"));
        } else {
            for (final String t : pointer.split("\\.")) out.add(t);
        }
        return out;
    }

    public static String pointerOf(final List<String> tokens) {
        final StringBuilder sb = new StringBuilder();
        for (final String t : tokens) sb.append('/').append(t.replace("~", "~0").replace("/", "~1"));
        return sb.toString();
    }

    @Nullable
    public JsonElement get(final String pointer) {
        JsonElement cur = root;
        for (final String t : tokens(pointer)) {
            if (cur == null) return null;
            if (cur.isJsonObject()) cur = cur.getAsJsonObject().get(t);
            else if (cur.isJsonArray()) {
                try {
                    final int i = Integer.parseInt(t);
                    final JsonArray a = cur.getAsJsonArray();
                    cur = i >= 0 && i < a.size() ? a.get(i) : null;
                } catch (final NumberFormatException e) { return null; }
            } else return null;
        }
        return cur;
    }

    /** Replace (or add, when the parent object exists) the element at {@code pointer}. */
    public boolean set(final String pointer, final JsonElement value) {
        final List<String> toks = tokens(pointer);
        if (toks.isEmpty()) { root = value; return true; }
        final JsonElement parent = get(pointerOf(toks.subList(0, toks.size() - 1)));
        final String last = toks.get(toks.size() - 1);
        if (parent == null) return false;
        if (parent.isJsonObject()) { parent.getAsJsonObject().add(last, value == null ? JsonNull.INSTANCE : value); return true; }
        if (parent.isJsonArray()) {
            try {
                final int i = Integer.parseInt(last);
                final JsonArray a = parent.getAsJsonArray();
                if (i >= 0 && i < a.size()) { a.set(i, value); return true; }
                if (i == a.size()) { a.add(value); return true; }
            } catch (final NumberFormatException ignored) {}
        }
        return false;
    }
}
