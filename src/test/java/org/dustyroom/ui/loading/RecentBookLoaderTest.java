package org.dustyroom.ui.loading;

import org.dustyroom.be.models.direction.ReadingMode;
import org.dustyroom.be.models.state.BookState;
import org.dustyroom.be.models.view.RecentBook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RecentBookLoaderTest {
    @TempDir
    Path directory;

    private BookState state(Path path) {
        return new BookState(path.toString(), "page", 2, 10, false, ReadingMode.MANGA);
    }

    @Test
    void missingSourceRetainsProgressAndCanBecomeAvailableAgain() throws Exception {
        Path path = directory.resolve("moved");
        RecentBook missing = RecentBookLoader.inspect(state(path));
        assertFalse(missing.isAvailable());
        assertEquals("Page 2 / 10", missing.getProgress());
        Files.createDirectory(path);
        assertTrue(RecentBookLoader.inspect(state(path)).isAvailable());
    }

    @Test
    void brokenCoverDoesNotDestroyHistory() throws Exception {
        Path archive = Files.writeString(directory.resolve("broken.zip"), "invalid zip");
        RecentBook book = RecentBookLoader.cover(RecentBookLoader.inspect(state(archive)));
        assertEquals(state(archive), book.getState());
        assertTrue(book.isAvailable());
        assertNull(book.getCover());
    }
}
