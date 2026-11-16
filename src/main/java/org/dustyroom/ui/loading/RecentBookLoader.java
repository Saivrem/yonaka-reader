package org.dustyroom.ui.loading;

import lombok.experimental.UtilityClass;
import org.dustyroom.be.models.state.BookState;
import org.dustyroom.be.models.view.RecentBook;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Filesystem checks and decoding belong on a worker, never on Swing's EDT.
 */
@UtilityClass
class RecentBookLoader {

    static RecentBook inspect(BookState state) {
        return new RecentBook(state, isAvailable(state), null);
    }

    private static boolean isAvailable(BookState state) {
        try {
            Path path = Path.of(state.getSourceId());
            return Files.isReadable(path) && (Files.isDirectory(path) || Files.isRegularFile(path));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static RecentBook inspectGroup(List<BookState> chapters) {
        RecentBook book = inspect(chapters.getFirst());
        return book.setChapters(chapters);
    }

    static RecentBook cover(RecentBook book) {
        if (!book.isAvailable()) {
            return book;
        }
        BufferedImage image = BookCoverLoader.load(book.getState().getSourceId());
        boolean available = image != null || isAvailable(book.getState());
        return new RecentBook(book.getState(), available, image, book.getChapters());
    }
}
