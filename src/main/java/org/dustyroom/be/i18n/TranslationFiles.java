package org.dustyroom.be.i18n;

import org.dustyroom.be.io.StagedFile;
import org.dustyroom.be.io.TranslationJson;
import lombok.extern.slf4j.Slf4j;
import org.dustyroom.be.models.i18n.Language;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Slf4j
public final class TranslationFiles {
    private final Path directory;

    public TranslationFiles(Path profile) {
        directory = profile.resolve("translations");
    }

    public List<Language> load() {
        List<Language> catalogs = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return catalogs;
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                if (isJson(file) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        catalogs.add(read(file));
                    } catch (RuntimeException | IOException exception) {
                        log.warn("Cannot read translation {}", file, exception);
                    }
                }
            }
        } catch (IOException exception) {
            log.warn("Cannot list translations in {}", directory, exception);
        }
        return catalogs;
    }

    private static boolean isJson(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    public static Path withJsonExtension(Path file) {
        if (isJson(file)) {
            return file;
        }
        return file.resolveSibling(file.getFileName() + ".json");
    }

    public static Language read(Path file) throws IOException {
        if (!isJson(file)) {
            throw new IOException(I18n.text("translations.jsonRequired"));
        }
        String filename = file.getFileName().toString();
        String id = checkedId(filename.substring(0, filename.length() - 5));
        try (Reader input = Files.newBufferedReader(file)) {
            Language parsed = TranslationJson.read(input);
            return new Language(id, parsed.getName(), parsed.getMessages());
        } catch (RuntimeException exception) {
            throw new IOException(I18n.text("translations.invalidJson"), exception);
        }
    }

    private static String checkedId(String name) throws IOException {
        if (name.isBlank() || name.endsWith(".") || !name.equals(name.strip())
                || name.matches("(?s).*[\\\\/:*\"<>?|\\p{Cntrl}].*")
                || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new IOException(I18n.text("translations.invalidName"));
        }
        return I18n.bundledId(name);
    }

    public Language save(Language language, boolean replace) throws IOException {
        String id = checkedId(language.getId());
        Files.createDirectories(directory);
        Path target = directory.resolve(id + ".json");
        try (Stream<Path> files = Files.list(directory)) {
            target = files.filter(file -> file.getFileName().toString().equalsIgnoreCase(id + ".json"))
                          .findFirst().orElse(target);
        }
        Language saved = new Language(id, language.getName(), language.getMessages());
        write(target, saved, replace);
        return saved;
    }

    public static void write(Path target, Language language, boolean replace) throws IOException {
        StagedFile.Content content = output -> TranslationJson.write(output, language);
        if (replace) {
            StagedFile.write(target, content, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } else {
            StagedFile.write(target, content);
        }
    }
}
