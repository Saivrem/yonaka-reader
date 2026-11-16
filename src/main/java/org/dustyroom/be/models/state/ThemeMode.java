package org.dustyroom.be.models.state;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ThemeMode {
    SYSTEM("System"),
    LIGHT("Ayu Light"),
    DARK("Ayu Dark");

    private final String label;

    public ViewerTheme resolve(ViewerTheme systemFallback) {
        return switch (this) {
            case SYSTEM -> systemFallback;
            case LIGHT -> ViewerTheme.LIGHT;
            case DARK -> ViewerTheme.DARK;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
