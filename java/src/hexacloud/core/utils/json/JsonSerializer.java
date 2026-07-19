package hexacloud.core.utils.json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import hexacloud.core.utils.common.Casts;

/**
 * Custom lightweight reflection-based JSON serializer compatible with Java 8+.
 */
public class JsonSerializer {

    /**
     * Serializes any Java object to its JSON string representation.
     */
    public static String serialize(Object obj) {
        if (obj == null) {
            return "null";
        }

        return Casts.<String>matchValue(obj)
            .when(String.class, s -> "\"" + escapeJson(s) + "\"")
            .when(Character.class, c -> "\"" + escapeJson(c.toString()) + "\"")
            .when(Number.class, n -> n.toString())
            .when(Boolean.class, b -> b.toString())
            .when(Enum.class, e -> "\"" + e.toString() + "\"")
            .when(Map.class, map -> {
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {
                    if (!first) {
                        sb.append(",");
                    }
                    first = false;
                    String keyStr = entry.getKey() == null ? "null" : entry.getKey().toString();
                    sb.append("\"").append(escapeJson(keyStr)).append("\":");
                    sb.append(serialize(entry.getValue()));
                }
                sb.append("}");
                return sb.toString();
            })
            .when(Iterable.class, iter -> {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                boolean first = true;
                for (Object item : iter) {
                    if (!first) {
                        sb.append(",");
                    }
                    first = false;
                    sb.append(serialize(item));
                }
                sb.append("]");
                return sb.toString();
            })
            .otherwise(other -> {
                if (other.getClass().isArray()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    int length = Array.getLength(other);
                    for (int i = 0; i < length; i++) {
                        if (i > 0) {
                            sb.append(",");
                        }
                        sb.append(serialize(Array.get(other, i)));
                    }
                    sb.append("]");
                    return sb.toString();
                }

                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    boolean first = true;
                    Class<?> clazz = other.getClass();
                    while (clazz != null && clazz != Object.class) {
                        Field[] fields = clazz.getDeclaredFields();
                        for (Field field : fields) {
                            int modifiers = field.getModifiers();
                            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                                continue;
                            }
                            if (field.isSynthetic()) {
                                continue;
                            }
                            if (!first) {
                                sb.append(",");
                            }
                            first = false;
                            field.setAccessible(true);
                            Object val = field.get(other);
                            sb.append("\"").append(escapeJson(field.getName())).append("\":");
                            sb.append(serialize(val));
                        }
                        clazz = clazz.getSuperclass();
                    }
                    sb.append("}");
                    return sb.toString();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize object to JSON via reflection", e);
                }
            });
    }

    /**
     * Escapes special characters for valid JSON formatting.
     */
    public static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if ((ch >= '\u0000' && ch <= '\u001F') || (ch >= '\u007F' && ch <= '\u009F') || (ch >= '\u2000' && ch <= '\u20FF')) {
                        String ss = Integer.toHexString(ch);
                        sb.append("\\u");
                        for (int k = 0; k < 4 - ss.length(); k++) {
                            sb.append('0');
                        }
                        sb.append(ss.toUpperCase());
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }
}
