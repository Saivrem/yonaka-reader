package org.dustyroom.ui.rendering;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpreadRendererTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void normalizesHeightWithoutPaddingOrChangingPageOrder(boolean rightToLeft) {
        BufferedImage first = solid(60, 100, Color.RED);
        BufferedImage second = solid(40, 50, Color.BLUE);
        SpreadRenderer renderer = new SpreadRenderer();
        BufferedImage spread = renderer.compose(first, second, rightToLeft);
        assertEquals(140, spread.getWidth());
        assertEquals(100, spread.getHeight());
        List<Rectangle> bounds = renderer.layout(first, second, rightToLeft);
        assertEquals(new Rectangle(rightToLeft ? 80 : 0, 0, 60, 100), bounds.getFirst());
        assertEquals(new Rectangle(rightToLeft ? 0 : 60, 0, 80, 100), bounds.getLast());
        for (int y : new int[]{0, 50, 99}) {
            assertEquals(Color.RED.getRGB(), spread.getRGB(bounds.getFirst().x + 30, y));
            assertEquals(Color.BLUE.getRGB(), spread.getRGB(bounds.getLast().x + 40, y));
        }
    }

    @Test
    void retainsTransparencyAndLeavesSinglePagesUntouched() {
        BufferedImage image = new BufferedImage(30, 50, BufferedImage.TYPE_INT_ARGB);
        SpreadRenderer renderer = new SpreadRenderer();
        assertSame(image, renderer.compose(image, null, true));
        BufferedImage spread = renderer.compose(image, new BufferedImage(40, 80, BufferedImage.TYPE_INT_ARGB), false);
        assertEquals(0, spread.getRGB(0, 0));
        assertEquals(0, spread.getRGB(spread.getWidth() - 1, spread.getHeight() - 1));
    }

    private static BufferedImage solid(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
