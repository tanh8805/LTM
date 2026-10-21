// Test giao thức chung: một dòng JSON UTF-8 cho mỗi tin nhắn.
package exam;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class ProtocolTest {

    private static JsonObject withInterval(String json) {
        return Protocol.parse("{\"featureIntervalSeconds\":" + json + "}");
    }

    @Test
    void featureIntervalAcceptsTenToSixty() {
        assertEquals(20, Protocol.featureInterval(withInterval("20")));
        assertEquals(10, Protocol.featureInterval(withInterval("10")));
        assertEquals(60, Protocol.featureInterval(withInterval("60")));
    }

    @Test
    void invalidIntervalFallsBackTo30() {
        assertEquals(30, Protocol.featureInterval(Protocol.parse("{}")));
        for (String bad : new String[] {"9", "61", "0", "-5", "20.5", "\"20\"", "null", "true"}) {
            assertEquals(30, Protocol.featureInterval(withInterval(bad)), bad);
        }
    }

    private static String serialize(JsonObject m) {
        StringWriter sw = new StringWriter();
        Protocol.send(new PrintWriter(sw), m);
        return sw.toString();
    }

    @Test
    void sendThenParseKeepsFieldsIncludingVietnamese() {
        JsonObject m = Protocol.msg(Protocol.LOGIN);
        m.addProperty("name", "Nguyễn Văn Á");
        String wire = serialize(m);
        assertTrue(wire.endsWith("\n"));
        assertEquals(1, wire.split("\n").length);
        JsonObject back = Protocol.parse(wire.trim());
        assertEquals(Protocol.LOGIN, Protocol.type(back));
        assertEquals("Nguyễn Văn Á", Protocol.str(back, "name"));
    }

    @Test
    void textWithNewlineStaysOnOneLine() {
        JsonObject m = Protocol.msg(Protocol.VIOLATION);
        m.addProperty("detail", "dòng 1\ndòng 2");
        String wire = serialize(m);
        assertEquals(1, wire.split("\n").length);
        assertEquals("dòng 1\ndòng 2", Protocol.parse(wire.trim()).get("detail").getAsString());
    }

    @Test
    void brokenLineThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse("{khong phai json"));
        assertThrows(IllegalArgumentException.class, () -> Protocol.parse("[1,2,3]"));
    }

    @Test
    void missingFieldsGiveEmptyString() {
        JsonObject empty = new JsonObject();
        assertEquals("", Protocol.type(empty));
        assertEquals("", Protocol.str(empty, "name"));
    }
}
