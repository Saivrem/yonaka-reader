package org.dustyroom.be.models.state;

import org.dustyroom.be.i18n.I18n;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ThemeMode {
    SYSTEM("ui.system"),
    LIGHT("ui.ayu_light"),
    DARK("ui.ayu_dark");

    private final String label;

    public String getLabel() {
        return I18n.text(label);
    }

    public ViewerTheme resolve(ViewerTheme systemFallback) {
        return switch (this) {
            case SYSTEM -> systemFallback;
            case LIGHT -> ViewerTheme.LIGHT;
            case DARK -> ViewerTheme.DARK;
        };
    }

    @Override
    public String toString() {
        return getLabel();
    }
}
