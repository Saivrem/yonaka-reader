package org.dustyroom.be.library;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.iterators.ImageIterator;
import org.dustyroom.be.iterators.ImageIteratorException;
import org.dustyroom.be.iterators.ImageIteratorFactory;
import org.dustyroom.be.models.library.LibraryImport;
import org.dustyroom.be.models.library.LibraryScan;
import org.dustyroom.be.models.library.LibrarySource;
import org.dustyroom.be.utils.FileUtils;
import org.dustyroom.be.utils.NaturalOrderComparator;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;

/**
 * Imports the selected directory and its immediate child directories.
 */
public final class LibraryScanner {
    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    public LibraryScan scan(Path directory, BooleanSupplier cancelled) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(I18n.text("errors.select_a_real_folder_not_a_symbolic_link"));
        }
        List<LibraryImport> works = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Path> folders = new ArrayList<>();
        List<LibrarySource> loose = sources(root, root, folders, cancelled, warnings);
        for (LibrarySource source : loose) {
            works.add(new LibraryImport(source.getPath(), source.getTitle(), List.of(source)));
        }
        for (Path folder : folders) {
            checkCancelled(cancelled);
            try {
                List<LibrarySource> chapters = sources(root, folder, null, cancelled, warnings);
                if (!chapters.isEmpty()) {
                    works.add(new LibraryImport(folder.toString(), folder.getFileName().toString(), chapters));
                }
            } catch (IOException exception) {
                warnings.add(I18n.text("scan.folder", root.relativize(folder)));
            }
        }
        works.sort(Comparator.comparing(LibraryImport::getTitle, NaturalOrderComparator.INSTANCE));
        checkCancelled(cancelled);
        return new LibraryScan(works, warnings);
    }

    private List<LibrarySource> sources(Path root, Path directory, List<Path> childFolders,
                                        BooleanSupplier cancelled, List<String> warnings) throws IOException {
        checkCancelled(cancelled);
        List<LibrarySource> chapters = new ArrayList<>();
        boolean images = false;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                checkCancelled(cancelled);
                if (childFolders != null && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    childFolders.add(child);
                } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                    String name = child.getFileName().toString();
                    if (FileUtils.isSupportedImage(name)) {
                        images = true;
                    } else if (FileUtils.isSupportedPdf(name)) {
                        try (ImageIterator pdf = ImageIteratorFactory.create(child.toFile())) {
                            chapters.add(new LibrarySource(child.toString(), name, false));
                        } catch (ImageIteratorException exception) {
                            warnings.add(exception.getMessage());
                        }
                    } else if (FileUtils.isSupportedArchive(name) && readableArchive(root, child, cancelled, warnings)) {
                        chapters.add(new LibrarySource(child.toString(), name, false));
                    }
                }
            }
        } catch (DirectoryIteratorException exception) {
            throw exception.getCause();
        }
        chapters.sort(Comparator.comparing(LibrarySource::getTitle, NaturalOrderComparator.INSTANCE));
        if (images) {
            String title = directory.getFileName() == null ? directory.toString() : directory.getFileName().toString();
            chapters.addFirst(new LibrarySource(directory.toString(), title, true));
        }
        return chapters;
    }

    private boolean readableArchive(Path root, Path archive, BooleanSupplier cancelled, List<String> warnings) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            boolean hasPages = zip
                    .stream()
                    .anyMatch(entry -> {
                        checkCancelled(cancelled);
                        return !entry.isDirectory() && FileUtils.isSupportedImage(entry.getName());
                    });
            if (hasPages) {
                return true;
            }
            warnings.add(I18n.text("scan.empty", root.relativize(archive)));
        } catch (IOException exception) {
            warnings.add(I18n.text("scan.zip", root.relativize(archive)));
        }
        return false;
    }
}
