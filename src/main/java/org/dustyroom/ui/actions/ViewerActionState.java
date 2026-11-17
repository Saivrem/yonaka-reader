package org.dustyroom.ui.actions;

import lombok.experimental.UtilityClass;
import org.dustyroom.be.models.direction.ReadingMode;
import org.dustyroom.be.models.state.FitMode;
import org.dustyroom.be.models.view.ViewerFrame;

import javax.swing.*;
import java.util.List;

/**
 * Publishes the displayed state to shared menu, toolbar and keyboard actions.
 */
@UtilityClass
public class ViewerActionState {

    public static void apply(ViewerActions actions, ViewerFrame frame, FitMode fit, boolean fullscreen) {
        boolean loaded = frame != null;
        for (Action action : List.of(actions.openPageGrid(), actions.nextImage(), actions.prevImage(), actions.firstImage(),
                actions.lastImage(), actions.nextVolume(), actions.prevVolume(), actions.goToPage(),
                actions.fitHeight(), actions.fitWidth(), actions.fitScreen(), actions.zoomIn(), actions.zoomOut(),
                actions.toggleTwoPageMode(), actions.setMangaReadingMode(), actions.setComicsReadingMode())) {
            action.setEnabled(loaded);
        }
        // Next and Previous remain available at the edges for the configured boundary action.
        actions.firstImage().setEnabled(loaded && frame.getFirstPage() > 1);
        actions.lastImage().setEnabled(loaded && frame.getLastPage() < frame.getPageCount());
        actions.exitFullscreen().setEnabled(fullscreen);
        selected(actions.fitHeight(), loaded && fit == FitMode.FIT_HEIGHT);
        selected(actions.fitWidth(), loaded && fit == FitMode.FIT_WIDTH);
        selected(actions.fitScreen(), loaded && fit == FitMode.FIT_SCREEN);
        actions.shiftPairing().setEnabled(loaded && frame.isTwoPageMode());
        selected(actions.shiftPairing(), loaded && frame.isShiftedPairing());
        selected(actions.toggleTwoPageMode(), loaded && frame.isTwoPageMode());
        selected(actions.setMangaReadingMode(), loaded && frame.getReadingMode() == ReadingMode.MANGA);
        selected(actions.setComicsReadingMode(), loaded && frame.getReadingMode() == ReadingMode.COMICS);
        selected(actions.toggleFullscreen(), fullscreen);
    }

    private static void selected(Action action, boolean selected) {
        action.putValue(Action.SELECTED_KEY, selected);
    }
}
