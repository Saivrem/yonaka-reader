package org.dustyroom.ui.components;

import org.dustyroom.ui.i18n.UiText;
import org.dustyroom.be.i18n.I18n;
import lombok.Getter;
import lombok.Setter;

import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.ui.actions.RunnableAction;
import org.dustyroom.ui.listeners.ComponentResizeListener;
import org.dustyroom.ui.listeners.ComponentShownListener;
import org.dustyroom.ui.listeners.MouseClickListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * EDT-owned, virtualized list. Tracks up to two outstanding thumbnail requests.
 */
public final class ThumbnailPanel extends JPanel {
    private static final int MAX_PENDING = 2;
    private static final int PREFETCH_MARGIN = 10;
    private final JList<PageRef> list = new ThumbnailList();
    private final ThumbnailLoader loader;
    private final JScrollPane scroll = new JScrollPane(list);
    private final JButton handle = new ThumbnailHandle();
    private final Map<Integer, Icon> cache = new HashMap<>();
    private final JPanel rail = new JPanel(new GridBagLayout());
    private final JButton gridButton = new JButton(new ReaderIcon(ReaderIconKind.PAGE_GRID, 16));
    private final IntConsumer navigate;
    @Setter
    private Consumer<Boolean> gridChanged = _ -> {
    };
    private boolean fullscreen;
    @Getter
    private boolean gridMode;
    private boolean expanded;
    private List<PageRef> pages = List.of();
    private int windowStart, windowEnd = -1;
    private final Map<Integer, AtomicBoolean> pending = new HashMap<>();
    private int first = -1, last = -1;

    public ThumbnailPanel(ThumbnailLoader loader, IntConsumer navigate, Runnable hide) {
        super(new BorderLayout());
        this.loader = loader;
        this.navigate = navigate;

        UiText.tooltip(gridButton, "ui.expand_page_grid");
        UiText.accessible(gridButton, "ui.expand_page_grid");
        gridButton.addActionListener(_ -> setGridMode(!gridMode));
        gridButton.setEnabled(false);
        gridButton.putClientProperty("JButton.buttonType", "toolBarButton");
        gridButton.setVisible(false);
        list.setFixedCellHeight(228);
        list.setFixedCellWidth(160);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        UiText.accessible(list, "ui.book_pages");
        list.setCellRenderer(new ThumbnailCellRenderer(cache, () -> first, () -> last, () -> gridMode));
        list.addMouseListener(new MouseClickListener(e -> {
            int index = list.locationToIndex(e.getPoint());
            if (SwingUtilities.isLeftMouseButton(e) && index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                openPage(index);
            }
        }));
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openPage");
        list.getActionMap().put("openPage", new RunnableAction(I18n.text("ui.open_page"), () -> {
            if (list.getSelectedIndex() >= 0) {
                openPage(list.getSelectedIndex());
            }
        }));

        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().addChangeListener(_ -> pump());
        scroll.getViewport().addComponentListener(new ComponentResizeListener(this::pump));
        add(scroll, BorderLayout.CENTER);
        rail.setPreferredSize(new Dimension(16, 0));
        handle.setPreferredSize(new Dimension(16, 72));
        handle.setBorderPainted(false);
        handle.setContentAreaFilled(false);
        handle.setFocusPainted(false);
        handle.setRolloverEnabled(true);
        handle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        handle.addActionListener(_ -> hide.run());
        rail.add(handle);
        add(rail, BorderLayout.EAST);
        add(gridButton, BorderLayout.SOUTH);
        UiText.bind(this, "labels", ThumbnailPanel::refreshLanguage);
        setExpanded(true);
        addComponentListener(new ComponentShownListener(() -> {
            followCurrent();
            pump();
        }));
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        updateGridButton();
    }

    private void refreshLanguage() {
        gridButton.setText(gridMode ? I18n.text("ui.back_to_reader") : null);
        gridButton.setIcon(new ReaderIcon(gridMode ? ReaderIconKind.PREVIOUS : ReaderIconKind.PAGE_GRID, 16));
        gridButton.setToolTipText(gridMode ? I18n.text("ui.return_to_reader") : I18n.text("ui.expand_page_grid"));
        gridButton.getAccessibleContext().setAccessibleName(gridButton.getToolTipText());
        String label = expanded ? I18n.text("ui.hide_thumbnails") : I18n.text("ui.show_thumbnails");
        handle.setToolTipText(label);
        handle.getAccessibleContext().setAccessibleName(label);
    }

