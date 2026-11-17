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
    private static final Map<String, Language> LANGUAGES = load();
    private static volatile Language current = LANGUAGES.get("en");

    public static List<Language> languages() {
        return List.copyOf(LANGUAGES.values());
    }

    public static Language language() {
        return current;
    }

    public static void select(String id) {
        current = LANGUAGES.getOrDefault(id, LANGUAGES.get("en"));
    }

    public static String text(String key, Object... arguments) {
        return format(current, key, arguments);
    }

    static String format(Language selected, String key, Object... arguments) {
        String english = LANGUAGES.get("en").getMessages().get(key);
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
        if (!languages.containsKey("en")) {
            throw new IllegalStateException("English translation is required");
        }
        return languages;
    }

    public static Language parse(Reader input) {
        JsonObject document = JsonParser.parseReader(input).getAsJsonObject();
        String id = document.get("id").getAsString();
        String name = document.get("name").getAsString();
        if (id.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("Language id and name must not be blank");
        }
        Map<String, String> messages = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : document.getAsJsonObject("messages").entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Translation must be a string: " + entry.getKey());
            }
            messages.put(entry.getKey(), entry.getValue().getAsString());
        }
        return new Language(id, name, Map.copyOf(messages));
    }

    private static BufferedReader reader(String file) throws IOException {
        InputStream input = I18n.class.getResourceAsStream("/i18n/" + file);
        if (input == null) {
            throw new IOException("Missing translation resource " + file);
        }
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }
}
