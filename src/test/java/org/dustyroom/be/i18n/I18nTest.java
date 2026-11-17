package org.dustyroom.be.i18n;

import org.dustyroom.be.models.i18n.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class I18nTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+)\\$([sd])");

    @AfterEach
    void restoreEnglish() {
        I18n.select("en-US");
    }

    private static Stream<String> languages() {
        return I18n.languages()
                   .stream()
                   .map(Language::getId);
    }

    @ParameterizedTest
    @MethodSource("languages")
    void shippedLanguagesHaveCompleteCompatibleMessages(String id) {
        I18n.select("en-US");
        Map<String, String> english = I18n.language().getMessages();
        I18n.select(id);
        assertEquals(id, I18n.language().getId());
        Map<String, String> translated = I18n.language().getMessages();
        assertTrue(english.keySet().containsAll(translated.keySet()));
        if (Set.of("en-US", "ru-RU", "ja-JP").contains(id)) {
            assertEquals(english.keySet(), translated.keySet());
        }
        translated.forEach((key, translation) -> {
            String pattern = english.get(key);
            assertEquals(placeholders(pattern), placeholders(translation), key);
            Object[] arguments = new Object[10];
            Matcher matcher = PLACEHOLDER.matcher(pattern);
            while (matcher.find()) {
                int index = Integer.parseInt(matcher.group(1)) - 1;
                arguments[index] = matcher.group(2).equals("d") ? 7 : "日本語 book";
            }
            assertDoesNotThrow(() -> String.format(Locale.forLanguageTag(id), translation, arguments), key);
        });
    }

    private static Set<String> placeholders(String text) {
        Set<String> result = new HashSet<>();
        PLACEHOLDER.matcher(text).results().forEach(match -> result.add(match.group()));
        return result;
    }

    @Test
    void partialTranslationsFallBackAndCanReorderArguments() {
        Language language = I18n.parse(new StringReader("""
                {"id":"test","name":"Test language","messages":{
                  "reading.progress":"%2$d pages, current %1$d",
                  "recent.menuContinue":"%5$d"
                }}
                """));
        assertEquals("10 pages, current 3", I18n.format(language, "reading.progress", 3, 10));
        assertEquals("Open a book", I18n.format(language, "ui.open_a_book"));
        assertEquals("Continue — chapter", I18n.format(language, "recent.menuContinue", "chapter"));
    }

    @ParameterizedTest
    @CsvSource({"en,en-US", "ru,ru-RU", "ja,ja-JP", "en_us,en-US", "RU_ru,ru-RU", "ja-JP,ja-JP"})
    void resolvesLegacyCodesAndNormalizesRegionalTags(String stored, String expected) {
        I18n.select(stored);
        assertEquals(expected, I18n.language().getId());
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        I18n.select("missing");
        assertEquals("en-US", I18n.language().getId());
        assertEquals("Open a book", I18n.text("ui.open_a_book"));
    }

    @Test
    void invalidTranslationValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> I18n.parse(new StringReader("""
                {"id":"test","name":"Test","messages":{"ui.open_a_book":3}}
                """)));
    }
}
