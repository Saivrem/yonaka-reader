package org.dustyroom.ui.i18n;

import lombok.experimental.UtilityClass;
import org.dustyroom.be.i18n.I18n;

import javax.swing.*;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.awt.Window;
import java.util.function.Consumer;

@UtilityClass
public class UiText {
    private static final List<TranslationBinding<?>> bindings = new ArrayList<>();

    public static <T> void bind(T owner, String property, Consumer<T> update) {
        bindings.removeIf(binding -> !binding.isAlive() || binding.replaces(owner, property));
        TranslationBinding<T> binding = new TranslationBinding<>(owner, property, update);
        bindings.add(binding);
        binding.refresh();
    }

    public static void installDefaults() {
        Locale locale = Locale.forLanguageTag(I18n.language().getId());
        JComponent.setDefaultLocale(locale);
        for (String key : I18n.languages().stream()
                              .filter(language -> language.getId().equals("en"))
                              .findFirst().orElseThrow().getMessages().keySet()) {
            if (key.startsWith("swing.")) {
                UIManager.put(key.substring("swing.".length()), I18n.text(key));
            }
        }
    }

    public static void refresh() {
        installDefaults();
        for (TranslationBinding<?> binding : List.copyOf(bindings)) {
            if (!binding.refresh()) {
                bindings.remove(binding);
            }
        }
        for (Window window : Window.getWindows()) {
            window.setLocale(Locale.forLanguageTag(I18n.language().getId()));
            window.invalidate();
            window.validate();
            window.repaint();
        }
    }

    public static void accessible(JComponent component, String key) {
        bind(component, "accessible", target -> target.getAccessibleContext().setAccessibleName(I18n.text(key)));
    }

    public static JComboBox<String> combo(String... keys) {
        JComboBox<String> combo = new JComboBox<>(keys);
        combo.setRenderer(new TranslationRenderer());
        bind(combo, "repaint", Component::repaint);
        return combo;
    }

    public static JList<String> list(String... keys) {
        JList<String> list = new JList<>(keys);
        list.setCellRenderer(new TranslationRenderer());
        bind(list, "repaint", Component::repaint);
        return list;
    }

    public static JCheckBox checkbox(String key, boolean selected) {
        JCheckBox checkbox = checkbox(key);
        checkbox.setSelected(selected);
        return checkbox;
    }

    public static JLabel centeredLabel(String key) {
        JLabel label = label(key);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    public static JLabel label(String key, Object... args) {
        JLabel label = new JLabel();
        text(label, key, args);
        return label;
    }

    public static JButton button(String key) {
        JButton button = new JButton();
        text(button, key);
        return button;
    }

    public static JCheckBox checkbox(String key) {
        JCheckBox button = new JCheckBox();
        text(button, key);
        return button;
    }

    public static JRadioButton radio(String key) {
        JRadioButton button = new JRadioButton();
        text(button, key);
        return button;
    }

    public static void text(JLabel label, String key, Object... args) {
        bind(label, "text", target -> target.setText(I18n.text(key, args)));
    }

    public static void text(AbstractButton button, String key, Object... args) {
        bind(button, "text", target -> target.setText(I18n.text(key, args)));
    }

    public static void tooltip(JComponent component, String key, Object... args) {
        bind(component, "tooltip", target -> target.setToolTipText(I18n.text(key, args)));
    }
}
