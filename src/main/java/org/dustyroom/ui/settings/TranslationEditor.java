package org.dustyroom.ui.settings;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.i18n.Language;
import org.dustyroom.ui.i18n.UiText;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TranslationEditor extends JSplitPane {
    private final Language original;
    private final Map<String, String> english = I18n.english().getMessages();
    private final Map<String, String> messages;
    private final JTextArea value;
    private String selection;

    TranslationEditor(Language original) {
        super(HORIZONTAL_SPLIT);
        this.original = original;
        messages = new LinkedHashMap<>(original.getMessages());
        List<String> keys = List.copyOf(english.keySet());
        JList<String> labels = new JList<>(english.values().toArray(String[]::new));
        labels.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        labels.setFixedCellWidth(260);
        JPanel editor = new JPanel(new GridLayout(2, 1, 0, 8));
        JTextArea source = field(editor, "translations.english", false);
        value = field(editor, "translations.value", true);
        labels.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || labels.isSelectionEmpty()) {
                return;
            }
            storeValue();
            selection = keys.get(labels.getSelectedIndex());
            source.setText(english.get(selection));
            value.setText(messages.getOrDefault(selection, source.getText()));
            source.setCaretPosition(0);
            value.setCaretPosition(0);
        });
        labels.setSelectedIndex(0);
        setLeftComponent(new JScrollPane(labels));
        setRightComponent(editor);
        setPreferredSize(new Dimension(660, 350));
    }

    Language translation() {
        storeValue();
        return new Language(original.getId(), original.getName(), messages);
    }

    private void storeValue() {
        if (selection != null && !value.getText().equals(messages.getOrDefault(selection, english.get(selection)))) {
            messages.put(selection, value.getText());
        }
    }

    private static JTextArea field(JPanel editor, String label, boolean editable) {
        JTextArea area = new JTextArea();
        area.setEditable(editable);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JLabel title = UiText.label(label);
        title.setLabelFor(area);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(title, BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        editor.add(panel);
        return area;
    }

}
