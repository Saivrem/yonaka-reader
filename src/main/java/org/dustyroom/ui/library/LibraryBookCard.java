package org.dustyroom.ui.library;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.library.LibraryChapter;
import org.dustyroom.be.models.library.LibraryWork;
import org.dustyroom.be.models.library.LibrarySnapshot;
import org.dustyroom.ui.components.BookCard;

import javax.swing.*;
import java.util.function.Consumer;

final class LibraryBookCard extends BookCard {
    LibraryBookCard(LibraryWork work, LibrarySnapshot snapshot, Runnable details, Consumer<LibraryChapter> open) {
        super(work.getRoot(), details);
        LibraryChapter resume = work.getResumeChapter();
        setHeading(work.getTitle(), resume.getSource().getTitle(), true, I18n.text("library.viewChapters", work.getTitle()));
        if (resume.isFinished()) {
            setProgress(resume.getProgress(), 1, 1);
        } else {
            setProgress(resume.getProgress(), resume.getPage(), resume.getPageCount());
        }
        setAvailability(snapshot.hasMissingChapters(work) ? I18n.text("ui.some_sources_are_unavailable") : "");

        int count = work.getChapters().size();
        String summary = I18n.text("library.chapterCount", count, work.getCompleted());
        JButton chapters = new JButton(summary);
        chapters.putClientProperty("JButton.buttonType", "borderless");
        chapters.addActionListener(_ -> details.run());

        JButton read = new JButton(resume.getLastRead() > 0 ? I18n.text("ui.continue_reading") : I18n.text("ui.start_reading"));
        read.setEnabled(snapshot.getAvailable().contains(resume.getId()));
        read.addActionListener(_ -> open.accept(resume));
        setFooter(chapters, read);
    }
}
