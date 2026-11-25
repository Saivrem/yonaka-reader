package org.dustyroom.be.i18n;

import org.dustyroom.be.models.i18n.Language;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.IllegalFormatException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class I18n {
    private static final Map<String, Language> BUNDLED = load();
    private static final Map<String, Language> LANGUAGES = new LinkedHashMap<>(BUNDLED);

    private static volatile Language current = BUNDLED.get("en-US");

    public static void loadProfile(List<Language> local, String selectedId) {
        LANGUAGES.clear();
        LANGUAGES.putAll(BUNDLED);
        local.forEach(language -> LANGUAGES.put(language.getId(), language));
        select(selectedId);
    }

    public static Language english() {
        return BUNDLED.get("en-US");
    }

    public static void install(Language language) {
        LANGUAGES.put(language.getId(), language);
        if (current.getId().equals(language.getId())) {
            current = language;
        }
    }

    static String bundledId(String name) {
        for (String id : BUNDLED.keySet()) {
            if (id.equalsIgnoreCase(name)) {
                return id;
            }
        }
        return name;
    }

    public static List<Language> languages() {
        return List.copyOf(LANGUAGES.values());
    }

    public static Language language() {
        return current;
    }

    public static void select(String id) {
        if (LANGUAGES.containsKey(id)) {
            current = LANGUAGES.get(id);
            return;
        }
        String requested = id == null ? "en-US" : id.replace('_', '-');
        String tag = Locale.forLanguageTag(requested).toLanguageTag();
        // Preserve selections saved before regional language tags were introduced.
        tag = switch (tag) {
            case "en" -> "en-US";
            case "ru" -> "ru-RU";
            case "ja" -> "ja-JP";
            default -> tag;
        };
        current = LANGUAGES.getOrDefault(tag, LANGUAGES.get("en-US"));
    }

    public static String text(String key, Object... arguments) {
        return format(current, key, arguments);
    }

    static String format(Language selected, String key, Object... arguments) {
        String english = english().getMessages().get(key);
        if (english == null) {
            throw new MissingResourceException("Unknown translation key", I18n.class.getName(), key);
        }
        String pattern = selected.getMessages().getOrDefault(key, english);
        try {
            return String.format(Locale.forLanguageTag(selected.getId()), pattern, arguments);
        } catch (IllegalFormatException exception) {
            log.warn("Invalid translation format for {}", key, exception);
            return String.format(Locale.ENGLISH, english, arguments);
        }
    }

    private static Map<String, Language> load() {
        Map<String, Language> languages = new LinkedHashMap<>();
        try (BufferedReader index = reader("languages.txt")) {
            for (String filename : index.lines().toList()) {
                try (BufferedReader input = reader(filename)) {
                    Language language = parse(input);
                    if (languages.putIfAbsent(language.getId(), language) != null) {
                        throw new IllegalArgumentException("Duplicate language " + language.getId());
                    }
                } catch (RuntimeException | IOException exception) {
                    log.warn("Cannot load translation {}", filename, exception);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load language index", exception);
        }
        if (!languages.containsKey("en-US")) {
            throw new IllegalStateException("English translation is required");
        }
        return languages;
    }

    public static Language parse(Reader input) {
        JsonObject document = JsonParser.parseReader(input).getAsJsonObject();
        String id = requiredString(document, "id");
        String name = requiredString(document, "name");
        Map<String, String> messages = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : document.getAsJsonObject("messages").entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Translation must be a string: " + entry.getKey());
            }
            messages.put(entry.getKey(), entry.getValue().getAsString());
        }
        return new Language(id, name, messages);
    }

    private static String requiredString(JsonObject document, String key) {
        JsonElement value = document.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Expected a nonblank string for " + key);
        }
        return value.getAsString();
    }

    private static BufferedReader reader(String file) throws IOException {
        InputStream input = I18n.class.getResourceAsStream("/i18n/" + file);
        if (input == null) {
            throw new IOException("Missing translation resource " + file);
        }
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }
}
