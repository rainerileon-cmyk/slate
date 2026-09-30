package dev.fallingcloud.slate.menu.client.loading.scene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON, as far as the game's model files need it: objects as maps, arrays as lists, numbers as doubles. The scene
 * reads models where no library is certain to be there, so it reads them itself.
 */
final class Json {

    private final String text;
    private int at;

    private Json(final String text) {
        this.text = text;
    }

    /** What the text says; throws {@link IllegalArgumentException} when it is not JSON. */
    static Object parse(final String text) {
        final Json j = new Json(text);
        j.space();
        final Object value = j.value();
        j.space();
        if (j.at < text.length()) throw j.wrong("something after the end");
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(final Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(final Object value) {
        return value instanceof List ? (List<Object>) value : List.of();
    }

    static float number(final Object value, final float fallback) {
        return value instanceof Number n ? n.floatValue() : fallback;
    }

    static String string(final Object value, final String fallback) {
        return value instanceof String s ? s : fallback;
    }

    private Object value() {
        if (at >= text.length()) throw wrong("nothing where a value should be");
        final char c = text.charAt(at);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (text.startsWith("true", at)) {
            at += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", at)) {
            at += 5;
            return Boolean.FALSE;
        }
        if (text.startsWith("null", at)) {
            at += 4;
            return null;
        }
        return number();
    }

    private Map<String, Object> object() {
        final Map<String, Object> map = new LinkedHashMap<>();
        at++;
        space();
        if (peek() == '}') {
            at++;
            return map;
        }
        while (true) {
            space();
            if (peek() != '"') throw wrong("a name expected");
            final String name = string();
            space();
            if (peek() != ':') throw wrong("a colon expected");
            at++;
            space();
            map.put(name, value());
            space();
            final char c = peek();
            at++;
            if (c == '}') return map;
            if (c != ',') throw wrong("a comma expected");
            // A comma before the end is let pass, as the game's reader lets it.
            space();
            if (peek() == '}') {
                at++;
                return map;
            }
        }
    }

    private List<Object> array() {
        final List<Object> list = new ArrayList<>();
        at++;
        space();
        if (peek() == ']') {
            at++;
            return list;
        }
        while (true) {
            space();
            list.add(value());
            space();
            final char c = peek();
            at++;
            if (c == ']') return list;
            if (c != ',') throw wrong("a comma expected");
            space();
            if (peek() == ']') {
                at++;
                return list;
            }
        }
    }

    private String string() {
        final StringBuilder s = new StringBuilder();
        at++;
        while (at < text.length()) {
            final char c = text.charAt(at++);
            if (c == '"') return s.toString();
            if (c != '\\') {
                s.append(c);
                continue;
            }
            if (at >= text.length()) break;
            final char e = text.charAt(at++);
            switch (e) {
                case 'n' -> s.append('\n');
                case 't' -> s.append('\t');
                case 'r' -> s.append('\r');
                case 'b' -> s.append('\b');
                case 'f' -> s.append('\f');
                case 'u' -> {
                    if (at + 4 > text.length()) throw wrong("a short escape");
                    s.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> s.append(e);
            }
        }
        throw wrong("a string without an end");
    }

    private Double number() {
        final int from = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
        if (at == from) throw wrong("a value expected");
        try {
            return Double.valueOf(text.substring(from, at));
        } catch (final NumberFormatException e) {
            throw wrong("not a number");
        }
    }

    private char peek() {
        return at < text.length() ? text.charAt(at) : '\0';
    }

    /** Over blanks, and over comments, which some tools leave in. */
    private void space() {
        while (at < text.length()) {
            final char c = text.charAt(at);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') {
                at++;
            } else if (c == '/' && at + 1 < text.length() && text.charAt(at + 1) == '/') {
                while (at < text.length() && text.charAt(at) != '\n') at++;
            } else if (c == '/' && at + 1 < text.length() && text.charAt(at + 1) == '*') {
                final int end = text.indexOf("*/", at + 2);
                at = end < 0 ? text.length() : end + 2;
            } else {
                return;
            }
        }
    }

    private IllegalArgumentException wrong(final String what) {
        return new IllegalArgumentException(what + " at " + at);
    }
}
