package org.dustyroom.ui.components;

import javax.swing.*;
import java.awt.*;

public final class BookCardGrid extends JPanel {
    public static final int GAP = 16;
    private final GridLayout grid = new GridLayout(0, 2, GAP, GAP);
    private Dimension coverSize = new Dimension(72, 104);

    public BookCardGrid() {
        setLayout(grid);
    }

    public void addCard(BookCard card) {
        card.setCoverSize(coverSize);
        add(card);
    }

    public void fit(int width, int height, int visibleCards) {
        int columns = columns(width);
        Dimension size = coverSize(width, height, visibleCards);
        if (columns == grid.getColumns() && size.equals(coverSize)) {
            return;
        }

        grid.setColumns(columns);
        coverSize = size;
        for (Component component : getComponents()) {
            ((BookCard) component).setCoverSize(size);
        }
        revalidate();
    }

    public static int columns(int width) {
        return width >= 1200 ? 3 : 2;
    }

    public static Dimension coverSize(int width, int height, int visibleCards) {
        int columns = columns(width);
        int rows = Math.ceilDiv(visibleCards, columns);
        int cardWidth = (width - (columns - 1) * GAP) / columns;
        int coverWidth = Math.clamp(cardWidth / 2, 72, 320);
        int coverHeight = Math.clamp(height / rows - 112, 104, 384);
        return new Dimension(coverWidth, coverHeight);
    }
}
