package org.dustyroom.ui.loading;

import lombok.experimental.UtilityClass;
import org.dustyroom.be.iterators.ImageIterator;
import org.dustyroom.be.iterators.ImageIteratorFactory;
import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.ui.rendering.ThumbnailRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

@UtilityClass
public class BookCoverLoader {
    public static BufferedImage load(String path) {
        try (ImageIterator source = ImageIteratorFactory.create(Path.of(path).toFile())) {
            List<PageRef> pages = source.pages();
            if (pages.isEmpty()) {
                return null;
            }
            return ThumbnailRenderer.renderCover(source.load(pages.getFirst(),
                    ThumbnailRenderer.COVER_WIDTH, ThumbnailRenderer.COVER_HEIGHT).getImage());
        } catch (RuntimeException ignored) {
            // Missing and unreadable sources still have a card with a placeholder.
            return null;
        }
    }
}
