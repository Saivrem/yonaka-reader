package org.dustyroom.ui.library;

import org.dustyroom.be.models.library.LibraryChapter;
import org.dustyroom.be.models.library.LibraryWork;
import org.dustyroom.ui.components.BookCard;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibraryShelfTest {
    @Test
    void mountsVisibleCardsAndKeepsScrollPositionWhenWorksRefresh() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<LibraryWork> works = new ArrayList<>();
            for (int index = 0; index < 150; index++) {
                String id = String.valueOf(index);
                works.add(new LibraryWork(id, id, id, 0, List.of(LibraryChapter.builder().id(id).build())));
            }
            List<LibraryWork> requested = new ArrayList<>();
            List<LibraryWork> cancelled = new ArrayList<>();
            JViewport viewport = new JViewport();
            LibraryShelf shelf = new LibraryShelf(viewport, work -> {
                requested.add(work);
                return new BookCard(work.getId(), () -> {}) {};
            }, cancelled::add);
            viewport.setView(shelf);
            viewport.setSize(960, 540);
            shelf.showWorks(works);
            viewport.doLayout();
            assertTrue(requested.contains(works.getFirst()));
            assertFalse(requested.contains(works.getLast()));

            Point bottom = new Point(0, shelf.getHeight() - viewport.getHeight());
            viewport.setViewPosition(bottom);
            assertTrue(requested.contains(works.getLast()));
            assertTrue(cancelled.contains(works.getFirst()));
            assertTrue(shelf.getComponentCount() < 20);

            shelf.showWorks(works);
            viewport.doLayout();
            assertEquals(bottom, viewport.getViewPosition());
        });
    }
}
