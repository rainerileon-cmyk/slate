package dev.fallingcloud.slate.building.ops;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * The current values of one {@link BuildMode}'s parameters: a typed map from parameter id to value, always complete
 * (every declared parameter has a value) and always valid (values pass {@link ModeParam#sanitize}). Travels in
 * {@code ApplyOp}/{@code SetSymmetry} as a {@link CompoundTag} and is persisted per mode in the client config as JSON.
 * Unknown keys from older/newer versions are ignored on read.
 *
 * <p>Skeleton: implemented fully (owner D1/D2 may add typed helpers).
 */
public final class ModeParams {

    private final BuildMode mode;
    private final Map<String, Object> values = new LinkedHashMap<>();

    /** Defaults of {@code mode}. */
    public ModeParams(final BuildMode mode) {
        this.mode = mode;
        for (final ModeParam p : mode.params()) values.put(p.id(), p.def());
    }

    public static ModeParams defaults(final BuildMode mode) {
        return new ModeParams(mode);
    }

    public BuildMode mode() { return mode; }

    /** Raw value (Boolean / Integer / String); null for an id the mode does not declare. */
    public Object get(final String id) {
        return values.get(id);
    }

    public boolean getBool(final String id) {
        return values.get(id) instanceof Boolean b && b;
    }

    public int getInt(final String id) {
        return values.get(id) instanceof Integer i ? i : 0;
    }

    /** A CHOICE value as declared (e.g. {@code "REPLACEABLE"}); empty for an unknown id. */
    public String getChoice(final String id) {
        return values.get(id) instanceof String s ? s : "";
    }

    /** A CHOICE value mapped onto an enum with the same constant names; {@code fallback} when it does not match. */
    public <E extends Enum<E>> E getEnum(final String id, final Class<E> type, final E fallback) {
        final String s = getChoice(id);
        for (final E e : type.getEnumConstants()) if (e.name().equals(s)) return e;
        return fallback;
    }

    /** Sets a value (coerced into range / options); ignored for ids the mode does not declare. Returns this. */
    public ModeParams set(final String id, final Object value) {
        final ModeParam p = mode.param(id);
        if (p != null) values.put(id, p.sanitize(value));
        return this;
    }

    /** Resets every parameter to its default. */
    public ModeParams reset() {
        for (final ModeParam p : mode.params()) values.put(p.id(), p.def());
        return this;
    }

    public ModeParams copy() {
        final ModeParams out = new ModeParams(mode);
        out.values.putAll(values);
        return out;
    }

    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        for (final ModeParam p : mode.params()) {
            final Object v = values.get(p.id());
            switch (p.type()) {
                case BOOL -> tag.putBoolean(p.id(), (Boolean) v);
                case INT -> tag.putInt(p.id(), (Integer) v);
                case CHOICE -> tag.putString(p.id(), (String) v);
            }
        }
        return tag;
    }

    /** Reads {@code tag} over the defaults of {@code mode}; missing or malformed entries keep their defaults. */
    public static ModeParams fromTag(final BuildMode mode, final CompoundTag tag) {
        final ModeParams out = new ModeParams(mode);
        for (final ModeParam p : mode.params()) {
            final String id = p.id();
            switch (p.type()) {
                case BOOL -> { if (tag.contains(id, Tag.TAG_BYTE)) out.set(id, tag.getBoolean(id)); }
                case INT -> { if (tag.contains(id, Tag.TAG_ANY_NUMERIC)) out.set(id, tag.getInt(id)); }
                case CHOICE -> { if (tag.contains(id, Tag.TAG_STRING)) out.set(id, tag.getString(id)); }
            }
        }
        return out;
    }

    public JsonObject toJson() {
        final JsonObject json = new JsonObject();
        for (final ModeParam p : mode.params()) {
            final Object v = values.get(p.id());
            switch (p.type()) {
                case BOOL -> json.addProperty(p.id(), (Boolean) v);
                case INT -> json.addProperty(p.id(), (Integer) v);
                case CHOICE -> json.addProperty(p.id(), (String) v);
            }
        }
        return json;
    }

    /** Reads {@code json} over the defaults of {@code mode}; missing or malformed entries keep their defaults. */
    public static ModeParams fromJson(final BuildMode mode, final JsonObject json) {
        final ModeParams out = new ModeParams(mode);
        for (final ModeParam p : mode.params()) {
            final JsonElement e = json.get(p.id());
            if (!(e instanceof JsonPrimitive prim)) continue;
            switch (p.type()) {
                case BOOL -> { if (prim.isBoolean()) out.set(p.id(), prim.getAsBoolean()); }
                case INT -> { if (prim.isNumber()) out.set(p.id(), prim.getAsInt()); }
                case CHOICE -> { if (prim.isString()) out.set(p.id(), prim.getAsString().toUpperCase(Locale.ROOT)); }
            }
        }
        return out;
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof ModeParams other && other.mode.equals(mode) && other.values.equals(values);
    }

    @Override
    public int hashCode() {
        return mode.id().hashCode() * 31 + values.hashCode();
    }

    @Override
    public String toString() {
        return mode.id() + values;
    }
}
