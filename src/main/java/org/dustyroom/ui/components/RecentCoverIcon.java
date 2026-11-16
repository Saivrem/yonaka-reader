package org.dustyroom.ui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Paints a cached cover without resizing the original image.
 */
final class RecentCoverIcon implements Icon {
    private final BufferedImage image;

    private final Dimension size;

    RecentCoverIcon(BufferedImage image, Dimension size) {
        this.size = size;
        this.image = image;
    }

    @Override
    public int getIconWidth() {
        return size.width;
    }

    @Override
    public int getIconHeight() {
        return size.height;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        double scale = Math.min((double) size.width / image.getWidth(), (double) size.height / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            copy.drawImage(image, x + (size.width - width) / 2, y + (size.height - height) / 2, width, height, null);
        } finally {
            copy.dispose();
        }
    }
}
