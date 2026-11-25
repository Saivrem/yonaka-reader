package org.dustyroom;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.i18n.TranslationFiles;
import org.dustyroom.be.models.state.ReaderSettings;
import org.dustyroom.be.models.state.ThemeMode;
import org.dustyroom.be.persistence.ReaderStore;
import org.dustyroom.be.persistence.RecoverableReaderStore;
import org.dustyroom.be.persistence.SqliteReaderStore;
import org.dustyroom.be.settings.SystemThemeDetector;
import org.dustyroom.be.utils.StorageUtils;
import org.dustyroom.ui.ImageViewer;
import org.dustyroom.ui.utils.BrandAssets;
import org.dustyroom.ui.utils.DialogUtils;
import org.dustyroom.ui.utils.PlatformUi;
import org.dustyroom.ui.utils.UiUtils;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class Main {

    static void main(String[] args) {
        PlatformUi.configure();
        BrandAssets.load();
        ReaderStore candidate = ReaderStore.disabled();
        ReaderSettings loaded = ReaderSettings.defaults();
        String error = null;
        Path database = null;
        // Initialize storage before scheduling Swing work; SQL never runs on the EDT.
        try {
            database = StorageUtils.database();
            candidate = new SqliteReaderStore(database);
            loaded = candidate.settings();
        } catch (RuntimeException exception) {
            try {
                candidate.close();
            } catch (RuntimeException ignored) { /* initialization already failed */ }
            candidate = database == null ? ReaderStore.disabled() : new RecoverableReaderStore(database);
            String recovery = database == null
                    ? I18n.text("startup.dataUnavailable")
                    : I18n.text("startup.resetHint");
            error = I18n.text("startup.database", exception.getMessage(), recovery);
        }
        I18n.loadProfile(database == null ? List.of() : new TranslationFiles(database.getParent()).load(), loaded.getLanguage());
        if (loaded.getThemeMode() == ThemeMode.SYSTEM) {
            loaded.setTheme(new SystemThemeDetector().detect().orElse(loaded.getTheme()));
        }
        ReaderStore store = candidate;
        ReaderSettings settings = loaded;
        String startupError = error;
        SwingUtilities.invokeLater(() -> {
            UiUtils.installTheme(settings.getTheme());
            ImageViewer imageViewer = new ImageViewer(store, settings);
            if (startupError != null) {
                DialogUtils.showError(imageViewer, startupError);
            }
            if (args.length > 0) {
                imageViewer.openFile(new File(args[0]));
            }
        });
    }

}
