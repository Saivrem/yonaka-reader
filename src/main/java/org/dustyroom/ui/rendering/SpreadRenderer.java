package org.dustyroom.ui.rendering;

import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.be.models.book.PictureMetadata;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class SpreadRenderer {

    public String buildTitle(PictureMetadata metadata, PageRef secondPage) {
        if (secondPage == null) {
            return String.format("%s - %s", metadata.getFileName(), metadata.getName());
        }
        return String.format("%s - %s + %s", metadata.getFileName(), metadata.getName(), secondPage.getMetadata().getName());
    }

    public BufferedImage compose(BufferedImage firstImage, BufferedImage secondImage, boolean rightToLeft) {
        if (secondImage == null) {
            return firstImage;
        }

        List<Rectangle> bounds = layout(firstImage, secondImage, rightToLeft);
        Rectangle first = bounds.getFirst();
        Rectangle second = bounds.getLast();
        int width = first.width + second.width;
        BufferedImage canvas = new BufferedImage(width, first.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(firstImage, first.x, first.y, first.width, first.height, null);
            graphics.drawImage(secondImage, second.x, second.y, second.width, second.height, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    /** Returns page bounds in source order, preserving proportions at a shared height. */
    public List<Rectangle> layout(BufferedImage first, BufferedImage second, boolean rightToLeft) {
        if (second == null) {
            return List.of(new Rectangle(0, 0, first.getWidth(), first.getHeight()));
        }
        int height = Math.max(first.getHeight(), second.getHeight());
        int firstWidth = scaledWidth(first, height);
        int secondWidth = scaledWidth(second, height);
        int firstX = rightToLeft ? secondWidth : 0;
        int secondX = rightToLeft ? 0 : firstWidth;
        return List.of(new Rectangle(firstX, 0, firstWidth, height),
                new Rectangle(secondX, 0, secondWidth, height));
    }

    private static int scaledWidth(BufferedImage image, int height) {
        return Math.max(1, (int) Math.round((double) image.getWidth() * height / image.getHeight()));
    }
}
