package org.dustyroom.persistence;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.state.FitMode;
import org.dustyroom.be.models.state.ReaderSettings;
import org.dustyroom.be.models.state.ViewerTheme;
import org.dustyroom.be.persistence.ReaderStore;
import org.dustyroom.be.persistence.SqliteReaderStore;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Run against the fat JAR to verify JDBC service discovery and native-library packaging.
 */
public final class PackagedStoreSmoke {
    static void main(String[] args) throws IOException {
        for (String format : new String[]{"JPEG2000", "JBIG2"}) {
            if (!ImageIO.getImageReadersByFormatName(format).hasNext()) {
                throw new AssertionError("Packaged PDF image decoder missing: " + format);
            }
        }
        BufferedImage scan = ImageIO.read(Path.of("src/test/resources/pdf/scan.jp2").toFile());
        if (scan == null || scan.getWidth() != 64 || scan.getRGB(8, 32) != Color.BLACK.getRGB()
                || scan.getRGB(56, 32) != Color.WHITE.getRGB()) {
            throw new AssertionError("Packaged JPEG 2000 decoder returned incorrect scan pixels");
        }
        for (String id : new String[]{"en-US", "ru-RU", "ja-JP"}) {
            I18n.select(id);
            if (!id.equals(I18n.language().getId()) || I18n.text("ui.open_a_book").isBlank()) {
                throw new AssertionError("Packaged translation missing: " + id);
            }
        }
        I18n.select("en-US");
        Path database = Path.of("build", "packaged-smoke.sqlite3");
        ReaderSettings expected = new ReaderSettings(ViewerTheme.LIGHT, FitMode.FIT_WIDTH, 1);
        try (ReaderStore store = new SqliteReaderStore(database)) {
            store.saveSettings(expected);
        }
        try (ReaderStore store = new SqliteReaderStore(database)) {
            if (!expected.equals(store.settings())) {
                throw new AssertionError("Packaged SQLite round trip failed");
            }
        }
        System.out.println("Packaged SQLite and language catalogs passed");
    }
}
