package org.dustyroom.be.iterators;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.be.models.book.Picture;

abstract class VolumePageIterator implements ImageIterator {
    private final String foreignSelectionKey;
    private int currentIndex = -1;
    private boolean initialSelectionPending;

    VolumePageIterator(String foreignSelectionKey) {
        this.foreignSelectionKey = foreignSelectionKey;
    }

    final void resetCursor(int index) {
        currentIndex = index;
        initialSelectionPending = index >= 0;
    }

    final int currentIndex() {
        return currentIndex;
    }

    void checkNavigationAvailable() {
    }

    @Override
    public Picture next() {
        checkNavigationAvailable();
        if (initialSelectionPending) {
            initialSelectionPending = false;
        } else {
            currentIndex = Math.floorMod(currentIndex + 1, pages().size());
        }
        return load(pages().get(currentIndex));
    }

    @Override
    public Picture prev() {
        checkNavigationAvailable();
        initialSelectionPending = false;
        currentIndex = Math.floorMod(currentIndex - 1, pages().size());
        return load(pages().get(currentIndex));
    }

    @Override
    public Picture first() {
        checkNavigationAvailable();
        initialSelectionPending = false;
        currentIndex = 0;
        return load(pages().get(currentIndex));
    }

    @Override
    public Picture last() {
        checkNavigationAvailable();
        initialSelectionPending = false;
        currentIndex = pages().size() - 1;
        return load(pages().get(currentIndex));
    }

    @Override
    public Picture nextVol() {
        PageRef page = switchToNextVolume();
        return consumeVolumeSelection(page);
    }

    @Override
    public Picture prevVol() {
        PageRef page = switchToPreviousVolume();
        return consumeVolumeSelection(page);
    }

    private Picture consumeVolumeSelection(PageRef page) {
        if (page == null) {
            return null;
        }
        int selectedIndex = pages().indexOf(page);
        if (selectedIndex < 0) {
            throw new ImageIteratorException(I18n.text(foreignSelectionKey), getVolumeRoot());
        }
        currentIndex = selectedIndex;
        initialSelectionPending = false;
        return load(page);
    }
}
