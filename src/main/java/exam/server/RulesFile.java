package exam.server;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.client.AppRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Sửa và ghi lại config/rules.json: chỉ đổi các danh sách được sửa, mọi khoá khác giữ nguyên. */
public final class RulesFile {
    public static final String BLACKLIST = "blacklistApps", WHITELIST = "whitelistApps", ALLOWED_WINDOW = "allowedWindowApps",
        IGNORE = "ignoreProcesses", DOMAINS = "blockedDomains";

    private RulesFile() {}

    public static List<String> listOf(JsonObject rules, String key) {
        List<String> list = new ArrayList<>();
        if (rules.get(key) instanceof JsonArray array) {
            for (JsonElement e : array) { list.add(e.getAsString()); }
        }
        return list;
    }

    /** Bản sao của rules với danh sách `key` thay bằng `values`. */
    public static JsonObject withList(JsonObject rules, String key, List<String> values) {
        JsonObject copy = rules.deepCopy();
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        copy.add(key, array);
        return copy;
    }

    /** Cho phép (vào whitelistApps) hoặc cấm (vào blacklistApps) một app; tên đó bị gỡ khỏi mọi danh sách app khác. */
    public static JsonObject setAppAllowed(JsonObject rules, String name, boolean allow) {
        String wanted = AppRules.norm(name);
        JsonObject copy = rules;
        for (String key : new String[] {BLACKLIST, WHITELIST, ALLOWED_WINDOW}) {
            List<String> list = listOf(copy, key);
            list.removeIf(item -> AppRules.norm(item).equals(wanted));
            if (key.equals(allow ? WHITELIST : BLACKLIST)) { list.add(name.trim()); }
            copy = withList(copy, key, list);
        }
        return copy;
    }

    public static void save(Path file, JsonObject rules) throws IOException {
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(rules);
        Files.writeString(file, json + "\n", StandardCharsets.UTF_8);
    }
}
