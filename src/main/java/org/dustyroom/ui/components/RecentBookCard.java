package org.dustyroom.ui.components;

import org.dustyroom.be.models.state.BookState;
import org.dustyroom.be.models.view.RecentBook;
import org.dustyroom.ui.listeners.MouseClickListener;
import org.dustyroom.be.utils.FileUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

final class RecentBookCard extends JPanel {
    private final JLabel cover = new JLabel(new ReaderIcon(ReaderIconKind.OPEN));
    private final JButton title = new JButton();
    private final JLabel progress = new JLabel();
    private final JLabel availability = new JLabel();
    private final JLabel subtitle = new JLabel("", SwingConstants.CENTER);
    private final JButton trash;
    private final JButton chapters = new JButton();
    private final JProgressBar bar = new JProgressBar(0, 100);
    private RecentBook current;

    RecentBookCard(String id, Consumer<String> open, Consumer<List<String>> remove) {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        setCoverSize(new Dimension(72, 104));
        cover.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel heading = new JPanel(new BorderLayout());
        heading.add(title, BorderLayout.NORTH);
        heading.add(subtitle, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.add(cover, BorderLayout.WEST);
        add(body, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        add(footer, BorderLayout.SOUTH);
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
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
        footer.add(chapters, BorderLayout.WEST);
        footer.add(trash, BorderLayout.EAST);
        subtitle.putClientProperty("html.disable", true);
        MouseClickListener continueReading = new MouseClickListener(event -> {
            if (SwingUtilities.isLeftMouseButton(event) && title.isEnabled()) {
                title.doClick(0);
            }
        });
        for (JComponent surface : List.of(this, heading, body, footer, details, cover, progress, subtitle, availability, bar)) {
            surface.addMouseListener(continueReading);
        }
        details.add(Box.createVerticalGlue());
        for (JComponent item : List.of(progress, bar, availability)) {
            item.setAlignmentX(LEFT_ALIGNMENT);
            details.add(item);
        }
        details.add(Box.createVerticalGlue());
        body.add(details, BorderLayout.CENTER);
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

    void setCoverSize(Dimension size) {
        cover.setPreferredSize(size);
        setPreferredSize(new Dimension(350, size.height + 108));
        if (current != null && current.getCover() != null) {
            cover.setIcon(new RecentCoverIcon(current.getCover(), size));
        }
    }

    void update(RecentBook book) {
        current = book;
        boolean grouped = book.getChapters().size() > 1;
        File file = new File(book.getState().getSourceId());
        File parent = file.getParentFile();
        String heading = book.getTitle();
        String detail = parent == null ? "" : parent.getName();
        if (parent != null && !detail.isBlank() && FileUtils.isSupportedArchive(file.getName())) {
            heading = detail;
            detail = file.getName();
        }
        subtitle.setText(detail);
        subtitle.setVisible(!detail.isBlank());
        String scope = grouped ? "all " + book.getChapters().size() + " chapters" : book.getTitle();
        String label = "Clear reading history for " + scope;
        trash.setToolTipText(label);
        trash.getAccessibleContext().setAccessibleName(label);
        chapters.setVisible(grouped);
        chapters.setText("Chapters (" + book.getChapters().size() + ") ▾");
        chapters.setToolTipText("Show recently read chapters. Continue: " + file.getName());
        // Treat filenames as plain text, never as Swing HTML markup.
        title.putClientProperty("html.disable", true);
        title.setText(heading);
        title.setEnabled(book.isAvailable());
        title.getAccessibleContext().setAccessibleName("Continue " + heading + ", " + detail + ", " + book.getProgress());
        progress.setText(book.getProgress());
        bar.setValue((int) (100L * book.getState().getPageNumber() / Math.max(1, book.getState().getPageCount())));
        availability.setText("Moved, missing or unavailable");
        availability.setVisible(!book.isAvailable());
        availability.setToolTipText(book.getState().getSourceId());
        if (book.getCover() != null) {
            cover.setIcon(new RecentCoverIcon(book.getCover(), cover.getPreferredSize()));
        } else {
            cover.setIcon(new ReaderIcon(ReaderIconKind.OPEN));
        }
    }
}
