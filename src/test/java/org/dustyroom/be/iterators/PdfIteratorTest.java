package org.dustyroom.be.iterators;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.dustyroom.be.library.LibraryScanner;
import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.be.models.book.Picture;
import org.dustyroom.be.models.library.LibraryScan;
import org.dustyroom.ui.loading.BookCoverLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static org.dustyroom.be.iterators.ImageTestFixtures.writePdf;
import static org.dustyroom.be.models.direction.Direction.NEXT;
import static org.dustyroom.be.models.direction.Direction.PREV;
import static org.junit.jupiter.api.Assertions.*;

class PdfIteratorTest {
    @TempDir
    Path directory;

    @Test
    void displaysTheJpeg2000ScanOverTheUnderlyingPageContent() throws Exception {
        Path file = directory.resolve("scan.pdf");
        try (PDDocument document = new PDDocument();
             InputStream encoded = getClass().getResourceAsStream("/pdf/scan.jp2")) {
            PDPage page = new PDPage(new PDRectangle(64, 64));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(Color.RED);
                content.addRect(0, 0, 64, 64);
                content.fill();
                PDImageXObject image = new PDImageXObject(document, encoded,
                        COSName.JPX_DECODE, 64, 64, 8, PDDeviceRGB.INSTANCE);
                content.drawImage(image, 0, 0);
            }
            document.save(file.toFile());
        }

        try (ImageIterator reader = ImageIteratorFactory.create(file.toFile())) {
            BufferedImage rendered = reader.load(reader.pages().getFirst(), 64, 64).getImage();
            assertEquals(Color.BLACK.getRGB(), rendered.getRGB(8, 32));
            assertEquals(Color.WHITE.getRGB(), rendered.getRGB(56, 32));
        }
    }

    @Test
    void turnsPagesAndKeepsDetachedPreviewUsableAfterSwitchAndClose() throws Exception {
        Path first = writePdf(directory.resolve("2.PDF"), 2);
        Path last = writePdf(directory.resolve("10.pdf"), 1);
        Files.createFile(directory.resolve("3.zip"));

        Supplier<Picture> preview;
        try (ImageIterator reader = ImageIteratorFactory.create(first.toFile())) {
            assertNull(reader.adjacentVolume(PREV));
            assertEquals(last.toFile(), reader.adjacentVolume(NEXT));
            assertEquals("1", reader.next().getMetadata().getName());
            assertEquals("2", reader.prev().getMetadata().getName());
            assertEquals("1", reader.next().getMetadata().getName());
            PageRef second = reader.pages().getLast();
            preview = reader.detachedLoad(second, 60, 75);
            reader.switchToNextVolume();
            assertNull(reader.adjacentVolume(NEXT));
            assertThrows(ImageIteratorException.class, () -> reader.load(second));
            assertEquals(first.getFileName().toString(), reader.nextVol().getMetadata().getFileName());
        }
        Picture picture = preview.get();
        assertEquals("2", picture.getMetadata().getName());
        assertEquals(50, picture.getImage().getWidth());
        assertEquals(75, picture.getImage().getHeight());
        Files.delete(first);
        Files.delete(last);
    }

    @Test
    void boundsRotatedPageAndRendersItsCoverAtTargetSize() throws Exception {
        Path path = directory.resolve("oversize.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage rotated = new PDPage(new PDRectangle(3000, 6000));
            rotated.setRotation(90);
            doc.addPage(rotated);
            doc.save(path.toFile());
        }
        try (ImageIterator reader = ImageIteratorFactory.create(path.toFile())) {
            BufferedImage full = reader.first().getImage();
            assertTrue(full.getWidth() > full.getHeight());
            assertTrue((long) full.getWidth() * full.getHeight() <= 8 * 1024 * 1024);
        }
        BufferedImage cover = BookCoverLoader.load(path.toString());
        assertEquals(960, cover.getWidth());
        assertEquals(480, cover.getHeight());
    }

    @Test
    void importsReadableVolumesAndExplainsPasswordFailureWithoutLosingCurrentVolume() throws Exception {
        Path ok = writePdf(directory.resolve("1.pdf"), 1);
        Path locked = directory.resolve("2.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner", "reader", new AccessPermission()));
            document.save(locked.toFile());
        }
        String message = "2.pdf requires a password. Open an unlocked copy to read it.";
        try (ImageIterator reader = ImageIteratorFactory.create(ok.toFile())) {
            assertEquals(message, assertThrows(ImageIteratorException.class, reader::nextVol).getMessage());
            assertEquals(ok.toFile(), reader.getVolumeRoot());
            assertEquals("1", reader.first().getMetadata().getName());
        }
        LibraryScan scan = new LibraryScanner().scan(directory, () -> false);
        assertEquals(List.of(message), scan.warnings());
        assertEquals(1, scan.works().size());
        assertEquals(ok.toString(), scan.works().getFirst().getSources().getFirst().getPath());
    }
}
