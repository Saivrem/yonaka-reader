package org.dustyroom.be.iterators;

import org.dustyroom.be.i18n.I18n;
import lombok.extern.slf4j.Slf4j;
import org.dustyroom.be.animation.gif.GifDecoder;
import org.dustyroom.be.models.animation.AnimationClip;
import org.dustyroom.be.models.book.PageKey;
import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.be.models.book.Picture;
import org.dustyroom.be.models.book.PictureMetadata;

import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.dustyroom.be.models.direction.Direction.NEXT;
import static org.dustyroom.be.models.direction.Direction.PREV;
import static org.dustyroom.be.utils.FileUtils.isSupportedImage;
import static org.dustyroom.be.utils.FileUtils.isZipFile;
import static org.dustyroom.be.utils.FileUtils.normalize;
import static org.dustyroom.be.utils.FileUtils.normalizedPath;
import static org.dustyroom.be.utils.NaturalOrderComparator.INSTANCE;

@Slf4j
public class ZipIterator implements ImageIterator {

    private List<PageRef> pageRefs = List.of();
    private File zipFilePath;
    private File pendingZipFilePath;
    private ZipFile zipFile;
    private int currentIndex = -1;
    private boolean initialSelectionPending;
    private boolean closed;

    public ZipIterator(File zipFilePath) {
        this.pendingZipFilePath = normalize(zipFilePath);
        init();
    }

    @Override
    public void init() {
        ensureNotClosed();
        File candidatePath = pendingZipFilePath != null ? pendingZipFilePath : zipFilePath;
        if (candidatePath == null) {
            throw new ImageIteratorException(I18n.text("errors.zip_source_is_not_set"), null);
        }

        ZipFile openedZip = null;
        try {
            openedZip = new ZipFile(candidatePath);
            List<ZipEntry> entries = new ArrayList<>();
            Enumeration<? extends ZipEntry> contents = openedZip.entries();

            while (contents.hasMoreElements()) {
                ZipEntry zipEntry = contents.nextElement();
                if (!zipEntry.isDirectory() && isSupportedImage(zipEntry.getName())) {
                    entries.add(zipEntry);
                }
            }

            entries.sort(Comparator.comparing(ZipEntry::getName, INSTANCE));
            if (entries.isEmpty()) {
                throw new ImageIteratorException(I18n.text("zip.empty", candidatePath.getName()), candidatePath);
            }

            List<PageRef> pages = entries
                    .stream()
                    .map(entry -> pageRef(candidatePath, entry))
                    .toList();
            ZipFile previousZip = zipFile;
            zipFile = openedZip;
            zipFilePath = candidatePath;
            pageRefs = pages;
            currentIndex = 0;
            initialSelectionPending = true;
            openedZip = null;
            closeQuietly(previousZip);
        } catch (IOException e) {
            log.warn("Can't read the ZIP file {}", candidatePath, e);
            throw new ImageIteratorException(I18n.text("zip.open", candidatePath.getName()), candidatePath, e);
        } finally {
            pendingZipFilePath = null;
            closeQuietly(openedZip);
        }
    }

    @Override
    public File getVolumeRoot() {
        return zipFilePath;
    }

    @Override
    public void setFile(File file) {
        ensureNotClosed();
        this.pendingZipFilePath = normalize(file);
    }

    @Override
    public Picture next() {
        ensureOpen();
        if (initialSelectionPending) {
            initialSelectionPending = false;
        } else {
            currentIndex = Math.floorMod(currentIndex + 1, pageRefs.size());
        }
        return load(pageRefs.get(currentIndex));
    }

    @Override
    public Picture prev() {
        ensureOpen();
        initialSelectionPending = false;
        currentIndex = Math.floorMod(currentIndex - 1, pageRefs.size());
        return load(pageRefs.get(currentIndex));
    }

    @Override
    public Picture first() {
        ensureOpen();
        initialSelectionPending = false;
        currentIndex = 0;
        return load(pageRefs.get(currentIndex));
    }

    @Override
    public Picture last() {
        ensureOpen();
        initialSelectionPending = false;
        currentIndex = pageRefs.size() - 1;
        return load(pageRefs.get(currentIndex));
    }

