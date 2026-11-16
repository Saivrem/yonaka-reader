package org.dustyroom.ui.loading;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BookCoverLoaderTest {
    @TempDir
    Path directory;

    @Test
    void archiveCoverIsFirstPageAndHandleIsClosed() throws Exception {
        Path archive = directory.resolve("日本語 book.zip");
        BufferedImage image = new BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("02.png"));
            ImageIO.write(image, "png", zip);
            zip.closeEntry();

            image.setRGB(0, 0, 0xff123456);
            zip.putNextEntry(new ZipEntry("01.png"));
            ImageIO.write(image, "png", zip);
            zip.closeEntry();
        }
        BufferedImage cover = BookCoverLoader.load(archive.toString());
        assertNotNull(cover);
        assertEquals(0xff123456, cover.getRGB(0, 0));
        Files.delete(archive); // On Windows this also verifies that the archive was closed.
    }
}