    private void updateGridButton() {
        gridButton.setVisible(gridMode || (fullscreen && expanded));
    }

    public void setGridMode(boolean gridMode) {
        if (this.gridMode == gridMode || (gridMode && pages.isEmpty())) {
            return;
        }
        this.gridMode = gridMode;
        if (gridMode) {
            setExpanded(true);
        }
        list.setLayoutOrientation(gridMode ? JList.HORIZONTAL_WRAP : JList.VERTICAL);
        list.setVisibleRowCount(gridMode ? -1 : 8);
        if (!gridMode) {
            list.setFixedCellWidth(160);
        }
        rail.setVisible(!gridMode);
        refreshLanguage();
        updateGridButton();
        gridChanged.accept(gridMode);
        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> {
            followCurrent();
            pump();
            if (this.gridMode) {
                list.requestFocusInWindow();
            }
        });
    }

    private void openPage(int index) {
        if (index < 0 || index >= pages.size()) {
            return;
        }
        setGridMode(false);
        navigate.accept(index + 1);
    }

    public void setExpanded(boolean expanded) {
        if (!expanded) {
            cancelPendingRequests();
            setGridMode(false);
        }
        if (this.expanded == expanded) {
            return;
        }
        this.expanded = expanded;
        updateGridButton();
        scroll.setVisible(expanded);
        setPreferredSize(new Dimension(expanded ? 200 : 16, 0));
        refreshLanguage();
        revalidate();
        repaint();
        if (expanded) {
            SwingUtilities.invokeLater(() -> {
                followCurrent();
                pump();
            });
        }
    }

    private void cancelPendingRequests() {
        pending.values().forEach(wanted -> wanted.set(false));
    }

    public void showPages(List<PageRef> newPages) {
        if (pages == newPages) {
            return;
        }
        setGridMode(false);
        cancelPendingRequests();
        pages = newPages;
        gridButton.setEnabled(!newPages.isEmpty());
        cache.clear();
        first = last = -1;
        list.setModel(new PageListModel(List.copyOf(newPages)));
        pump();
    }

    public void showSpread(int firstPage, int lastPage) {
        boolean changed = first != firstPage - 1 || last != lastPage - 1;
        first = firstPage - 1;
        last = lastPage - 1;
        if (changed) {
            list.setSelectedIndex(first);
            followCurrent();
        }
        list.repaint();
        pump();
    }

    private void followCurrent() {
        if (expanded && isVisible() && first >= 0) {
            Rectangle spread = list.getCellBounds(first, last);
            if (spread != null && spread.height <= list.getVisibleRect().height) {
                list.scrollRectToVisible(spread);
            } else {
                list.ensureIndexIsVisible(first);
            }
        }
    }

    private void pump() {
        if (!expanded || !isShowing() || pages.isEmpty()) {
            return;
        }
        int start = list.getFirstVisibleIndex(), end = list.getLastVisibleIndex();
        if (start < 0 || end < start) {
            return;
        }
        windowStart = Math.max(0, start - PREFETCH_MARGIN);
        windowEnd = Math.min(pages.size() - 1, end + PREFETCH_MARGIN);
        cache.keySet().removeIf(index -> index < windowStart || index > windowEnd);
        pending.forEach((index, wanted) -> {
            if (index < windowStart || index > windowEnd) {
                wanted.set(false);
            }
        });
        // Visible pages first, then alternate forward/backward by distance from the viewport.
        for (int index = start; index <= end && pending.size() < MAX_PENDING; index++) {
            request(index);
        }
        for (int distance = 1; distance <= PREFETCH_MARGIN && pending.size() < MAX_PENDING; distance++) {
            if (end + distance <= windowEnd) {
                request(end + distance);
            }
            if (start - distance >= windowStart && pending.size() < MAX_PENDING) {
                request(start - distance);
            }
        }
    }

    private void request(int index) {
        if (cache.containsKey(index) || pending.containsKey(index)) {
            return;
        }
        AtomicBoolean wanted = new AtomicBoolean(true);
        pending.put(index, wanted);
        loader.load(index, pages.get(index), wanted::get, image -> {
            pending.remove(index);
            if (expanded && isShowing() && wanted.get() && index >= windowStart && index <= windowEnd) {
                // Empty icon marks an unreadable page; retry only if it leaves and re-enters the window.
                cache.put(index, image == null ? new ImageIcon() : new ThumbnailIcon(image));
                list.repaint(list.getCellBounds(index, index));
            }
            pump();
        });
    }
}
