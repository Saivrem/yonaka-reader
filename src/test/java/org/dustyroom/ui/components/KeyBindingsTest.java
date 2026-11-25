package org.dustyroom.ui.components;

import org.dustyroom.ui.actions.ViewerActions;
import org.dustyroom.ui.utils.PlatformUi;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyBindingsTest {

    private static Map<String, Action> actionsByMethod() {
        Map<String, Action> actions = new HashMap<>();
        for (Method method : ViewerActions.class.getMethods()) {
            String id = method.getName();
            Action action = new AbstractAction(id) {
                @Override
                public void actionPerformed(ActionEvent event) {
                }
            };
            action.putValue(Action.ACTION_COMMAND_KEY, id);
            action.putValue(Action.SHORT_DESCRIPTION, id);
            if (id.equals("openFile")) {
                action.putValue(Action.ACCELERATOR_KEY,
                        KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
            } else if (id.equals("nextImage")) {
                action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
            }
            actions.put(id, action);
        }
        return actions;
    }

    private static ViewerActions proxy(Map<String, Action> actions) {
        return (ViewerActions) Proxy.newProxyInstance(
                ViewerActions.class.getClassLoader(),
                new Class<?>[]{ViewerActions.class},
                (_, method, _) -> actions.get(method.getName()));
    }

    @Test
    void libraryShortcutUpdatesTheMenuAndToolbarTogether() {
        Map<String, Action> actions = actionsByMethod();
        ViewerActions viewer = proxy(actions);
        KeyBindings bindings = new KeyBindings(viewer);
        JRootPane root = new JRootPane();
        JMenuItem menu = new JMenuItem(viewer.library());
        JButton button = new JButton(viewer.library());
        KeyStroke shortcut = KeyStroke.getKeyStroke("ctrl pressed L");
        bindings.apply(root, Map.of("library", shortcut.toString()));
        assertEquals("library", root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(shortcut));
        assertSame(viewer.library(), root.getActionMap().get("library"));
        assertEquals(shortcut, menu.getAccelerator());
        assertEquals("library (" + KeyBindings.display(List.of(shortcut)) + ")", button.getToolTipText());
    }

    @Test
    void savedShortcutTakesPrecedenceOverANewDefault() {
        Map<String, Action> actions = actionsByMethod();
        KeyStroke shiftP = KeyStroke.getKeyStroke("shift pressed P");
        actions.get("shiftPairing").putValue(Action.ACCELERATOR_KEY, shiftP);
        KeyBindings bindings = new KeyBindings(proxy(actions));
        JRootPane root = new JRootPane();
        JMenuItem menu = new JMenuItem(actions.get("shiftPairing"));
        InputMap inputs = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

        bindings.apply(root, Map.of("openFile", shiftP.toString()));
        assertEquals("openFile", inputs.get(shiftP));
        assertNull(menu.getAccelerator());

        bindings.apply(root, Map.of());
        assertEquals("shiftPairing", inputs.get(shiftP));
        assertEquals(shiftP, menu.getAccelerator());
    }

    @Test
    void bindsActionAcceleratorsAndSupportedAliases() {
        Map<String, Action> actionsByMethod = actionsByMethod();
        ViewerActions actions = proxy(actionsByMethod);
        JRootPane rootPane = new JRootPane();

        KeyBindings bindings = new KeyBindings(actions);
        bindings.apply(rootPane, Map.of());

        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        KeyStroke openShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK);
        assertEquals("openFile", inputMap.get(openShortcut));
        assertSame(actionsByMethod.get("openFile"), rootPane.getActionMap().get("openFile"));

        assertEquals("openFile", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_O, 0)));
        assertSame(actionsByMethod.get("openFile"), rootPane.getActionMap().get("openFile"));
        assertEquals("nextImage", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0)));
        if (!PlatformUi.usesScreenMenuBar()) {
            assertEquals("toggleFullscreen", inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0)));
        }

        JMenuItem menu = new JMenuItem(actions.shiftPairing());
        JButton button = new JButton(actions.shiftPairing());
        KeyStroke custom = KeyStroke.getKeyStroke("shift pressed J");
        bindings.apply(rootPane, Map.of("nextImage", "", "shiftPairing", custom.toString()));
        assertEquals("shiftPairing", inputMap.get(custom));
        assertEquals(custom, menu.getAccelerator());
        assertEquals("shiftPairing (" + KeyBindings.display(List.of(custom)) + ")", button.getToolTipText());
        assertNull(inputMap.get(KeyStroke.getKeyStroke("RIGHT")));
        assertNull(inputMap.get(KeyStroke.getKeyStroke("PAGE_DOWN")));

        bindings.apply(rootPane, Map.of());
        assertNull(inputMap.get(custom));
        assertNull(menu.getAccelerator());
        assertEquals("shiftPairing", button.getToolTipText());
        assertEquals("nextImage", inputMap.get(KeyStroke.getKeyStroke("RIGHT")));
    }
}
