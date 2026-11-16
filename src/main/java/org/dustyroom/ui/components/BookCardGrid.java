package org.dustyroom.ui.components;

import javax.swing.*;
import java.awt.*;

public final class BookCardGrid extends JPanel {
    private final GridLayout grid = new GridLayout(0, 2, 16, 16);
    private Dimension coverSize = new Dimension(72, 104);

    public BookCardGrid() {
        setLayout(grid);
    }

    public void addCard(BookCard card) {
        card.setCoverSize(coverSize);
        add(card);
    }

    public void fit(int width, int height, int visibleCards) {
        int columns = width >= 1200 ? 3 : 2;
        int rows = Math.ceilDiv(visibleCards, columns);
        int cardWidth = (width - (columns - 1) * grid.getHgap()) / columns;
        int coverWidth = Math.clamp(cardWidth / 2, 72, 320);
        int coverHeight = Math.clamp(height / rows - 112, 104, 384);
        Dimension size = new Dimension(coverWidth, coverHeight);
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
}
