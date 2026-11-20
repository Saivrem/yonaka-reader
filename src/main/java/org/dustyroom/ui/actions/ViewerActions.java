package org.dustyroom.ui.actions;

import javax.swing.*;

public interface ViewerActions {
    Action openPageGrid();

    Action toggleThumbnails();

    Action openFile();

    Action home();

    Action library();

    Action exit();

    Action fitHeight();

    Action fitWidth();

    Action fitScreen();

    Action zoomIn();

    Action zoomOut();

    Action nextImage();

    Action prevImage();

    Action firstImage();

    Action lastImage();

    Action nextVolume();

    Action prevVolume();

    Action toggleFullscreen();

    Action toggleTwoPageMode();

    Action shiftPairing();

    Action setComicsReadingMode();

    Action setMangaReadingMode();

    Action goToPage();

    Action exitFullscreen();

    Action settings();

    Action resetComicDatabase();

    Action showAbout();
}
