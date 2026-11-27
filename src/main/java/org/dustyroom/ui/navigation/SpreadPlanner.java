package org.dustyroom.ui.navigation;

import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.be.models.book.PageShape;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class SpreadPlanner {

    public int anchor(List<PageRef> pages, int index, boolean twoPageMode, boolean shiftedPairing,
                      Function<PageRef, PageShape> shapeOf) {
        if (index == 0 || startsPair(index, shiftedPairing)) {
            return index;
        }
        return secondPage(pages, index - 1, twoPageMode, shiftedPairing, shapeOf) == null ? index : index - 1;
    }

    public PageRef secondPage(List<PageRef> pages, int anchorIndex, boolean twoPageMode, boolean shiftedPairing,
                              Function<PageRef, PageShape> shapeOf) {
        if (!twoPageMode || pages.size() < 2 || anchorIndex + 1 >= pages.size()) {
            return null;
        }
        if (shiftedPairing && !startsPair(anchorIndex, true)) {
            return null;
        }
        PageRef current = pages.get(anchorIndex);
        PageRef next = pages.get(anchorIndex + 1);
        return shouldPair(shapeOf.apply(current), () -> shapeOf.apply(next)) ? next : null;
    }

    public boolean startsPair(int pageIndex, boolean shifted) {
        int first = shifted ? 1 : 0;
        return pageIndex % 2 == first;
    }

    public boolean shouldPair(PageShape currentPage, Supplier<PageShape> nextPage) {
        return !currentPage.isLandscape() && !nextPage.get().isLandscape();
    }

}
