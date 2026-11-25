package org.dustyroom.ui.components;

import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.Consumer;

@RequiredArgsConstructor
final class ReaderMouseGestures extends MouseAdapter {
    private final ImageComponent image;
    private final JScrollPane scroll;
    private final Timer save;
    private final Consumer<Boolean> swipe;
    private static final int SWIPE_DISTANCE = 64;
    private long lastWheel;
    private double swipeDistance;
    private boolean wheelTurned;
    private boolean dragCanTurn;

    private Point pressed, origin;
    private double verticalRemainder, horizontalRemainder;

    @Override
    public void mouseWheelMoved(MouseWheelEvent event) {
        if (event.isControlDown() || event.isMetaDown()) {
            Point anchor = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), scroll.getViewport());
            image.zoomAt(Math.exp(-event.getPreciseWheelRotation() * 0.12), anchor, scroll);
            save.restart();
        } else {
            boolean horizontal = event.isShiftDown();
            JScrollBar bar = horizontal ? scroll.getHorizontalScrollBar() : scroll.getVerticalScrollBar();
            boolean block = event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL;
            double units = event.getPreciseWheelRotation();
            if (!block) {
                units *= Math.max(1, event.getScrollAmount());
            }
            if (horizontal && pageFits()) {
                if (event.getWhen() - lastWheel > 300) {
                    swipeDistance = 0;
                    wheelTurned = false;
                }
                lastWheel = event.getWhen();
                // Swipe sensitivity is independent of the wheel's panning speed.
                swipeDistance += units * (block ? bar.getBlockIncrement() : 48);
                if (!wheelTurned && Math.abs(swipeDistance) >= SWIPE_DISTANCE) {
                    wheelTurned = true;
                    swipe.accept(swipeDistance > 0);
                }
                event.consume();
                return;
            }
            double delta = units * (block ? bar.getBlockIncrement() : bar.getUnitIncrement());
            double total = delta + (horizontal ? horizontalRemainder : verticalRemainder);
            int pixels = (int) total;
            if (horizontal) {
                horizontalRemainder = total - pixels;
            } else {
                verticalRemainder = total - pixels;
            }
            bar.setValue(bar.getValue() + pixels);
        }
        event.consume();
    }

    private boolean pageFits() {
        JViewport viewport = scroll.getViewport();
        Dimension view = viewport.getViewSize();
        Dimension extent = viewport.getExtentSize();
        return view.width <= extent.width && view.height <= extent.height;
    }

    @Override
    public void mousePressed(MouseEvent event) {
        if (!SwingUtilities.isLeftMouseButton(event) && !SwingUtilities.isMiddleMouseButton(event)) {
            return;
        }
        dragCanTurn = SwingUtilities.isLeftMouseButton(event) && pageFits();
        pressed = SwingUtilities.convertPoint(image, event.getPoint(), scroll.getViewport());
        origin = scroll.getViewport().getViewPosition();
        image.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        if (pressed == null) {
            return;
        }
        Point now = SwingUtilities.convertPoint(image, event.getPoint(), scroll.getViewport());
        JViewport viewport = scroll.getViewport();
        int x = origin.x + pressed.x - now.x;
        int y = origin.y + pressed.y - now.y;
        image.setViewportPosition(viewport, x, y);
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        if (pressed != null && dragCanTurn && pageFits()) {
            Point end = SwingUtilities.convertPoint(image, event.getPoint(), scroll.getViewport());
            int dx = end.x - pressed.x;
            int dy = end.y - pressed.y;
            if (Math.abs(dx) >= SWIPE_DISTANCE && Math.abs(dx) > 2 * Math.abs(dy)) {
                swipe.accept(dx < 0);
            }
        }
        pressed = origin = null;
        image.setCursor(Cursor.getDefaultCursor());
    }

}
