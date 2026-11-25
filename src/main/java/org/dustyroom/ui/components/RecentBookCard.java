package org.dustyroom.ui.components;

import org.dustyroom.ui.i18n.UiText;
import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.state.BookState;
import org.dustyroom.be.models.view.RecentBook;
import org.dustyroom.be.utils.FileUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

final class RecentBookCard extends BookCard {
    private final JButton trash;
    private final JButton chapters = new JButton();
    private RecentBook current;

    RecentBookCard(String id, Consumer<String> open, Consumer<List<String>> remove) {
        super(id, () -> open.accept(id));
        trash = trashButton(I18n.text("ui.remove_from_recent"), () -> remove.accept(current.getChapters()
                .stream()
                .map(BookState::getSourceId)
                .toList()));
        chapters.putClientProperty("JButton.buttonType", "borderless");
        chapters.addActionListener(_ -> showChapters(open, remove));
        setFooter(chapters, trash);
        UiText.bind(this, "content", RecentBookCard::refreshLanguage);
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
            row.add(trashButton(I18n.text("recent.clearBook", name), () -> {
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

    private void refreshLanguage() {
        if (current != null) {
            update(current);
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
        String scope = grouped ? I18n.text("recent.allChapters", book.getChapters().size()) : book.getTitle();
        String label = I18n.text("recent.clearBook", scope);
        trash.setToolTipText(label);
        trash.getAccessibleContext().setAccessibleName(label);
        chapters.setVisible(grouped);
        chapters.setText(I18n.text("recent.chapters", book.getChapters().size()));
        chapters.setToolTipText(I18n.text("recent.continueChapter", file.getName()));
        setHeading(heading, detail, book.isAvailable(), I18n.text("recent.continue", heading, detail, book.getProgress()));
        setProgress(book.getProgress(), book.getState().getPageNumber(), book.getState().getPageCount());
        setAvailability(book.isAvailable() ? "" : I18n.text("ui.moved_missing_or_unavailable"));
        setCover(book.getCover());
    }
}
