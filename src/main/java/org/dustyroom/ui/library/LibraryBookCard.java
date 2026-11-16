package org.dustyroom.ui.library;

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
        setHeading(work.getTitle(), resume.getSource().getTitle(), true, "View chapters of " + work.getTitle());
        if (resume.isFinished()) {
            setProgress(resume.getProgress(), 1, 1);
        } else {
            setProgress(resume.getProgress(), resume.getPage(), resume.getPageCount());
        }
        setAvailability(snapshot.hasMissingChapters(work) ? "Some sources are unavailable" : "");

        int count = work.getChapters().size();
        String summary = count + (count == 1 ? " chapter" : " chapters") + " · " + work.getCompleted() + " finished";
        JButton chapters = new JButton(summary);
        chapters.putClientProperty("JButton.buttonType", "borderless");
        chapters.addActionListener(_ -> details.run());

        JButton read = new JButton(resume.getLastRead() > 0 ? "Continue reading" : "Start reading");
        read.setEnabled(snapshot.getAvailable().contains(resume.getId()));
        read.addActionListener(_ -> open.accept(resume));
        setFooter(chapters, read);
    }
}
