package org.dustyroom.ui.navigation;

import org.dustyroom.be.models.book.PageKey;
import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.be.models.book.PageShape;
import org.dustyroom.be.models.book.PictureMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpreadPlannerTest {
    private final SpreadPlanner planner = new SpreadPlanner();
    private final List<PageRef> pages = List.of(page("1"), page("2"), page("3"));

    @Test
    void skipsShapeLookupForCoverAndEndOfBook() {
        assertNull(planner.secondPage(pages, 0, true, true, _ -> fail("Shifted cover stands alone")));
        assertNull(planner.secondPage(pages, 2, true, false, _ -> fail("No following page")));
        assertNull(planner.secondPage(pages, 0, false, false, _ -> fail("Single-page mode")));
    }

    @Test
    void skipsNextShapeAfterLandscapeAndAlignsShiftedPortraitPair() {
        assertNull(planner.secondPage(pages, 0, true, false, page -> {
            assertSame(pages.getFirst(), page);
            return new PageShape(2, 1);
        }));
        assertEquals(1, planner.anchor(pages, 2, true, true, _ -> new PageShape(1, 2)));
    }

    private static PageRef page(String name) {
        return new PageRef(new PageKey("book", name), new PictureMetadata(name, "book", null));
    }
}
