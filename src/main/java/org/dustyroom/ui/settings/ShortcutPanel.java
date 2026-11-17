package org.dustyroom.ui.settings;

import org.dustyroom.ui.components.KeyBindings;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ShortcutPanel extends JPanel {
    private final KeyBindings bindings;
    private final Map<String, String> draft;
    private final List<String> ids;
    private final DefaultTableModel rows = new DefaultTableModel(new String[]{"Action", "Shortcut"}, 0);
    private final JTable table = new JTable(rows);

    ShortcutPanel(KeyBindings bindings, Map<String, String> saved) {
        super(new BorderLayout(0, 8));
        this.bindings = bindings;
        draft = new HashMap<>(saved);
        ids = bindings.ids();
        for (String id : ids) {
            rows.addRow(new Object[]{bindings.name(id), ""});
        }
        refresh();
        table.setDefaultEditor(Object.class, null);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionInterval(0, 0);
        table.setPreferredScrollableViewportSize(new Dimension(420, 260));
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton assign = new JButton("Assign…");
        assign.addActionListener(_ -> capture(selected()));
        JButton clear = new JButton("Clear");
        clear.addActionListener(_ -> {
            draft.put(selected(), "");
            refresh();
        });
        table.getSelectionModel().addListSelectionListener(_ -> {
            boolean selected = table.getSelectedRow() >= 0;
            assign.setEnabled(selected);
            clear.setEnabled(selected);
        });
        JButton restore = new JButton("Restore defaults");
        restore.addActionListener(_ -> {
            draft.clear();
            refresh();
        });
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(assign);
        controls.add(clear);
        controls.add(restore);
        add(controls, BorderLayout.SOUTH);
    }

    private String selected() {
        return ids.get(table.getSelectedRow());
    }

    Map<String, String> overrides() {
        return Map.copyOf(draft);
    }

    private void refresh() {
        for (int row = 0; row < ids.size(); row++) {
            rows.setValueAt(KeyBindings.display(bindings.keys(ids.get(row), draft)), row, 1);
        }
    }

    private void capture(String id) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, bindings.name(id), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(_ -> dialog.dispose());
        JPanel content = new JPanel(new BorderLayout(8, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(new JLabel("Press the new keyboard shortcut."), BorderLayout.CENTER);
        content.add(cancel, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(this);

        KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        KeyEventDispatcher capture = event -> {
            if (!dialog.isActive() || event.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }
            int code = event.getKeyCode();
            switch (code) {
                case KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_SHIFT,
                     KeyEvent.VK_META, KeyEvent.VK_ALT_GRAPH, KeyEvent.VK_UNDEFINED -> {
                    return true;
                }
                default -> {
                    KeyStroke shortcut = KeyStroke.getKeyStroke(code, event.getModifiersEx());
                    dialog.dispose();
                    assign(id, shortcut);
                    return true;
                }
            }
        };
        focus.addKeyEventDispatcher(capture);
        try {
            dialog.setVisible(true);
        } finally {
            focus.removeKeyEventDispatcher(capture);
        }
    }

    private void assign(String id, KeyStroke shortcut) {
        for (String other : ids) {
            if (other.equals(id)) {
                continue;
            }
            List<KeyStroke> existing = bindings.keys(other, draft);
            if (existing.remove(shortcut)) {
                String message = "This shortcut is assigned to “%s”. Move it to “%s”?"
                        .formatted(bindings.name(other), bindings.name(id));
                if (JOptionPane.showConfirmDialog(this, message, "Reassign shortcut",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                    return;
                }
                String remaining = existing.stream()
                                           .map(KeyStroke::toString)
                                           .collect(Collectors.joining(","));
                draft.put(other, remaining);
                break;
            }
        }
        draft.put(id, shortcut.toString());
        refresh();
    }
}
