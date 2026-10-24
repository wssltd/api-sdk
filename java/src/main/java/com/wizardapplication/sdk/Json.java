// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer so the SDK has no dependencies.
 * Objects become {@code Map<String, Object>} (insertion ordered), arrays
 * {@code List<Object>}, whole numbers {@code Long}, other numbers
 * {@code Double}, plus String, Boolean and null.
 */
public final class Json {
    private static final int MAX_DEPTH = 512;

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** Parses JSON text. Throws IllegalArgumentException on invalid input. */
    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != p.s.length()) throw p.err("unexpected trailing data");
        return v;
    }

    /** Writes a Map, List, String, Number, Boolean or null as JSON. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) throw err("nesting too deep");
        if (i >= s.length()) throw err("unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object(depth);
            case '[': return array(depth);
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw err("unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (peek('}')) { i++; return m; }
        for (;;) {
            ws();
            if (!peek('"')) throw err("expected a key");
            String key = string();
            ws();
            expect(':');
            ws();
            m.put(key, value(depth + 1));
            ws();
            if (peek(',')) { i++; continue; }
            expect('}');
            return m;
        }
    }

    private List<Object> array(int depth) {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (peek(']')) { i++; return l; }
        for (;;) {
            ws();
            l.add(value(depth + 1));
            ws();
            if (peek(',')) { i++; continue; }
            expect(']');
            return l;
        }
    }

    private String string() {
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw err("bad unicode escape");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw err("bad unicode escape");
                        }
                        i += 4;
                        break;
                    default: throw err("bad escape");
                }
            } else if (c < 0x20) {
                throw err("control character in string");
            } else {
                sb.append(c);
            }
        }
        throw err("unterminated string");
    }

    private Object number() {
        int start = i;
        if (peek('-')) i++;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        boolean fraction = false;
        if (peek('.')) { fraction = true; i++; while (i < s.length() && Character.isDigit(s.charAt(i))) i++; }
        if (peek('e') || peek('E')) {
            fraction = true;
            i++;
            if (peek('+') || peek('-')) i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        }
        String n = s.substring(start, i);
        try {
            if (!fraction) {
                try {
                    return Long.parseLong(n);
                } catch (NumberFormatException tooBig) {
                    return Double.parseDouble(n);
                }
            }
            return Double.parseDouble(n);
        } catch (NumberFormatException ex) {
            throw err("bad number");
        }
    }

    private Object literal(String word, Object v) {
        if (!s.startsWith(word, i)) throw err("unexpected token");
        i += word.length();
        return v;
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
            i++;
        }
    }

    private boolean peek(char c) {
        return i < s.length() && s.charAt(i) == c;
    }

    private void expect(char c) {
        if (!peek(c)) throw err("expected '" + c + "'");
        i++;
    }

    private IllegalArgumentException err(String msg) {
        return new IllegalArgumentException("Invalid JSON at " + i + ": " + msg);
    }

    private static void write(StringBuilder sb, Object v, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Value nested too deep to write as JSON");
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String || v instanceof Character || v instanceof Enum) {
            quote(sb, v.toString());
        } else if (v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new IllegalArgumentException("JSON has no NaN or Infinity");
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d); else sb.append(d);
        } else if (v instanceof Number) {
            sb.append(v);
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue(), depth + 1);
            }
            sb.append('}');
        } else if (v instanceof Collection || v instanceof Object[]) {
            Iterable<?> items = v instanceof Object[] ? java.util.Arrays.asList((Object[]) v) : (Collection<?>) v;
            sb.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item, depth + 1);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Cannot write " + v.getClass().getName() + " as JSON; use a Map, List, String, Number or Boolean");
        }
    }

    private static void quote(StringBuilder sb, String str) {
        sb.append('"');
        for (int k = 0; k < str.length(); k++) {
            char c = str.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
