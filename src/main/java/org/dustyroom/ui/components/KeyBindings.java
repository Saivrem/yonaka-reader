package org.dustyroom.ui.components;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.ui.actions.ViewerActions;
import org.dustyroom.ui.i18n.UiText;
import org.dustyroom.ui.utils.PlatformUi;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class KeyBindings {
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final Map<String, String> defaults = new HashMap<>();
    private final Map<String, String> descriptions = new HashMap<>();
    private final List<KeyStroke> installed = new ArrayList<>();

    public KeyBindings(ViewerActions viewer) {
        List<Action> commands = List.of(viewer.openFile(), viewer.home(), viewer.library(), viewer.openPageGrid(), viewer.toggleThumbnails(),
                viewer.nextImage(), viewer.prevImage(), viewer.firstImage(), viewer.lastImage(), viewer.nextVolume(), viewer.prevVolume(),
                viewer.goToPage(), viewer.toggleTwoPageMode(), viewer.shiftPairing(), viewer.setMangaReadingMode(), viewer.setComicsReadingMode(),
                viewer.fitScreen(), viewer.fitWidth(), viewer.fitHeight(), viewer.zoomIn(), viewer.zoomOut(), viewer.toggleFullscreen(),
                viewer.exitFullscreen(), viewer.settings(), viewer.showAbout(), viewer.resetComicDatabase(), viewer.exit());
        for (Action action : commands) {
            String id = (String) action.getValue(Action.ACTION_COMMAND_KEY);
            actions.put(id, action);
            descriptions.put(id, (String) action.getValue(Action.SHORT_DESCRIPTION));
            KeyStroke primary = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
            defaults.put(id, primary == null ? "" : primary.toString());
        }
        alias(viewer.openFile(), KeyEvent.VK_O);
        alias(viewer.exit(), KeyEvent.VK_Q);
        alias(viewer.nextImage(), KeyEvent.VK_PAGE_DOWN);
        alias(viewer.prevImage(), KeyEvent.VK_PAGE_UP);
        alias(viewer.zoomIn(), KeyEvent.VK_EQUALS);
        if (!PlatformUi.usesScreenMenuBar()) {
            alias(viewer.toggleFullscreen(), KeyEvent.VK_F11);
        }
    }

    private void alias(Action action, int keyCode) {
        String id = (String) action.getValue(Action.ACTION_COMMAND_KEY);
        String alias = KeyStroke.getKeyStroke(keyCode, 0).toString();
        defaults.merge(id, alias, (first, second) -> first.isEmpty() ? second : first + "," + second);
    }

    public List<String> ids() {
        return List.copyOf(actions.keySet());
    }

    public String name(String id) {
        return (String) actions.get(id).getValue(Action.NAME);
    }

    public List<KeyStroke> keys(String id, Map<String, String> overrides) {
        if (overrides.containsKey(id)) {
            return parseKeys(overrides.get(id));
        }

        List<KeyStroke> keys = parseKeys(defaults.get(id));
        for (String assigned : overrides.values()) {
            keys.removeAll(parseKeys(assigned));
        }
        return keys;
    }

    private static List<KeyStroke> parseKeys(String value) {
        List<KeyStroke> keys = new ArrayList<>();
        for (String token : value.split(",")) {
            KeyStroke stroke = KeyStroke.getKeyStroke(token);
            if (stroke != null) {
                keys.add(stroke);
            }
        }
        return keys;
    }

    public static String display(List<KeyStroke> keys) {
        return keys.stream()
                   .map(KeyBindings::display)
                   .collect(Collectors.joining(", "));
    }

    private static String display(KeyStroke key) {
        String modifiers = InputEvent.getModifiersExText(key.getModifiers());
        String name = KeyEvent.getKeyText(key.getKeyCode());
        return modifiers.isEmpty() ? name : modifiers + "+" + name;
    }

    public void apply(JRootPane root, Map<String, String> overrides) {
        InputMap inputs = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        installed.forEach(inputs::remove);
        installed.clear();
        actions.forEach((id, action) -> {
            List<KeyStroke> keys = keys(id, overrides);
            action.putValue(Action.ACCELERATOR_KEY, keys.isEmpty() ? null : keys.getFirst());
            String suffix = keys.isEmpty() ? "" : " (" + display(keys) + ")";
            if (action.getValue("translation.description") instanceof String key) {
                UiText.bind(action, "shortcutHint", target ->
                        target.putValue(Action.SHORT_DESCRIPTION, I18n.text(key) + suffix));
            } else {
                action.putValue(Action.SHORT_DESCRIPTION, descriptions.get(id) + suffix);
            }
            root.getActionMap().put(id, action);
            for (KeyStroke key : keys) {
                inputs.put(key, id);
                installed.add(key);
            }
        });
    }
}
