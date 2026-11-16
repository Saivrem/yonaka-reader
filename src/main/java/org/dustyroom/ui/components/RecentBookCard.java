package org.dustyroom.ui.components;

import org.dustyroom.be.models.state.BookState;
import org.dustyroom.be.models.view.RecentBook;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import org.dustyroom.ui.listeners.MouseClickListener;
import java.util.function.Consumer;

final class RecentBookCard extends JPanel {
    private final JLabel cover = new JLabel(new ReaderIcon(ReaderIconKind.OPEN));
    private final JButton title = new JButton();
    private final JLabel progress = new JLabel();
    private final JLabel availability = new JLabel();
    private final JLabel folder = new JLabel();
    private final JButton trash;
    private final JButton chapters = new JButton();
    private final JProgressBar bar = new JProgressBar(0, 100);
    private RecentBook current;

    RecentBookCard(String id, Consumer<String> open, Consumer<List<String>> remove) {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        setPreferredSize(new Dimension(350, 176));
        cover.setPreferredSize(new Dimension(72, 104));
        cover.setHorizontalAlignment(SwingConstants.CENTER);
        add(cover, BorderLayout.WEST);
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        title.setHorizontalAlignment(SwingConstants.LEFT);
        title.putClientProperty("JButton.buttonType", "borderless");
        title.addActionListener(_ -> open.accept(id));
        title.setToolTipText(id);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
        trash = trashButton("Remove from recent", () -> remove.accept(current.getChapters()
                .stream()
                .map(BookState::getSourceId)
                .toList()));
        chapters.putClientProperty("JButton.buttonType", "borderless");
        chapters.addActionListener(_ -> showChapters(open, remove));
        folder.putClientProperty("html.disable", true);
        MouseClickListener continueReading = new MouseClickListener(event -> {
            if (SwingUtilities.isLeftMouseButton(event) && title.isEnabled()) {
                title.doClick(0);
            }
        });
        for (JComponent surface : List.of(this, details, cover, progress, folder, availability, bar)) {
            surface.addMouseListener(continueReading);
        }
        for (JComponent item : List.of(title, folder, progress, bar, chapters, availability, trash)) {
            item.setAlignmentX(LEFT_ALIGNMENT);
            details.add(item);
        }
        add(details, BorderLayout.CENTER);
    }

    private static JButton trashButton(String label, Runnable remove) {
        JButton button = new JButton(new ReaderIcon(ReaderIconKind.TRASH, 16));
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.addActionListener(_ -> remove.run());
        return button;
    }

    private void showChapters(Consumer<String> open, Consumer<List<String>> remove) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), current.getTitle(),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
        for (BookState chapter : current.getChapters()) {
            String id = chapter.getSourceId();
            String name = new File(id).getName();
            JButton entry = new JButton();
            entry.putClientProperty("html.disable", true);
            entry.setText(name + " — " + chapter.getPageNumber() + "/" + chapter.getPageCount());
            entry.setHorizontalAlignment(SwingConstants.LEADING);
            entry.addActionListener(_ -> {
                dialog.dispose();
                open.accept(id);
            });
            JPanel row = new JPanel(new BorderLayout());
            row.add(entry, BorderLayout.CENTER);
            row.add(trashButton("Clear reading history for " + name, () -> {
                dialog.dispose();
                remove.accept(List.of(id));
            }), BorderLayout.EAST);
            rows.add(row);
        }
        JPanel content = new JPanel(new BorderLayout());
        content.add(rows, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(content);
        scroll.setPreferredSize(new Dimension(440, Math.min(400, rows.getPreferredSize().height + 12)));
        dialog.setContentPane(scroll);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    void update(RecentBook book) {
        current = book;
        boolean grouped = book.getChapters().size() > 1;
        File file = new File(book.getState().getSourceId());
        File parent = file.getParentFile();
        folder.setText(grouped ? file.getName() : parent == null ? "" : parent.getName());
        String scope = grouped ? "all " + book.getChapters().size() + " chapters" : book.getTitle();
        String label = "Clear reading history for " + scope;
        trash.setToolTipText(label);
        trash.getAccessibleContext().setAccessibleName(label);
        chapters.setVisible(grouped);
        chapters.setText("Chapters (" + book.getChapters().size() + ") ▾");
        chapters.setToolTipText("Show recently read chapters. Continue: " + new File(book.getState().getSourceId()).getName());
        // Treat filenames as plain text, never as Swing HTML markup.
        title.putClientProperty("html.disable", true);
        title.setText(book.getTitle());
        title.setEnabled(book.isAvailable());
        title.getAccessibleContext().setAccessibleName("Continue " + book.getTitle() + ", " + book.getProgress());
        progress.setText(book.getProgress());
        bar.setValue((int) (100L * book.getState().getPageNumber() / Math.max(1, book.getState().getPageCount())));
        availability.setText(book.isAvailable() ? " " : "Moved, missing or unavailable");
        availability.setToolTipText(book.getState().getSourceId());
        if (book.getCover() != null) {
            cover.setIcon(new RecentCoverIcon(book.getCover()));
        } else {
            cover.setIcon(new ReaderIcon(ReaderIconKind.OPEN));
        }
    }
}
