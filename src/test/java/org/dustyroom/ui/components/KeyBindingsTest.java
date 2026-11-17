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
