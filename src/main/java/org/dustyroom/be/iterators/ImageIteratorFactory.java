package org.dustyroom.be.iterators;

import org.dustyroom.be.i18n.I18n;
import lombok.experimental.UtilityClass;

import java.io.File;

import static org.dustyroom.be.utils.FileUtils.*;

@UtilityClass
public class ImageIteratorFactory {

    public static ImageIterator create(File source) {
        if (isZipFile.test(source)) {
            return new ZipIterator(source);
        }
        if (isImage.test(source) || isDirectory.test(source)) {
            return new FileImageIterator(source);
        }
        throw new ImageIteratorException(I18n.text("errors.unsupported_or_empty_image_source"), source);
    }
}
