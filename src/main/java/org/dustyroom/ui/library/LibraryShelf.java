package org.dustyroom.ui.library;

import org.dustyroom.be.models.library.LibraryWork;
import org.dustyroom.ui.components.BookCard;
import org.dustyroom.ui.components.BookCardGrid;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Reserves space for the whole collection and mounts cards around the viewport. */
final class LibraryShelf extends JPanel {
    private final JViewport viewport;
    private final Function<LibraryWork, BookCard> createCard;
    private final Consumer<LibraryWork> cancelCover;
    private final Map<Integer, BookCard> cards = new HashMap<>();
    private List<LibraryWork> works = List.of();
    private Dimension coverSize = new Dimension();

    LibraryShelf(JViewport viewport, Function<LibraryWork, BookCard> createCard, Consumer<LibraryWork> cancelCover) {
        super(null);
        this.viewport = viewport;
        this.createCard = createCard;
        this.cancelCover = cancelCover;
        viewport.addChangeListener(_ -> updateCards());
    }

    void showWorks(List<LibraryWork> works) {
        removeAll();
        cards.clear();
        this.works = works;
        updateCards();
    }

    private void updateCards() {
        Rectangle view = viewport.getViewRect();
        int columns = BookCardGrid.columns(view.width);
        Dimension size = BookCardGrid.coverSize(view.width, view.height, 6);
        boolean resized = !size.equals(coverSize);
        coverSize = size;
        int rowHeight = size.height + BookCard.HEIGHT_BELOW_COVER + BookCardGrid.GAP;
        int rows = Math.ceilDiv(works.size(), columns);
        Dimension extent = new Dimension(view.width, Math.max(0, rows * rowHeight - BookCardGrid.GAP));
        if (!extent.equals(getPreferredSize())) {
            setPreferredSize(extent);
        }

        if (view.width <= 0 || view.height <= 0) {
            return;
        }
        int start = Math.max(0, view.y / rowHeight - 1) * columns;
        int end = Math.min(works.size(), (Math.ceilDiv(view.y + view.height, rowHeight) + 1) * columns);
        cards.entrySet().removeIf(entry -> {
            int index = entry.getKey();
            if (index >= start && index < end) {
                return false;
            }
            remove(entry.getValue());
            cancelCover.accept(works.get(index));
            return true;
        });

        int width = (view.width - (columns - 1) * BookCardGrid.GAP) / columns;
        for (int index = start; index < end; index++) {
            BookCard card = cards.get(index);
            if (card == null) {
                card = createCard.apply(works.get(index));
                cards.put(index, card);
                add(card);
                card.setCoverSize(size);
            } else if (resized) {
                card.setCoverSize(size);
            }
            card.setBounds(index % columns * (width + BookCardGrid.GAP), index / columns * rowHeight,
                    width, rowHeight - BookCardGrid.GAP);
        }
        repaint();
    }
}
