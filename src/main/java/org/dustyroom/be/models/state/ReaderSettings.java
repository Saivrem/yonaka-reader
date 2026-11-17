package org.dustyroom.be.models.state;

import lombok.*;
import lombok.experimental.Accessors;
import org.dustyroom.be.models.direction.ReadingMode;

import java.util.Map;

@Getter
@EqualsAndHashCode
@ToString
@Setter
@Accessors(chain = true)
public final class ReaderSettings {
    private final String language;
    private ViewerTheme theme;
    private FitMode fitMode;
    private double zoom;
    private String lastDirectory;
    private boolean thumbnailsVisible;
    private boolean groupRecentByFolder;

    @Setter(AccessLevel.NONE)
    private final Map<String, String> shortcuts;
    private VolumeBoundary volumeBoundary;
    private ThemeMode themeMode;
    private ReadingMode defaultReadingMode;
    private boolean defaultTwoPageMode;
    private FitMode defaultFitMode;
    private String libraryDirectory;

    @Builder(toBuilder = true)
    public ReaderSettings(ViewerTheme theme, FitMode fitMode, double zoom, String lastDirectory,
                          boolean thumbnailsVisible, boolean groupRecentByFolder, ThemeMode themeMode,
                          ReadingMode defaultReadingMode, boolean defaultTwoPageMode, FitMode defaultFitMode,
                          String libraryDirectory, VolumeBoundary volumeBoundary, Map<String, String> shortcuts, String language) {
        this.language = language == null ? "en" : language;
        this.shortcuts = shortcuts == null ? Map.of() : Map.copyOf(shortcuts);
        this.volumeBoundary = volumeBoundary == null ? VolumeBoundary.STOP : volumeBoundary;
        setTheme(theme);
        setFitMode(fitMode);
        setZoom(zoom);
        setLastDirectory(lastDirectory);

        this.thumbnailsVisible = thumbnailsVisible;
        this.groupRecentByFolder = groupRecentByFolder;

        if (themeMode == null) {
            themeMode = theme == ViewerTheme.LIGHT ? ThemeMode.LIGHT : ThemeMode.DARK;
        }
        this.themeMode = themeMode;
        this.defaultReadingMode = defaultReadingMode == null ? ReadingMode.MANGA : defaultReadingMode;
        this.defaultTwoPageMode = defaultTwoPageMode;
        this.defaultFitMode = defaultFitMode == null ? FitMode.FIT_SCREEN : defaultFitMode;
        this.libraryDirectory = libraryDirectory == null ? "" : libraryDirectory;
    }

    public ReaderSettings(ViewerTheme theme, FitMode fitMode, double zoom, String lastDirectory,
                          boolean thumbnailsVisible, boolean groupRecentByFolder) {
        this(theme, fitMode, zoom, lastDirectory, thumbnailsVisible, groupRecentByFolder,
                null, ReadingMode.MANGA, false, fitMode, "", null, Map.of(), "en");
    }

    public ReaderSettings(ViewerTheme theme, FitMode fitMode, double zoom, String lastDirectory, boolean thumbnailsVisible) {
        this(theme, fitMode, zoom, lastDirectory, thumbnailsVisible, true);
    }

    public ReaderSettings(ViewerTheme theme, FitMode fitMode, double zoom, String lastDirectory) {
        this(theme, fitMode, zoom, lastDirectory, true);
    }

    public ReaderSettings(ViewerTheme theme, FitMode fitMode, double zoom) {
        this(theme, fitMode, zoom, "");
    }

    public static ReaderSettings defaults() {
        return new ReaderSettings(ViewerTheme.DARK, FitMode.FIT_SCREEN, 1.0);
    }

    public ReaderSettings setTheme(ViewerTheme theme) {
        if (theme == null) {
            throw new IllegalArgumentException("Invalid reader settings");
        }
        this.theme = theme;
        return this;
    }

    public ReaderSettings setFitMode(FitMode fitMode) {
        if (fitMode == null) {
            throw new IllegalArgumentException("Invalid reader settings");
        }
        this.fitMode = fitMode;
        return this;
    }

    public ReaderSettings setZoom(double zoom) {
        if (!Double.isFinite(zoom) || zoom < 0.05 || zoom > 20) {
            throw new IllegalArgumentException("Invalid reader settings");
        }
        this.zoom = zoom;
        return this;
    }

    public ReaderSettings setLastDirectory(String lastDirectory) {
        this.lastDirectory = lastDirectory == null ? "" : lastDirectory;
        return this;
    }
}
