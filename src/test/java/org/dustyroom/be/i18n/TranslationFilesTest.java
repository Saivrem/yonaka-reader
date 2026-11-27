package org.dustyroom.be.i18n;

import org.dustyroom.be.models.i18n.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TranslationFilesTest {
    @TempDir Path profile;

    @AfterEach
    void restoreBundledCatalogs() {
        I18n.loadProfile(List.of(), "en-US");
    }

    @Test
    void localSaveOverridesBundledEnglishImmediatelyAndAfterRestart() throws IOException {
        TranslationFiles files = new TranslationFiles(profile);
        Language bundled = I18n.english();
        String key = "ui.open_a_book";
        assertTrue(files.load().isEmpty());
        assertFalse(Files.exists(profile.resolve("translations")));

        Language edited = new Language("en-US", bundled.getName(), Map.of(key, "Read something"));
        I18n.install(files.save(edited, true));
        assertEquals("Read something", I18n.text(key));
        assertEquals("Open a book", bundled.getMessages().get(key));
        I18n.loadProfile(files.load(), "en");
        assertEquals("Read something", I18n.text(key));
        assertEquals(bundled.getMessages().get("ui.cancel"), I18n.text("ui.cancel"));
        assertEquals(1, I18n.languages().stream().filter(language -> language.getId().equals("en-US")).count());

        files.save(new Language("en-US", bundled.getName(), Map.of(key, "Open it")), true);
        assertEquals("Open it", files.load().getFirst().getMessages().get(key));
    }

    @Test
    void importsExportsAndReopensRenamedCatalogs() throws IOException {
        TranslationFiles files = new TranslationFiles(profile);
        Path incoming = profile.resolve("en-us.json");
        Language translation = new Language("en-US", "Custom English", Map.of("reading.progress", "%1$d\n%2$d"));
        TranslationFiles.write(incoming, translation, false);
        Language imported = TranslationFiles.read(incoming);
        assertEquals("en-US", imported.getId());
        files.save(imported, false);
        assertThrows(FileAlreadyExistsException.class, () -> files.save(imported, false));

        String name = "Вася-перевод";
        Language renamed = files.save(new Language(name, name, imported.getMessages()), false);
        I18n.loadProfile(files.load(), name);
        assertEquals(renamed, I18n.language());
        assertEquals("2\n9", I18n.text("reading.progress", 2, 9));
        Path exported = profile.resolve(name + ".json");
        TranslationFiles.write(exported, renamed, false);
        assertEquals(renamed, TranslationFiles.read(exported));
        assertEquals(-1L, Files.mismatch(exported, profile.resolve("translations").resolve(name + ".json")));
        assertEquals(imported, TranslationFiles.read(profile.resolve("translations/en-US.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"id\":7,\"name\":\"Test\",\"messages\":{}}",
            "{\"id\":\"test\",\"name\":true,\"messages\":{}}",
            "{\"id\":\"test\",\"name\":\"Test\",\"messages\":[[\"key\",\"value\"]]}",
            "{\"id\":\"test\",\"name\":\"Test\",\"messages\":{\"key\":null}}"
    })
    void rejectsMalformedCatalogFields(String json) throws IOException {
        Path file = Files.writeString(profile.resolve("import.json"), json);
        assertThrows(IOException.class, () -> TranslationFiles.read(file));
    }

    @Test
    void malformedFilesAndUnsafeNamesDoNotReplaceCatalogs() throws IOException {
        TranslationFiles files = new TranslationFiles(profile);
        Language valid = I18n.english();
        files.save(valid, false);
        Path invalid = profile.resolve("translations/broken.json");
        Files.writeString(invalid, "{}");
        assertThrows(IOException.class, () -> TranslationFiles.read(invalid));
        assertEquals(List.of(valid), files.load());
        assertThrows(IOException.class, () -> files.save(new Language("../outside", "Unsafe", Map.of()), false));
        assertFalse(Files.exists(profile.resolve("outside.json")));
    }
}
