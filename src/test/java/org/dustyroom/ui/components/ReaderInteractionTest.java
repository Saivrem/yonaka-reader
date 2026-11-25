package org.dustyroom.ui.components;

import org.dustyroom.be.models.state.FitMode;
import org.dustyroom.ui.rendering.SpreadRenderer;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ReaderInteractionTest {
    @Test
    void mixedSizeSpreadRefitsAfterRepeatedViewportResizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage first = new BufferedImage(600, 1000, BufferedImage.TYPE_INT_RGB);
            BufferedImage second = new BufferedImage(400, 500, BufferedImage.TYPE_INT_RGB);
            BufferedImage spread = new SpreadRenderer().compose(first, second, true);
            ImageComponent image = new ImageComponent();
            JScrollPane scroll = new JScrollPane(image);
            image.setImageAndCenter(spread, scroll);
            for (Dimension size : new Dimension[]{new Dimension(1600, 900), new Dimension(600, 900),
                    new Dimension(2200, 1200), new Dimension(600, 400)}) {
                scroll.getViewport().setSize(size);
                image.setFitMode(FitMode.FIT_SCREEN);
                double expected = Math.min(size.getWidth() / 1400, size.getHeight() / 1000);
                assertEquals(expected, image.getScale(), 0.000001);
                assertEquals((int) (1400 * expected), image.getPreferredSize().width);
                assertEquals((int) (1000 * expected), image.getPreferredSize().height);
            }
            image.restoreView(FitMode.ORIGINAL, 1.5);
            scroll.getViewport().setSize(500, 300);
            image.updateScale();
            assertEquals(1.5, image.getScale());
        });
    }

    @Test
    void horizontalWheelGestureTurnsOncePerBurstOnFittedPages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ImageComponent image = new ImageComponent();
            JScrollPane scroll = new JScrollPane(image);
            scroll.setSize(600, 400);
            image.setImageAndCenter(new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB), scroll);
            List<Boolean> swipes = new ArrayList<>();
            ReaderGestures.install(image, scroll, swipes::add, () -> {});
            for (int time : List.of(1000, 1010, 1020, 2000)) {
                double rotation = time == 2000 ? -1 : 1;
                scroll.dispatchEvent(new MouseWheelEvent(scroll, MouseEvent.MOUSE_WHEEL, time,
                        InputEvent.SHIFT_DOWN_MASK, 100, 100, 100, 100, 0, false,
                        MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, (int) rotation, rotation));
            }
            assertEquals(List.of(true, false), swipes);
        });
    }

    @Test
    void pageIsCenteredSynchronouslyAndDoesNotJumpOnTheNextEdtTurn() throws Exception {
        Point[] expected = new Point[1];
        JScrollPane[] scroller = new JScrollPane[1];
        SwingUtilities.invokeAndWait(() -> {
            ImageComponent image = new ImageComponent();
            JScrollPane scroll = new JScrollPane(image);
            scroller[0] = scroll;
            scroll.setSize(600, 400);
            image.restoreView(FitMode.ORIGINAL, 1);
            image.setImageAndCenter(new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB), scroll);
            scroll.getViewport().setViewPosition(new Point(11, 90));
            image.setImageAndCenter(new BufferedImage(1000, 800, BufferedImage.TYPE_INT_RGB), scroll);
            expected[0] = new Point((1000 - scroll.getViewport().getWidth()) / 2, 0);
            assertEquals(expected[0], scroll.getViewport().getViewPosition());
        });
        SwingUtilities.invokeAndWait(() -> assertEquals(expected[0], scroller[0].getViewport().getViewPosition()));
    }

    @Test
    void zoomKeepsThePointUnderThePointerAndClampsScale() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ImageComponent image = new ImageComponent();
            JScrollPane scroll = new JScrollPane(image);
            scroll.setSize(600, 400);
            image.restoreView(FitMode.ORIGINAL, 1);
            image.setImageAndCenter(new BufferedImage(1200, 1000, BufferedImage.TYPE_INT_RGB), scroll);
            scroll.getViewport().setViewPosition(new Point(200, 150));
            Point anchor = new Point(100, 100);
            image.zoomAt(2, anchor, scroll);
            assertEquals(2, image.getScale());
            assertEquals(new Point(500, 400), scroll.getViewport().getViewPosition());
            image.zoomAt(100, anchor, scroll);
            assertEquals(20, image.getScale());
            image.zoomAt(0.00001, anchor, scroll);
            assertEquals(0.05, image.getScale());
        });
    }

    private static void wheel(JScrollPane scroll, int modifiers, double rotation) {
        scroll.dispatchEvent(new MouseWheelEvent(scroll, MouseEvent.MOUSE_WHEEL, 1000, modifiers,
                100, 100, 100, 100, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3,
                (int) rotation, rotation));
    }

    @Test
    void changesAxisImmediatelyAndKeepsCenterDuringZoom() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ImageComponent image = new ImageComponent();
            JScrollPane scroll = new JScrollPane(image);
            scroll.setSize(600, 400);
            image.restoreView(FitMode.ORIGINAL, 1);
            image.setImageAndCenter(new BufferedImage(1400, 1200, BufferedImage.TYPE_INT_RGB), scroll);
            scroll.getViewport().setViewPosition(new Point(100, 100));
            ReaderGestures.install(image, scroll, _ -> fail("Unexpected page turn"), () -> {});

            wheel(scroll, 0, 1);
            wheel(scroll, 0, 1);
            wheel(scroll, InputEvent.SHIFT_DOWN_MASK, 1);
            assertEquals(new Point(148, 196), scroll.getViewport().getViewPosition());
            Dimension extent = scroll.getViewport().getExtentSize();
            Point expected = new Point(296 + extent.width / 2, 392 + extent.height / 2);
            image.zoomAtCenter(2, scroll);
            assertEquals(expected, scroll.getViewport().getViewPosition());
        });
    }

    @Test
    void horizontalInputOnTallPageDoesNotTurnEvenAtBottom() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ImageComponent image = new ImageComponent();
            JScrollPane scroll = new JScrollPane(image);
            scroll.setSize(600, 400);
            image.setFitMode(FitMode.FIT_WIDTH);
            image.setImageAndCenter(new BufferedImage(400, 1200, BufferedImage.TYPE_INT_RGB), scroll);
            ReaderGestures.install(image, scroll, _ -> fail("Unexpected page turn"), () -> {});
            scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
            Point bottom = scroll.getViewport().getViewPosition();
            wheel(scroll, InputEvent.SHIFT_DOWN_MASK, 2);
            assertEquals(bottom, scroll.getViewport().getViewPosition());
        });
    }

    @Test
    void preciseWheelScrollAndDraggingMoveTheViewport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ImageComponent image = new ImageComponent();
            JScrollPane scroll = new JScrollPane(image);
            scroll.setSize(600, 400);
            image.restoreView(FitMode.ORIGINAL, 1);
            image.setImageAndCenter(new BufferedImage(1200, 1000, BufferedImage.TYPE_INT_RGB), scroll);
            ReaderGestures.install(image, scroll, _ -> fail("Panning a wide page must not turn it"), () -> {
            });
            MouseWheelEvent wheel = new MouseWheelEvent(scroll, MouseEvent.MOUSE_WHEEL, 0, 0, 100, 100,
                    100, 100, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 0.25);
            scroll.dispatchEvent(wheel);
            assertEquals(12, scroll.getViewport().getViewPosition().y);
            Point before = scroll.getViewport().getViewPosition();
            image.dispatchEvent(new MouseEvent(image, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
                    100, 100, 1, false, MouseEvent.BUTTON1));
            image.dispatchEvent(new MouseEvent(image, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_DOWN_MASK,
                    70, 70, 0, false, MouseEvent.NOBUTTON));
            assertEquals(new Point(before.x + 30, before.y + 30), scroll.getViewport().getViewPosition());
            image.dispatchEvent(new MouseEvent(image, MouseEvent.MOUSE_RELEASED, 2, 0, 70, 70, 1, false, MouseEvent.BUTTON1));
        });
    }
}
