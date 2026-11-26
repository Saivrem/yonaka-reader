package org.dustyroom.be.iterators;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.DefaultResourceCache;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.book.PageKey;
import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.be.models.book.Picture;
import org.dustyroom.be.models.book.PictureMetadata;
import org.dustyroom.be.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.dustyroom.be.models.direction.Direction.NEXT;
import static org.dustyroom.be.models.direction.Direction.PREV;

@Slf4j
final class PdfIterator implements ImageIterator {
    // 288 dpi on typical pages. Cap the RGB raster at 32 MiB and also limit very narrow pages.
    private static final int MAX_PIXELS = 8 * 1024 * 1024;
    private static final int MAX_SIDE = 8192;

    private File file;
    private File pending;
    private PDDocument document;
    private List<PageRef> pages = List.of();
    private int index;
    private boolean firstPending;
    private boolean closed;

    PdfIterator(File file) {
        setFile(file);
        init();
    }

    @Override
    public void init() {
        ensureActive();
        File candidate = pending == null ? file : pending;
        pending = null;
        PDDocument opened = open(candidate);
        try {
            int count = opened.getNumberOfPages();
            if (count == 0) {
                throw new ImageIteratorException(I18n.text("pdf.empty", candidate.getName()), candidate);
            }
            List<PageRef> refs = IntStream.rangeClosed(1, count)
                    .mapToObj(number -> {
                        String id = Integer.toString(number);
                        return new PageRef(new PageKey(candidate.getPath(), id),
                                new PictureMetadata(id, candidate.getName(), candidate.getParentFile()));
                    })
                    .toList();

            release(document);
            document = opened;
            opened = null;
            file = candidate;
            pages = refs;
            index = 0;
            firstPending = true;
        } finally {
            release(opened);
        }
    }

    private static PDDocument open(File file) {
        try {
            PDDocument document = Loader.loadPDF(file);
            document.setResourceCache(new DefaultResourceCache() {
                @Override
                public void put(COSObject key, PDXObject object) {
                    // The reader already caches rendered pages. Release decoded scans after each render.
                    if (!(object instanceof PDImageXObject)) {
                        super.put(key, object);
                    }
                }
            });
            return document;
        } catch (InvalidPasswordException e) {
            throw new ImageIteratorException(I18n.text("pdf.password", file.getName()), file, e);
        } catch (IOException e) {
            throw new ImageIteratorException(I18n.text("pdf.open", file.getName()), file, e);
        }
    }

    @Override
    public Picture load(PageRef page) {
        return load(page, MAX_SIDE, MAX_SIDE);
    }

    @Override
    public Picture load(PageRef page, int maxWidth, int maxHeight) {
        return render(document, file, page, pageIndex(page), maxWidth, maxHeight);
    }

    @Override
    public Supplier<Picture> detachedLoad(PageRef page) {
        return detachedLoad(page, MAX_SIDE, MAX_SIDE);
    }

    @Override
    public Supplier<Picture> detachedLoad(PageRef page, int maxWidth, int maxHeight) {
        int number = pageIndex(page);
        File source = file;
        return () -> {
            try (PDDocument independent = open(source)) {
                return render(independent, source, page, number, maxWidth, maxHeight);
            } catch (IOException e) {
                throw new ImageIteratorException(I18n.text("pdf.open", source.getName()), source, e);
            }
        };
    }

    private int pageIndex(PageRef page) {
        ensureActive();
        if (!page.getKey().sourceId().equals(file.getPath())) {
            throw new ImageIteratorException(I18n.text("errors.wrong_source"), file);
        }
        return Integer.parseInt(page.getKey().pageId()) - 1;
    }

    private static Picture render(PDDocument document, File file, PageRef ref, int number, int maxWidth, int maxHeight) {
        try {
            PDPage page = document.getPage(number);
            PDRectangle crop = page.getCropBox();
            boolean rotated = page.getRotation() % 180 != 0;
            double width = rotated ? crop.getHeight() : crop.getWidth();
            double height = rotated ? crop.getWidth() : crop.getHeight();
            if (!(width > 0 && height > 0 && Double.isFinite(width * height))) {
                throw new IOException("Invalid PDF page dimensions");
            }
            double fit = Math.min(Math.min(maxWidth, MAX_SIDE) / width, Math.min(maxHeight, MAX_SIDE) / height);
            double scale = Math.min(4, Math.min(fit, Math.sqrt(MAX_PIXELS / (width * height))));
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            return new Picture(renderer.renderImage(number, (float) scale, ImageType.RGB), ref.getMetadata());
        } catch (IOException | RuntimeException e) {
            throw new ImageIteratorException(I18n.text("pdf.page", number + 1, file.getName()), file, e);
        }
    }

    @Override
    public File getVolumeRoot() {
        return file;
    }

    @Override
    public void setFile(File file) {
        ensureActive();
        pending = FileUtils.normalize(file);
    }

    @Override
    public List<PageRef> pages() {
        return pages;
    }

    @Override
    public int initialPageIndex() {
        return 0;
    }

    private Picture select(int target) {
        ensureActive();
        index = Math.floorMod(target, pages.size());
        firstPending = false;
        return load(pages.get(index));
    }

    @Override
    public Picture next() {
        return select(firstPending ? index : index + 1);
    }

    @Override
    public Picture prev() {
        return select(index - 1);
    }

    @Override
    public Picture first() {
        return select(0);
    }

    @Override
    public Picture last() {
        return select(pages.size() - 1);
    }

    @Override
    public PageRef switchToNextVolume() {
        return switchVolume(FileUtils.isPdf, NEXT);
    }

    @Override
    public PageRef switchToPreviousVolume() {
        return switchVolume(FileUtils.isPdf, PREV);
    }

    @Override
    public Picture nextVol() {
        switchToNextVolume();
        return first();
    }

    @Override
    public Picture prevVol() {
        switchToPreviousVolume();
        return first();
    }

    @Override
    public void close() {
        closed = true;
        release(document);
        document = null;
        pages = List.of();
    }

    private void ensureActive() {
        if (closed) {
            throw new ImageIteratorException(I18n.text("pdf.closed"), file);
        }
    }

    private static void release(PDDocument document) {
        if (document != null) {
            try {
                document.close();
            } catch (IOException e) {
                log.warn("Could not close PDF", e);
            }
        }
    }
}
