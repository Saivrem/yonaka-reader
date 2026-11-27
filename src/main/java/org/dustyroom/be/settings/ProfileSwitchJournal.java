package org.dustyroom.be.settings;

import org.dustyroom.be.i18n.I18n;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.Reader;
import org.dustyroom.be.io.StagedFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Metadata for completing an explicitly requested move after an interrupted shutdown.
 */
@UtilityClass
class ProfileSwitchJournal {
    static Path file(Path standardDirectory) {
        return standardDirectory.resolve("profile-switch.properties");
    }

    static void begin(Path standardDirectory, Path source, Path target) throws IOException {
        Files.createDirectories(standardDirectory);
        if (Files.exists(file(standardDirectory))) {
            throw new IOException(I18n.text("profile.recoveryRequired"));
        }
        Properties properties = new Properties();
        properties.setProperty("source", source.toAbsolutePath().normalize().toString());
        properties.setProperty("target", target.toAbsolutePath().normalize().toString());
        StagedFile.write(file(standardDirectory),
                output -> properties.store(output, "Pending user-requested profile move; not a database backup"),
                StandardCopyOption.ATOMIC_MOVE);
    }

    static void recover(Path standardDirectory) throws IOException {
        Path journal = file(standardDirectory);
        if (!Files.exists(journal)) {
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(journal, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Path source = Path.of(properties.getProperty("source"));
        Path target = Path.of(properties.getProperty("target"));
        if (!source.isAbsolute() || !target.isAbsolute() || !"reader.sqlite3".equals(source.getFileName().toString())
                || !"reader.sqlite3".equals(target.getFileName().toString())) {
            throw new IOException(I18n.text("errors.invalid_pending_profile_switch"));
        }
        if (Files.isRegularFile(source)) {
            Files.delete(journal);
            return;
        }
        if (!Files.isRegularFile(target)) {
            throw new IOException(I18n.text("profile.recoveryFailed"));
        }
        ProfileManager.writePointer(standardDirectory, target.getParent());
        Files.delete(journal);
    }
}
