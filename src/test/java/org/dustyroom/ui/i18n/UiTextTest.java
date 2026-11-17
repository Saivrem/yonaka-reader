package org.dustyroom.ui.i18n;

import org.dustyroom.be.i18n.I18n;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

class UiTextTest {
    @Test
    void existingWidgetsChangeLanguageAndKeepTheirState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                I18n.select("en");
                JButton open = UiText.button("ui.open_a_book");
                JCheckBox selected = UiText.checkbox("ui.show_thumbnails", true);
                JLabel progress = UiText.label("reading.progress", 3, 10);
                assertEquals("Open a book", open.getText());

                I18n.select("ru");
                UiText.refresh();
                assertEquals("Открыть книгу", open.getText());
                assertEquals("Страница 3 / 10", progress.getText());
                assertTrue(selected.isSelected());

                I18n.select("ja");
                UiText.refresh();
                assertEquals("本を開く", open.getText());
                assertEquals("3 / 10 ページ", progress.getText());
                assertEquals("はい", UIManager.getString("OptionPane.yesButtonText"));
                assertTrue(selected.isSelected());

                UiText.text(progress, "reading.page", 5);
                I18n.select("en");
                UiText.refresh();
                assertEquals("Page 5", progress.getText());
                assertEquals("Open a book", open.getText());
            } finally {
                I18n.select("en");
                UiText.refresh();
            }
        });
    }
}
