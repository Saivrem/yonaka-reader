package org.dustyroom.ui.actions;

import org.dustyroom.ui.i18n.UiText;
import org.dustyroom.be.i18n.I18n;
import lombok.Getter;
import lombok.experimental.Accessors;

import org.dustyroom.ui.ViewerController;
import org.dustyroom.ui.utils.PlatformUi;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@Getter
@Accessors(fluent = true)
public class DefaultViewerActions implements ViewerActions {
    private final Action openPageGrid;
    private final Action toggleThumbnails;
    private final Action openFile;
    private final Action home;
    private final Action library;
    private final Action exit;
    private final Action fitHeight;
    private final Action fitWidth;
    private final Action fitScreen;
    private final Action zoomIn;
    private final Action zoomOut;
    private final Action nextImage;
    private final Action prevImage;
    private final Action firstImage;
    private final Action lastImage;
    private final Action nextVolume;
    private final Action prevVolume;
    private final Action toggleFullscreen;
    private final Action toggleTwoPageMode;
    private final Action shiftPairing;
    private final Action setComicsReadingMode;
    private final Action setMangaReadingMode;
    private final Action showAbout;
    private final Action resetComicDatabase;
    private final Action settings;
    private final Action goToPage;
    private final Action exitFullscreen;

    public DefaultViewerActions(ViewerController controller) {
        this.openPageGrid = action("openPageGrid", "ui.grid", "ui.open_page_grid", KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK), controller::openPageGrid);
        this.toggleThumbnails = action("toggleThumbnails", "ui.thumbnails", "ui.show_or_hide_page_thumbnails",
                key(KeyEvent.VK_T), controller::toggleThumbnails);
        KeyStroke homeShortcut = menuShortcut(KeyEvent.VK_H);
        this.home = action("home", "ui.home", "ui.return_to_recently_read_books",
                KeyStroke.getKeyStroke(KeyEvent.VK_H, homeShortcut.getModifiers() | InputEvent.SHIFT_DOWN_MASK), controller::showHome);
        this.library = action("library", "ui.library", "ui.open_library",
                menuShortcut(KeyEvent.VK_L), controller::showLibrary);
        this.openFile = action("openFile", "ui.open", "ui.open_an_image_zip_cbz_archive_or_folder",
                menuShortcut(KeyEvent.VK_O), controller::chooseFile);
        this.exit = action("exit", PlatformUi.usesScreenMenuBar() ? "ui.quit" : "ui.exit", "ui.close_yonaka_reader",
                menuShortcut(KeyEvent.VK_Q), controller::exit);
        this.fitHeight = action("fitHeight", "ui.fit_height", "ui.fit_the_page_to_the_window_height",
                key(KeyEvent.VK_H), controller::fitHeight);
        this.fitWidth = action("fitWidth", "ui.fit_width", "ui.fit_the_page_to_the_window_width",
                key(KeyEvent.VK_W), controller::fitWidth);
        this.fitScreen = action("fitScreen", "ui.fit_to_window", "ui.fit_the_entire_page_inside_the_window",
                key(KeyEvent.VK_S), controller::fitScreen);
        this.zoomIn = action("zoomIn", "ui.zoom_in", "ui.increase_the_page_scale",
                key(KeyEvent.VK_PLUS), controller::zoomIn);
        this.zoomOut = action("zoomOut", "ui.zoom_out", "ui.decrease_the_page_scale",
                key(KeyEvent.VK_MINUS), controller::zoomOut);
        this.nextImage = action("nextImage", "ui.next_page", "ui.show_the_next_page",
                key(KeyEvent.VK_RIGHT), controller::showNextImage);
        this.prevImage = action("previousImage", "ui.previous_page", "ui.show_the_previous_page",
                key(KeyEvent.VK_LEFT), controller::showPreviousImage);
        this.firstImage = action("firstImage", "ui.first_page", "ui.jump_to_the_first_page",
                key(KeyEvent.VK_HOME), controller::showFirstImage);
        this.lastImage = action("lastImage", "ui.last_page", "ui.jump_to_the_last_page",
                key(KeyEvent.VK_END), controller::showLastImage);
        this.nextVolume = action("nextVolume", "ui.next_volume", "ui.open_the_next_volume",
                key(KeyEvent.VK_DOWN), controller::showNextVolume);
        this.prevVolume = action("previousVolume", "ui.previous_volume", "ui.open_the_previous_volume",
                key(KeyEvent.VK_UP), controller::showPrevVolume);
        this.toggleFullscreen = action("toggleFullscreen", "ui.full_screen", "ui.enter_or_leave_full_screen_mode",
                key(KeyEvent.VK_F), controller::toggleFullscreen);
        this.shiftPairing = action("shiftPairing", "ui.shift_spread_by_one_page", "reading.shiftHint", null, controller::shiftPairing);
        this.toggleTwoPageMode = action("toggleTwoPageMode", "ui.two_page_spread", "ui.show_compatible_pages_as_a_spread",
                key(KeyEvent.VK_P), controller::toggleTwoPageMode);
        this.setComicsReadingMode = action("comicsReadingMode", "ui.comics_left_to_right", "ui.use_left_to_right_spread_order",
                key(KeyEvent.VK_C), controller::setComicsReadingMode);
        this.setMangaReadingMode = action("mangaReadingMode", "ui.manga_right_to_left", "ui.use_right_to_left_spread_order",
                key(KeyEvent.VK_M), controller::setMangaReadingMode);
        this.goToPage = action("goToPage", "ui.go_to_page", "ui.jump_to_a_page",
                key(KeyEvent.VK_G), controller::choosePage);
        this.exitFullscreen = action("exitFullscreen", "ui.leave_grid_or_full_screen", "reading.leaveViewHint",
                key(KeyEvent.VK_ESCAPE), controller::exitFullscreen);
        this.settings = action("settings", "ui.settings", "ui.appearance_reading_defaults_library_and_data",
                menuShortcut(KeyEvent.VK_COMMA), controller::showSettings);
        this.resetComicDatabase = action("resetComicDatabase", "ui.reset_comic_database",
                "ui.reset_settings_library_and_reading_history", null, controller::resetComicDatabase);
        this.showAbout = action("showAbout", "ui.about_yonaka_reader", "ui.show_application_information",
                null, controller::showAboutDialog);
    }

    static Action action(String id, String name, String description,
                         KeyStroke accelerator, Runnable runnable) {
        Action action = new RunnableAction(I18n.text(name), runnable);
        UiText.bind(action, "name", target -> target.putValue(Action.NAME, I18n.text(name)));
        action.putValue("translation.description", description);
        action.putValue(Action.ACTION_COMMAND_KEY, id);
        action.putValue(Action.SHORT_DESCRIPTION, I18n.text(description));
        if (accelerator != null) {
            action.putValue(Action.ACCELERATOR_KEY, accelerator);
        }
        return action;
    }

    private static KeyStroke menuShortcut(int keyCode) {
        int modifiers;
        try {
            modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException exception) {
            modifiers = InputEvent.CTRL_DOWN_MASK;
        }
        return KeyStroke.getKeyStroke(keyCode, modifiers);
    }

    private static KeyStroke key(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, 0);
    }
}
