import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonCodec {
    private JsonCodec() {}

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.space();
        if (!parser.done()) throw new IllegalArgumentException("Unexpected JSON suffix");
        return value;
    }

    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            return "{" + map.entrySet().stream()
                    .map(e -> quote(e.getKey().toString()) + ":" + stringify(e.getValue()))
                    .reduce((a, b) -> a + "," + b).orElse("") + "}";
        }
        if (value instanceof Iterable<?> items) {
            List<String> encoded = new ArrayList<>();
            items.forEach(item -> encoded.add(stringify(item)));
            return "[" + String.join(",", encoded) + "]";
        }
        if (value.getClass().isRecord()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            try {
                for (RecordComponent component : value.getClass().getRecordComponents()) {
                    fields.put(component.getName(), component.getAccessor().invoke(value));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Cannot encode record", e);
            }
            return stringify(fields);
        }
        return quote(value.toString());
    }

    private static String quote(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            result.append(switch (c) {
                case '\"' -> "\\\"";
                case '\\' -> "\\\\";
                case '\n' -> "\\n";
                case '\r' -> "\\r";
                case '\t' -> "\\t";
                default -> c < 32 ? String.format("\\u%04x", (int) c) : c;
            });
        }
        return result.append('\"').toString();
    }

    private static final class Parser {
        private final String source;
        private int at;
        Parser(String source) { this.source = source; }
        boolean done() { return at == source.length(); }
        void space() { while (!done() && Character.isWhitespace(source.charAt(at))) at++; }
        Object value() {
            space();
            if (done()) throw new IllegalArgumentException("Expected JSON value");
            return switch (source.charAt(at)) {
                case '{' -> object();
                case '[' -> array();
                case '\"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }
        Object literal(String token, Object value) {
            if (!source.startsWith(token, at)) throw new IllegalArgumentException("Invalid JSON literal");
            at += token.length();
            return value;
        }
        Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            at++; space();
            if (take('}')) return result;
            do { space(); String key = string(); space(); expect(':'); result.put(key, value()); space(); }
            while (take(','));
            expect('}');
            return result;
        }
        List<Object> array() {
            List<Object> result = new ArrayList<>();
            at++; space();
            if (take(']')) return result;
            do { result.add(value()); space(); } while (take(','));
            expect(']');
            return result;
        }
        String string() {
            expect('\"'); StringBuilder result = new StringBuilder();
            while (!done()) {
                char c = source.charAt(at++);
                if (c == '\"') return result.toString();
                if (c != '\\') { result.append(c); continue; }
                char escaped = source.charAt(at++);
                result.append(switch (escaped) {
                    case '\"', '\\', '/' -> escaped;
                    case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    case 'u' -> (char) Integer.parseInt(source.substring(at, at += 4), 16);
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                });
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }
        Number number() {
            int start = at;
            while (!done() && "-+0123456789.eE".indexOf(source.charAt(at)) >= 0) at++;
            String token = source.substring(start, at);
            return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token);
        }
        boolean take(char expected) { if (!done() && source.charAt(at) == expected) { at++; return true; } return false; }
        void expect(char expected) { if (!take(expected)) throw new IllegalArgumentException("Expected " + expected); }
    }
}
