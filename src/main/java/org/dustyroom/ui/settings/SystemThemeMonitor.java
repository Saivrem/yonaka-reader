package org.dustyroom.ui.settings;

import org.dustyroom.be.models.state.ThemeMode;
import org.dustyroom.be.models.state.ViewerTheme;
import org.dustyroom.be.settings.SystemThemeDetector;

import javax.swing.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

public final class SystemThemeMonitor implements AutoCloseable {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "yonaka-system-theme");
        thread.setDaemon(true);
        return thread;
    });
    private final Consumer<ViewerTheme> changed;
    private final SystemThemeDetector detector = new SystemThemeDetector();
    private ScheduledFuture<?> polling;
    private volatile long generation;

    public SystemThemeMonitor(ThemeMode initial, Consumer<ViewerTheme> changed) {
        this.changed = changed;
        setMode(initial);
    }

    public void setMode(ThemeMode mode) {
        if (mode == ThemeMode.SYSTEM && polling == null) {
            long request = ++generation;
            polling = worker.scheduleWithFixedDelay(() -> detect(request), 0, 5, TimeUnit.SECONDS);
        } else if (mode != ThemeMode.SYSTEM && polling != null) {
            generation++;
            polling.cancel(true);
            polling = null;
        }
    }

    private void detect(long request) {
        detector.detect().ifPresent(theme -> SwingUtilities.invokeLater(() -> {
            if (request == generation) {
                changed.accept(theme);
            }
        }));
    }

    @Override
    public void close() {
        generation++;
        worker.shutdownNow();
    }
}