    @Override
    public AnimationClip animation(PageRef page) {
        ensureOpen();
        if (!page.getKey().sourceId().equals(normalizedPath(zipFilePath))) {
            throw new IllegalArgumentException(I18n.text("errors.wrong_source"));
        }
        // Playback owns an independent archive handle, including after switching volumes.
        File archive = zipFilePath;
        String entryName = page.getKey().pageId();
        return GifDecoder.read(() -> {
            ZipFile zip = new ZipFile(archive);
            try {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    throw new IOException(I18n.text("zip.gifMissing", entryName));
                }
                return new ZipEntryInputStream(zip.getInputStream(entry), zip);
            } catch (IOException | RuntimeException error) {
                try {
                    zip.close();
                } catch (IOException suppressed) {
                    error.addSuppressed(suppressed);
                }
                throw error;
            }
        });
    }

    @Override
    public Picture load(PageRef page) {
        validatePage(page);
        return readImage(zipFile, zipFilePath, page);
    }

    @Override
    public Supplier<Picture> detachedLoad(PageRef page) {
        validatePage(page);
        File archive = zipFilePath;
        return () -> {
            try (ZipFile zip = new ZipFile(archive)) {
                return readImage(zip, archive, page);
            } catch (IOException e) {
                throw new ImageIteratorException(I18n.text("zip.open", archive.getName()), archive, e);
            }
        };
    }

    private void validatePage(PageRef page) {
        ensureOpen();
        String currentSourceId = normalizedPath(zipFilePath);
        if (!page.getKey().sourceId().equals(currentSourceId)) {
            throw new ImageIteratorException(I18n.text("zip.foreignPage"), zipFilePath);
        }
    }

    private static Picture readImage(ZipFile zipFile, File zipFilePath, PageRef page) {
        ZipEntry entry = zipFile.getEntry(page.getKey().pageId());
        if (entry == null || entry.isDirectory()) {
            throw new ImageIteratorException(I18n.text("zip.missing", page.getKey().pageId()), zipFilePath);
        }
        log.debug("File: {}", entry.getName());
        try (InputStream input = zipFile.getInputStream(entry);
             ImageInputStream imageInput = ImageDecoder.openStream(input, entry.getSize())) {
            BufferedImage read = ImageDecoder.read(imageInput);
            if (read == null) {
                throw new ImageIteratorException(
                        I18n.text("zip.corrupt", entry.getName()),
                        zipFilePath
                );
            }
            return new Picture(read, page.getMetadata());
        } catch (IOException e) {
            log.warn("Can't read image {} from {}", entry.getName(), zipFilePath, e);
            throw new ImageIteratorException(
                    I18n.text("zip.read", entry.getName(), zipFilePath.getName()),
                    zipFilePath,
                    e
            );
        }
    }

    @Override
    public Picture nextVol() {
        PageRef page = switchToNextVolume();
        return consumeVolumeSelection(page);
    }

    @Override
    public Picture prevVol() {
        PageRef page = switchToPreviousVolume();
        return consumeVolumeSelection(page);
    }

    @Override
    public List<PageRef> pages() {
        return pageRefs;
    }

    @Override
    public int initialPageIndex() {
        return 0;
    }

    @Override
    public PageRef switchToNextVolume() {
        ensureCurrentVolumeAvailable();
        return switchVolume(isZipFile, NEXT);
    }

    @Override
    public PageRef switchToPreviousVolume() {
        ensureCurrentVolumeAvailable();
        return switchVolume(isZipFile, PREV);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeQuietly(zipFile);
        zipFile = null;
        pendingZipFilePath = null;
        pageRefs = List.of();
        currentIndex = -1;
        initialSelectionPending = false;
    }

    private void ensureOpen() {
        if (closed || zipFile == null) {
            throw new ImageIteratorException(I18n.text("errors.zip_archive_is_closed"), zipFilePath);
        }
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new ImageIteratorException(I18n.text("errors.zip_archive_is_closed"), zipFilePath);
        }
    }

    private void ensureCurrentVolumeAvailable() {
        ensureOpen();
        if (!isZipFile.test(zipFilePath)) {
            throw new ImageIteratorException(I18n.text("errors.current_zip_source_is_no_longer_available"), zipFilePath);
        }
    }

    private void closeQuietly(ZipFile source) {
        if (source == null) {
            return;
        }
        try {
            source.close();
        } catch (IOException e) {
            log.warn("Can't close the ZIP file {}", zipFilePath, e);
        }
    }

    private PageRef pageRef(File source, ZipEntry entry) {
        return new PageRef(
                new PageKey(source.getPath(), entry.getName()),
                new PictureMetadata(entry.getName(), source.getName(), source.getParentFile())
        );
    }

    private Picture consumeVolumeSelection(PageRef page) {
        if (page == null) {
            return null;
        }
        int selectedIndex = pageRefs.indexOf(page);
        if (selectedIndex < 0) {
            throw new ImageIteratorException(I18n.text("zip.foreignSelection"), zipFilePath);
        }
        currentIndex = selectedIndex;
        initialSelectionPending = false;
        return load(page);
    }
}
