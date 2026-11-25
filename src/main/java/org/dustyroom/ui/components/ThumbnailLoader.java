package org.dustyroom.ui.components;

import org.dustyroom.be.models.book.PageRef;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

@FunctionalInterface
public interface ThumbnailLoader {
    void load(int index, PageRef page, BooleanSupplier wanted, Consumer<BufferedImage> result);
}
