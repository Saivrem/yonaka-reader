package org.dustyroom.be.iterators;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.animation.AnimationClip;
import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.be.models.book.Picture;
import org.dustyroom.be.models.direction.Direction;
import org.dustyroom.be.utils.FileUtils;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.dustyroom.be.utils.FileUtils.getSortedFilesFromParent;

public interface ImageIterator extends AutoCloseable {
    void init();

    File getVolumeRoot();

    void setFile(File file);

    Picture next();

    Picture prev();

    Picture first();

    Picture last();

    Picture nextVol();

    Picture prevVol();

    List<PageRef> pages();

    int initialPageIndex();

    Picture load(PageRef page);

    default Picture load(PageRef page, int maxWidth, int maxHeight) {
        return load(page);
    }

    /** Captures a read that owns its resources and may run after the iterator changes volume or closes. */
    Supplier<Picture> detachedLoad(PageRef page);

    default Supplier<Picture> detachedLoad(PageRef page, int maxWidth, int maxHeight) {
        return detachedLoad(page);
    }

    default AnimationClip animation(PageRef page) {
        return null;
    }

    PageRef switchToNextVolume();

    PageRef switchToPreviousVolume();

    @Override
    default void close() {
    }

    default PageRef switchVolume(Predicate<File> fileType, Direction dir) {
        File target = findVolume(fileType, dir, true);
        if (target == null) {
            return null;
        }
        setFile(target);
        init();
        List<PageRef> pages = pages();
        return pages.isEmpty() ? null : pages.get(initialPageIndex());
    }

    default File adjacentVolume(Direction direction) {
        if (FileUtils.isPdf.test(getVolumeRoot())) {
            return findVolume(FileUtils.isPdf, direction, false);
        }
        Predicate<File> volumes = FileUtils.isZipFile.test(getVolumeRoot()) ? FileUtils.isZipFile : FileUtils.containsImages;
        return findVolume(volumes, direction, false);
    }

    private File findVolume(Predicate<File> fileType, Direction dir, boolean wrap) {
        File filePath = getVolumeRoot();
        List<File> sortedFiles = getSortedFilesFromParent(filePath, fileType);
        if (sortedFiles.isEmpty()) {
            if (filePath == null || !fileType.test(filePath)) {
                throw new ImageIteratorException(I18n.text("errors.current_volume_no_longer_exists"), filePath);
            }
            return null;
        }

        int currentIndex = sortedFiles.indexOf(filePath);
        if (currentIndex < 0) {
            throw new ImageIteratorException(I18n.text("errors.current_volume_no_longer_exists"), filePath);
        }
        int target = currentIndex + dir.getShift();
        if (!wrap && (target < 0 || target >= sortedFiles.size())) {
            return null;
        }
        return sortedFiles.get(Math.floorMod(target, sortedFiles.size()));
    }
}
