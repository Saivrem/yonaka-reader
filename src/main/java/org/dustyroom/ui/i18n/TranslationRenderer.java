package org.dustyroom.ui.i18n;

import org.dustyroom.be.i18n.I18n;

import javax.swing.*;
import java.awt.*;

final class TranslationRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean selected, boolean focused) {
        String text = value instanceof String key ? I18n.text(key) : "";
        return super.getListCellRendererComponent(list, text, index, selected, focused);
    }
}
