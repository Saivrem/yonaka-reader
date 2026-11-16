package org.dustyroom.persistence;

import org.dustyroom.be.models.direction.ReadingMode;
import org.dustyroom.be.models.state.BookState;
import org.dustyroom.be.models.state.ViewerTheme;
import org.dustyroom.be.persistence.SqliteReaderStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.dustyroom.persistence.PersistenceSql.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentBooksTest extends BasePersistenceTest {

    private BookState book(String id, int page) {
        return new BookState(id, "page-" + page, page, 10, true, ReadingMode.COMICS);
    }

    @Test
    void removalClearsOnlySelectedHistoryAcrossRestarts() throws Exception {
        Path file = Files.writeString(directory.resolve("日本語 book.zip"), "untouched");
        BookState state = book(file.toString(), 3);
        try (SqliteReaderStore store = openStore()) {
            store.saveBook(state);
            store.saveBook(book("second.zip", 2));
            store.saveBook(book("other.zip", 5));
            store.removeRecent(List.of(state.getSourceId(), "second.zip"));
        }
        try (SqliteReaderStore store = openStore()) {
            assertTrue(store.book(state.getSourceId()).isEmpty());
            assertTrue(store.book("second.zip").isEmpty());
            assertEquals(List.of(book("other.zip", 5)), store.recentBooks(12));
        }
        assertEquals("untouched", Files.readString(file));
    }

    @Test
    void historyIsOrderedBoundedAndDoesNotDuplicateBooks() throws Exception {
        try (SqliteReaderStore store = openStore()) {
            for (int index = 0; index < 15; index++) {
                String id = "book-" + index;
                store.saveBook(book(id, 1));
                execute(SET_BOOK_TIMESTAMP, index, id);
            }

            assertEquals(12, store.recentBooks(12).size());
            assertEquals("book-14", store.recentBooks(12).getFirst().getSourceId());
            store.saveBook(book("book-0", 5));
            assertEquals("book-0", store.recentBooks(12).getFirst().getSourceId());
            assertEquals(15, store.recentBooks(100).size());
        }
    }

    @Test
    void upgradesSchemaOneWithoutLosingStateOrCreatingCopies() throws Exception {
        execute(CREATE_LEGACY_SETTINGS);
        execute(INSERT_LEGACY_SETTING, "theme", "LIGHT");
        execute(CREATE_LEGACY_BOOKS);
        execute(INSERT_LEGACY_BOOK, "saved.zip", "page-3", 3, 10, true, "COMICS", 123);
        setSchemaVersion(1);
        try (SqliteReaderStore store = openStore()) {
            assertEquals(book("saved.zip", 3), store.recentBooks(12).getFirst());
            assertEquals(ViewerTheme.LIGHT, store.settings().getTheme());
        }
        assertNoBackups();
    }
}
