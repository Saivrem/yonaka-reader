package org.dustyroom.ui.settings;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.i18n.Language;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationEditorTest {
    @Test
    void switchingLabelsKeepsDraftEditsAndLeavesTheOriginalIntact() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> keys = List.copyOf(I18n.english().getMessages().keySet());
            Language original = new Language("private", "Private", Map.of(keys.getFirst(), "Original"));
            TranslationEditor editor = new TranslationEditor(original);
            JList<?> labels = (JList<?>) ((JScrollPane) editor.getLeftComponent()).getViewport().getView();
            JPanel fields = (JPanel) editor.getRightComponent();
            JPanel translated = (JPanel) fields.getComponent(1);
            BorderLayout layout = (BorderLayout) translated.getLayout();
            JScrollPane scroll = (JScrollPane) layout.getLayoutComponent(BorderLayout.CENTER);
            JTextArea value = (JTextArea) scroll.getViewport().getView();

            assertEquals("Original", value.getText());
            value.setText("Edited\nline %1$s");
            labels.setSelectedIndex(1);
            assertEquals(I18n.english().getMessages().get(keys.get(1)), value.getText());
            labels.setSelectedIndex(0);
            assertEquals("Edited\nline %1$s", value.getText());
            assertEquals(Map.of(keys.getFirst(), "Edited\nline %1$s"), editor.translation().getMessages());
            assertEquals("Original", original.getMessages().get(keys.getFirst()));
        });
    }
}
