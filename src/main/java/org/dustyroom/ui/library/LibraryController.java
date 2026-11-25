package org.dustyroom.ui.library;

import org.dustyroom.ui.i18n.UiText;
import org.dustyroom.be.i18n.I18n;
import lombok.Getter;
import lombok.experimental.Accessors;

import org.dustyroom.be.library.LibraryInspector;
import org.dustyroom.be.library.LibraryScanner;
import org.dustyroom.be.models.library.*;
import org.dustyroom.be.persistence.ReaderStore;
import org.dustyroom.ui.loading.AsyncViewerSession;
import org.dustyroom.ui.loading.BackgroundTask;
import org.dustyroom.ui.loading.BookCoverLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * UI orchestration only. Import scanning and persistence never run on the EDT.
 */
public final class LibraryController {
    private final AsyncViewerSession session;
    private final Consumer<File> openBook;
    private final AtomicLong coverGeneration = new AtomicLong();
    @Getter
    @Accessors(fluent = true)
    private final LibraryPanel panel;
    private final Deque<LibraryCoverRequest> covers = new ArrayDeque<>();
    private boolean coverPending;
    private long request;
    private boolean importing;
    private SwingWorker<LibrarySnapshot, Void> availability;

    public LibraryController(AsyncViewerSession session, Consumer<File> openBook) {
        this.session = session;
        this.openBook = openBook;
        panel = new LibraryPanel(this);
    }

    public void refresh() {
        long current = ++request;
        if (availability != null) {
            availability.cancel(true);
        }
        invalidateCovers();
        panel.status("ui.checking_library");
        session.libraryTask(ReaderStore::libraryWorks, works -> {
            if (current != request) {
                return;
            }
            availability = new BackgroundTask<>(_ -> LibraryInspector.inspect(works), task -> {
                if (current != request) {
                    return;
                }
                try {
                    panel.showSnapshot(task.get());
                } catch (CancellationException ignored) {
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    error(I18n.text("errors.checkLibrary", exception.getCause().getMessage()));
                }
            });
            availability.execute();
        }, message -> {
            if (current == request) {
                error(message);
            }
        });
    }

    void invalidateCovers() {
        coverGeneration.incrementAndGet();
        covers.clear();
    }

    void cover(LibraryChapter chapter, Consumer<BufferedImage> loaded) {
        covers.addLast(new LibraryCoverRequest(coverGeneration.get(), chapter, loaded));
        pumpCover();
    }

    void cancelCover(LibraryChapter chapter) {
        covers.removeIf(request -> request.chapter().equals(chapter));
    }

    private void pumpCover() {
        if (coverPending || covers.isEmpty()) {
            return;
        }
        LibraryCoverRequest next = covers.removeFirst();
        coverPending = true;
        session.libraryTask(_ -> {
            if (next.generation() != coverGeneration.get()) {
                return null;
            }
            return BookCoverLoader.load(next.chapter().getSource().getPath());
        }, image -> {
            coverPending = false;
            if (next.generation() == coverGeneration.get()) {
                next.loaded().accept(image);
            }
            pumpCover();
        }, _ -> {
            coverPending = false;
            pumpCover();
        });
    }

    void open(LibraryChapter chapter) {
        // The normal reader validates the source and restores page, direction and spread mode.
        invalidateCovers();
        openBook.accept(new File(chapter.getSource().getPath()));
    }

    void rename(LibraryWork work) {
        String name = JOptionPane.showInputDialog(panel, I18n.text("ui.library_title_files_will_not_be_renamed"), work.getTitle());
        if (name != null && !name.isBlank()) {
            mutate(store -> store.renameLibrary(work.getId(), name));
        }
    }

    void remove(LibraryWork work) {
        String message = I18n.text("library.remove", work.getTitle());
        if (JOptionPane.showConfirmDialog(panel, message,
                I18n.text("ui.remove_from_library_label"), JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            mutate(store -> store.removeLibrary(work.getId()));
        }
    }

    void finish(LibraryChapter chapter) {
        mutate(store -> store.finishChapter(chapter.getId(), !chapter.isFinished()));
    }

    private void mutate(Consumer<ReaderStore> operation) {
        session.libraryTask(store -> {
            operation.accept(store);
            return true;
        }, _ -> refresh(), this::error);
    }

    void chooseFolder() {
        if (importing) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.text("ui.add_books_from_a_folder"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        importFolder(chooser.getSelectedFile().toPath());
    }

    public void importFolder(Path directory) {
        if (importing) {
            return;
        }
        File selected = directory.toFile();
        importing = true;
        JDialog progress = new JDialog(SwingUtilities.getWindowAncestor(panel), I18n.text("ui.scanning_folder"), Dialog.ModalityType.APPLICATION_MODAL);
        JLabel label = UiText.label("library.scanning");
        JButton cancel = UiText.button("ui.cancel");
        progress.add(label, BorderLayout.CENTER);
        progress.add(cancel, BorderLayout.SOUTH);
        progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        SwingWorker<LibraryScan, Void> scan = new BackgroundTask<>(
                cancelled -> new LibraryScanner().scan(selected.toPath(), cancelled),
                task -> {
                    importing = false;
                    progress.dispose();
                    try {
                        preview(task.get());
                    } catch (CancellationException ignored) {
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException exception) {
                        error(I18n.text("errors.scan", exception.getCause().getMessage()));
                    }
                });
        cancel.addActionListener(_ -> scan.cancel(true));
        progress.pack();
        progress.setLocationRelativeTo(panel);
        scan.execute();
        progress.setVisible(true);
    }

    private void preview(LibraryScan found) {
        String warningsText = String.join("\n", found.warnings());
        if (found.works().isEmpty()) {
            error(I18n.text("library.noBooks", warningsText));
            return;
        }
        LibraryImportTableModel model = new LibraryImportTableModel(found.works());
        JTable chapters = new JTable(model);
        chapters.getColumnModel().getColumn(0).setMaxWidth(65);
        chapters.putClientProperty("terminateEditOnFocusLost", true);
        JPanel preview = new JPanel(new BorderLayout(8, 8));
        JScrollPane scroll = new JScrollPane(chapters);
        scroll.setPreferredSize(new Dimension(580, 300));
        preview.add(scroll, BorderLayout.CENTER);
        JTextArea explanation = new JTextArea(I18n.text("library.importHint", warningsText));
        explanation.setEditable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        JScrollPane warnings = new JScrollPane(explanation);
        warnings.setPreferredSize(new Dimension(580, 85));
        preview.add(warnings, BorderLayout.SOUTH);
        while (JOptionPane.showConfirmDialog(panel, preview, I18n.text("ui.import_preview"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (chapters.isEditing()) {
                chapters.getCellEditor().stopCellEditing();
            }
            List<LibraryImport> selected = model.selection();
            boolean missingTitle = selected
                    .stream()
                    .anyMatch(work -> work.getTitle().isBlank());
            if (selected.isEmpty() || missingTitle) {
                error(I18n.text("library.selectionRequired"));
                continue;
            }
            mutate(store -> store.importWorks(selected));
            return;
        }
    }

    private void error(String message) {
        panel.status("library.operationFailed");
        JOptionPane.showMessageDialog(panel, message, I18n.text("ui.library"), JOptionPane.ERROR_MESSAGE);
    }
}
