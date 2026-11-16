package org.dustyroom.ui.navigation;

import org.dustyroom.be.models.book.PageShape;

import java.util.function.Supplier;

public class SpreadPlanner {

    public boolean shouldPair(PageShape currentPage, Supplier<PageShape> nextPage) {
        return !currentPage.isLandscape() && !nextPage.get().isLandscape();
    }

}
