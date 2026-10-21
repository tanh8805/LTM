package exam.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RulesFileTest {
    @TempDir Path tmp;

    @Test
    void saveRulesKeepsOtherKeys() throws IOException {
        Path file = tmp.resolve("rules.json");
        Files.writeString(file, "{\"featureIntervalSeconds\":20,\"blacklistApps\":[\"zalo.exe\"],"
            + "\"whitelistApps\":[\"calc.exe\"],\"blockedDomains\":[\"Chợ.vn\"],\"extra\":{\"a\":1}}", StandardCharsets.UTF_8);
        JsonObject rules = Protocol.parse(Files.readString(file, StandardCharsets.UTF_8));
        JsonObject updated = RulesFile.withList(rules, RulesFile.BLACKLIST, List.of("zalo.exe", "chrome.exe"));
        RulesFile.save(file, updated);

        JsonObject back = Protocol.parse(Files.readString(file, StandardCharsets.UTF_8));
        assertEquals(20, back.get("featureIntervalSeconds").getAsInt());
        assertEquals(1, back.getAsJsonObject("extra").get("a").getAsInt());
        assertEquals(List.of("zalo.exe", "chrome.exe"), RulesFile.listOf(back, RulesFile.BLACKLIST));
        assertEquals(List.of("calc.exe"), RulesFile.listOf(back, RulesFile.WHITELIST));
        assertEquals(List.of("Chợ.vn"), RulesFile.listOf(back, RulesFile.DOMAINS), "tiếng Việt không bị escape hỏng");
    }

    @Test
    void quickActionMovesAppBetweenLists() {
        JsonObject rules = Protocol.parse("{\"blacklistApps\":[\"Zalo.exe\"],\"whitelistApps\":[\"calc.exe\"]}");
        JsonObject allowed = RulesFile.setAppAllowed(rules, "zalo", true);
        assertEquals(List.of(), RulesFile.listOf(allowed, RulesFile.BLACKLIST));
        assertEquals(List.of("calc.exe", "zalo"), RulesFile.listOf(allowed, RulesFile.WHITELIST));
        JsonObject banned = RulesFile.setAppAllowed(allowed, "calc", false);
        assertEquals(List.of("calc"), RulesFile.listOf(banned, RulesFile.BLACKLIST));
        assertEquals(List.of("zalo"), RulesFile.listOf(banned, RulesFile.WHITELIST));
    }
}
