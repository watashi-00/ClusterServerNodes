package hexacloud.core.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for JsonSerializer class.
 */
public class JsonSerializerTest {

    @Test
    public void testSerializeNull() {
        assertEquals("null", JsonSerializer.serialize(null));
    }

    @Test
    public void testSerializePrimitives() {
        assertEquals("123", JsonSerializer.serialize(123));
        assertEquals("12.34", JsonSerializer.serialize(12.34));
        assertEquals("true", JsonSerializer.serialize(true));
        assertEquals("false", JsonSerializer.serialize(false));
    }

    @Test
    public void testSerializeStringAndEscaping() {
        assertEquals("\"hello\"", JsonSerializer.serialize("hello"));
        assertEquals("\"hello \\\"world\\\"\"", JsonSerializer.serialize("hello \"world\""));
        assertEquals("\"line1\\\\nline2\"", JsonSerializer.serialize("line1\\nline2"));
    }

    enum DummyEnum {
        VAL_A,
        VAL_B {
            @Override
            public String toString() {
                return "custom_val_b";
            }
        }
    }

    @Test
    public void testSerializeEnum() {
        assertEquals("\"VAL_A\"", JsonSerializer.serialize(DummyEnum.VAL_A));
        assertEquals("\"custom_val_b\"", JsonSerializer.serialize(DummyEnum.VAL_B));
    }

    @Test
    public void testSerializeMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Alice");
        map.put("age", 30);
        map.put("active", true);

        assertEquals("{\"name\":\"Alice\",\"age\":30,\"active\":true}", JsonSerializer.serialize(map));
    }

    @Test
    public void testSerializeIterableAndArray() {
        List<Object> list = Arrays.asList("apple", 123, false);
        assertEquals("[\"apple\",123,false]", JsonSerializer.serialize(list));

        String[] array = new String[]{"one", "two"};
        assertEquals("[\"one\",\"two\"]", JsonSerializer.serialize(array));
    }

    static class SubClass extends BaseClass {
        private final String childField = "child";
    }

    static class BaseClass {
        private final int parentField = 42;
    }

    @Test
    public void testSerializeCustomObject() {
        SubClass obj = new SubClass();
        String json = JsonSerializer.serialize(obj);
        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertTrue(json.contains("\"childField\":\"child\""));
        assertTrue(json.contains("\"parentField\":42"));
    }
}
