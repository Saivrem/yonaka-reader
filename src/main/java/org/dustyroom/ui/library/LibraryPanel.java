package org.dustyroom.ui.library;

import org.dustyroom.ui.i18n.UiText;
import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.library.LibrarySnapshot;
import org.dustyroom.be.models.library.LibraryWork;
import org.dustyroom.ui.components.BookCard;
import org.dustyroom.ui.listeners.DocumentChangeListener;
import org.dustyroom.ui.listeners.MouseClickListener;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LibraryPanel extends JPanel {
    private final JScrollPane collection = new JScrollPane();
    private final LibraryShelf shelf;
    private final LibraryController controller;
    private final JPanel body = new JPanel(new BorderLayout());
    private final JTextField search = new JTextField(18);
    private final JComboBox<String> filter = UiText.combo("ui.all", "ui.unread", "ui.reading", "ui.finished", "ui.unavailable");
    private final JComboBox<String> sort = UiText.combo("ui.title", "ui.last_read", "ui.date_added");
    private final JLabel status = UiText.label("library.emptyHint");
    private final JLabel workCount = new JLabel();
    private LibrarySnapshot snapshot = new LibrarySnapshot(List.of(), Set.of());
    private String selectedWork;
    private String selectedChapter;

    LibraryPanel(LibraryController controller) {
        super(new BorderLayout(12, 12));
        this.controller = controller;
        shelf = new LibraryShelf(collection.getViewport(), this::card, work ->
                controller.cancelCover(work.getChapters().getFirst()));
        collection.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        collection.setViewportView(shelf);
        collection.getVerticalScrollBar().setUnitIncrement(24);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel top = new JPanel(new BorderLayout(8, 8));
        JLabel heading = UiText.label("ui.library");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 20f));
        top.add(heading, BorderLayout.NORTH);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(button("ui.add_folder", controller::chooseFolder));
        controls.add(button("ui.refresh", controller::refresh));
        controls.add(UiText.label("ui.search"));
        UiText.accessible(search, "ui.search_library_by_title_or_path");
        controls.add(search);
        controls.add(filter);
        controls.add(sort);
        top.add(controls, BorderLayout.CENTER);
        JPanel summary = new JPanel(new BorderLayout(12, 0));
        summary.add(status, BorderLayout.CENTER);
        summary.add(workCount, BorderLayout.LINE_END);
        top.add(summary, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentChangeListener(this::changed));
        filter.addActionListener(_ -> changed());
        sort.addActionListener(_ -> changed());
        UiText.bind(this, "content", LibraryPanel::render);
    }

    private static JButton button(String title, Runnable action) {
        JButton button = UiText.button(title);
        button.addActionListener(_ -> action.run());
        return button;
    }

    public void showCollection() {
        selectedChapter = null;
        changed();
    }

    private void changed() {
        selectedWork = null;
        collection.getViewport().setViewPosition(new Point());
        render();
    }

    void status(String text) {
        if (text.isEmpty()) {
            UiText.bind(status, "text", target -> target.setText(""));
        } else {
            UiText.text(status, text);
        }
    }

    void showSnapshot(LibrarySnapshot snapshot) {
        this.snapshot = snapshot;
        int count = snapshot.getWorks().size();
        UiText.text(workCount, "library.workCount", count);
        status("");
        render();
    }

    private boolean matches(LibraryWork work, String query, int filterIndex) {
        String text = work.getTitle() + " " + work.getRoot();
        if (!text.toLowerCase(Locale.ROOT).contains(query)) {
            return false;
        }

        return switch (filterIndex) {
            case 1 -> work.getLastRead() == 0 && work.getCompleted() == 0;
            case 2 -> {
                long completed = work.getCompleted();
                boolean started = completed > 0 || work.getLastRead() > 0;
                yield started && completed < work.getChapters().size();
            }
            case 3 -> !work.getChapters().isEmpty() && work.getCompleted() == work.getChapters().size();
            case 4 -> snapshot.hasMissingChapters(work);
            default -> true;
        };
    }

    private void render() {
        controller.invalidateCovers();
        body.removeAll();
        LibraryWork selected = snapshot.getWorks()
                .stream()
                .filter(work -> work.getId().equals(selectedWork))
                .findFirst()
                .orElse(null);
        if (selected != null) {
            shelf.showWorks(List.of());
            details(selected);
        } else {
            shelf();
        }
        body.revalidate();
        body.repaint();
    }

    private void shelf() {
        Comparator<LibraryWork> order = switch (sort.getSelectedIndex()) {
            case 1 -> Comparator.comparingLong(LibraryWork::getLastRead).reversed();
            case 2 -> Comparator.comparingLong(LibraryWork::getAddedAt).reversed();
            default -> Comparator.comparing(LibraryWork::getTitle, String.CASE_INSENSITIVE_ORDER);
        };
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        int filterIndex = filter.getSelectedIndex();
        List<LibraryWork> works = snapshot.getWorks()
                .stream()
                .filter(work -> matches(work, query, filterIndex))
                .sorted(order)
                .toList();
        shelf.showWorks(works);
        if (works.isEmpty()) {
            body.add(UiText.centeredLabel("library.noMatches"), BorderLayout.CENTER);
        } else {
            body.add(collection, BorderLayout.CENTER);
        }
    }

    private BookCard card(LibraryWork work) {
        LibraryBookCard card = new LibraryBookCard(work, snapshot, () -> {
            selectedWork = work.getId();
            render();
        }, controller::open, () -> controller.remove(work));
        controller.cover(work.getChapters().getFirst(), card::setCover);
        return card;
    }

    private void details(LibraryWork work) {
        JPanel heading = new JPanel(new GridLayout(0, 1, 4, 4));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.add(button("ui.library_label", () -> {
            selectedWork = null;
            render();
        }));
        actions.add(button("ui.rename", () -> controller.rename(work)));
        actions.add(button("ui.remove_from_library", () -> controller.remove(work)));
        heading.add(actions);
        heading.add(new JLabel(work.getTitle()));
        JTextField path = new JTextField(work.getRoot());
        path.setEditable(false);
        heading.add(path);
        heading.add(UiText.label("library.unavailableHint"));
        body.add(heading, BorderLayout.NORTH);
        JTable chapters = new JTable(new LibraryChapterTableModel(work.getChapters(), snapshot.getAvailable()));
        chapters.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chapters.setRowHeight(32);
        int selectedRow = 0;
        for (int row = 0; row < work.getChapters().size(); row++) {
            if (work.getChapters().get(row).getId().equals(selectedChapter)) {
                selectedRow = row;
            }
        }
        chapters.setRowSelectionInterval(selectedRow, selectedRow);
        chapters.getSelectionModel().addListSelectionListener(_ -> {
            int row = chapters.getSelectedRow();
            if (row >= 0) {
                selectedChapter = work.getChapters().get(row).getId();
            }
        });
        Runnable open = () -> {
            if (chapters.getSelectedRow() >= 0) {
                controller.open(work.getChapters().get(chapters.getSelectedRow()));
            }
        };
        chapters.addMouseListener(new MouseClickListener(event -> {
            if (event.getClickCount() == 2 && chapters.rowAtPoint(event.getPoint()) >= 0 && SwingUtilities.isLeftMouseButton(event)) {
                open.run();
            }
        }));
        JScrollPane chapterScroll = new JScrollPane(chapters);
        chapterScroll.setColumnHeaderView(chapters.getTableHeader());
        body.add(chapterScroll, BorderLayout.CENTER);
        JPanel actionsBottom = new JPanel();
        actionsBottom.add(button("ui.open_selected_chapter", open));
        actionsBottom.add(button("ui.toggle_finished", () -> {
            int row = chapters.getSelectedRow();
            if (row >= 0) {
                controller.finish(work.getChapters().get(row));
            }
        }));
        body.add(actionsBottom, BorderLayout.SOUTH);
    }
}
