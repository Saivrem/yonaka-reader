package org.dustyroom.ui.components;

import org.dustyroom.ui.i18n.UiText;
import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.view.ViewerFrame;
import org.dustyroom.ui.actions.ViewerActions;

import javax.swing.*;
import java.awt.*;

public class NavigationPanel extends JPanel {
    private final JButton position;
    private String pageLabel;
    private boolean loading;

    public NavigationPanel(ViewerActions actions) {
        setLayout(new BorderLayout(12, 0));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JPanel files = group();
        files.add(button(actions.home(), ReaderIconKind.HOME));
        files.add(button(actions.openFile(), ReaderIconKind.OPEN));
        files.add(button(actions.openPageGrid(), ReaderIconKind.PAGE_GRID));
        files.add(toggle(actions.toggleFullscreen(), ReaderIconKind.FULLSCREEN));
        files.add(button(actions.prevVolume(), ReaderIconKind.PREVIOUS_VOLUME));
        files.add(button(actions.nextVolume(), ReaderIconKind.NEXT_VOLUME));
        add(files, BorderLayout.WEST);

        JPanel pages = group();
        pages.add(button(actions.firstImage(), ReaderIconKind.FIRST));
        pages.add(button(actions.prevImage(), ReaderIconKind.PREVIOUS));
        position = new JButton(actions.goToPage());
        position.setRequestFocusEnabled(false);
        refreshPosition();
        position.setPreferredSize(new Dimension(116, 36));

        UiText.accessible(position, "ui.page_position_activate_to_go_to_a_page");
        pages.add(position);
        pages.add(button(actions.nextImage(), ReaderIconKind.NEXT));
        pages.add(button(actions.lastImage(), ReaderIconKind.LAST));
        add(pages, BorderLayout.CENTER);

        JPanel settings = group();
        settings.add(toggle(actions.fitScreen(), ReaderIconKind.FIT_SCREEN));
        settings.add(toggle(actions.fitWidth(), ReaderIconKind.FIT_WIDTH));
        settings.add(toggle(actions.fitHeight(), ReaderIconKind.FIT_HEIGHT));
        settings.add(button(actions.zoomOut(), ReaderIconKind.ZOOM_OUT));
        settings.add(button(actions.zoomIn(), ReaderIconKind.ZOOM_IN));
        settings.add(toggle(actions.setMangaReadingMode(), ReaderIconKind.READ_RIGHT_TO_LEFT));
        settings.add(toggle(actions.setComicsReadingMode(), ReaderIconKind.READ_LEFT_TO_RIGHT));
        settings.add(toggle(actions.toggleTwoPageMode(), ReaderIconKind.SPREAD));
        add(settings, BorderLayout.EAST);
        UiText.bind(this, "position", NavigationPanel::refreshPosition);
    }

    private static JPanel group() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        panel.setOpaque(false);
        return panel;
    }

    private static JButton button(Action action, ReaderIconKind icon) {
        JButton button = new JButton(action);
        decorate(button, icon);
        return button;
    }

    private static JToggleButton toggle(Action action, ReaderIconKind icon) {
        JToggleButton button = new JToggleButton(action);
        decorate(button, icon);
        button.putClientProperty("FlatLaf.style", "selectedBackground: $Component.accentColor");
        updateToggleShape(button);
        button.addItemListener(_ -> updateToggleShape(button));
        return button;
    }

    private static void updateToggleShape(JToggleButton button) {
        button.putClientProperty("JButton.buttonType", button.isSelected() ? "roundRect" : "toolBarButton");
    }

    private static void decorate(AbstractButton button, ReaderIconKind icon) {
        button.setHideActionText(true);
        button.setRequestFocusEnabled(false);
        button.setIcon(new ReaderIcon(icon));
        button.setPreferredSize(new Dimension(36, 36));
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.getAccessibleContext().setAccessibleName((String) button.getAction().getValue(Action.NAME));
    }

    public void updateState(ViewerFrame frame) {
        loading = false;
        pageLabel = null;
        if (frame != null) {
            pageLabel = (frame.getFirstPage() == frame.getLastPage() ? String.valueOf(frame.getFirstPage())
                    : frame.getFirstPage() + "–" + frame.getLastPage()) + " / " + frame.getPageCount();
        }
        refreshPosition();
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        refreshPosition();
    }

    private void refreshPosition() {
        String label = pageLabel == null ? I18n.text("ui.no_book_open") : pageLabel;
        position.setText(loading ? I18n.text("ui.loading") : label);
    }
}
