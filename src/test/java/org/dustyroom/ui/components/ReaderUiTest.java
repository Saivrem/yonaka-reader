package org.dustyroom.ui.components;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.direction.ReadingMode;
import org.dustyroom.be.models.state.FitMode;
import org.dustyroom.be.models.state.ReaderSettings;
import org.dustyroom.be.models.state.ViewerTheme;
import org.dustyroom.be.models.state.ThemeMode;
import org.dustyroom.be.models.view.ViewerFrame;
import org.dustyroom.ui.actions.ViewerActionState;
import org.dustyroom.ui.actions.ViewerActions;
import org.dustyroom.ui.i18n.UiText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.swing.*;
import java.awt.*;
import org.dustyroom.ui.actions.RunnableAction;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ReaderUiTest {
    @ParameterizedTest
    @CsvSource({"1, 1, 1 / 10", "2, 3, 2–3 / 10"})
    void completedLoadingRestoresPagePositionAcrossLanguageChanges(int first, int last, String label) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewerActions actions = actions();
            NavigationPanel panel = new NavigationPanel(actions);
            JButton position = children(panel)
                    .filter(JButton.class::isInstance)
                    .map(JButton.class::cast)
                    .filter(button -> button.getAction() == actions.goToPage())
                    .findFirst().orElseThrow();
            ViewerFrame frame = ViewerFrame.builder()
                    .firstPage(first)
                    .lastPage(last)
                    .pageCount(10)
                    .build();

            try {
                for (String language : new String[]{"en-US", "ru-RU", "ja-JP"}) {
                    I18n.select(language);
                    panel.setLoading(true);
                    UiText.refresh();
                    assertEquals(I18n.text("ui.loading"), position.getText());

                    panel.updateState(frame);
                    UiText.refresh();
                    assertEquals(label, position.getText());

                    // A failed opening with no displayed frame restores the empty state.
                    panel.setLoading(true);
                    panel.updateState(null);
                    assertEquals(I18n.text("ui.no_book_open"), position.getText());
                }
            } finally {
                I18n.select("en-US");
                UiText.refresh();
            }
        });
    }

    private static Stream<Component> children(Container parent) {
        return Arrays
                .stream(parent.getComponents())
                .flatMap(component -> component instanceof Container container
                        ? Stream.concat(Stream.of(component), children(container)) : Stream.of(component));
    }

    private static ViewerActions actions() {
        Map<String, Action> actions = new HashMap<>();
        return (ViewerActions) Proxy.newProxyInstance(ViewerActions.class.getClassLoader(),
                new Class<?>[]{ViewerActions.class}, (_, method, _) ->
                        actions.computeIfAbsent(method.getName(), name -> {
                            Action action = new RunnableAction(name, () -> {});
                            action.putValue(Action.SHORT_DESCRIPTION, name);
                            return action;
                        }));
    }

    @Test
    void toolbarFitsOneRowAtTheMinimumWindowWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewerActions actions = actions();
            NavigationPanel panel = new NavigationPanel(actions);
            // Allow for native window borders inside the 940-pixel minimum frame width.
            panel.setSize(920, panel.getPreferredSize().height);
            panel.doLayout();
            for (Component component : panel.getComponents()) {
                JPanel group = (JPanel) component;
                group.doLayout();
                for (Component button : group.getComponents()) {
                    assertTrue(group.getVisibleRect().contains(button.getBounds()));
                }
            }

            Map<Action, ReaderIconKind> shortcuts = Map.of(
                    actions.library(), ReaderIconKind.LIBRARY,
                    actions.openFile(), ReaderIconKind.OPEN,
                    actions.openPageGrid(), ReaderIconKind.PAGE_GRID,
                    actions.toggleFullscreen(), ReaderIconKind.FULLSCREEN);
            shortcuts.forEach((action, icon) -> {
                AbstractButton button = children(panel)
                        .filter(AbstractButton.class::isInstance)
                        .map(AbstractButton.class::cast)
                        .filter(item -> item.getAction() == action)
                        .findFirst().orElseThrow();
                assertEquals(icon, assertInstanceOf(ReaderIcon.class, button.getIcon()).kind());
            });
        });
    }

    @Test
    void toolbarTracksDisplayedPagesModesAndAvailability() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewerActions actions = actions();
            ViewerActionState.apply(actions, null, FitMode.FIT_SCREEN, false);
            NavigationPanel panel = new NavigationPanel(actions);
            assertTrue(children(panel).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                    .anyMatch(button -> button.getAction() == actions.home() && button.isEnabled()));
            assertTrue(children(panel).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                    .anyMatch(button -> button.getAction() == actions.firstImage()));
            assertTrue(children(panel).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                    .anyMatch(button -> button.getAction() == actions.lastImage()));
            assertFalse(children(panel).filter(JToggleButton.class::isInstance)
                    .map(JToggleButton.class::cast).anyMatch(AbstractButton::isSelected));
            assertFalse(actions.nextImage().isEnabled());
            assertFalse(actions.goToPage().isEnabled());
            assertTrue(actions.openFile().isEnabled());
            ViewerFrame frame = new ViewerFrame(null, "Book", null, 2, 3, 10, true, ReadingMode.COMICS, null);
            ViewerActionState.apply(actions, frame, FitMode.FIT_WIDTH, true);
            panel.updateState(frame);
            assertEquals(Boolean.TRUE, actions.fitWidth().getValue(Action.SELECTED_KEY));
            assertEquals(Boolean.FALSE, actions.fitScreen().getValue(Action.SELECTED_KEY));
            assertEquals(Boolean.TRUE, actions.toggleTwoPageMode().getValue(Action.SELECTED_KEY));
            assertEquals(Boolean.TRUE, actions.setComicsReadingMode().getValue(Action.SELECTED_KEY));
            assertEquals(Boolean.FALSE, actions.setMangaReadingMode().getValue(Action.SELECTED_KEY));
            assertTrue(actions.exitFullscreen().isEnabled());
            assertTrue(children(panel).filter(JButton.class::isInstance).map(JButton.class::cast)
                    .anyMatch(button -> "2–3 / 10".equals(button.getText())));
            children(panel).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                    .forEach(button -> {
                        assertEquals(0, button.getMnemonic());
                        assertFalse(button.isRequestFocusEnabled());
                        assertNotNull(button.getToolTipText());
                        if (button.getAction() != actions.goToPage()) {
                            assertNotNull(button.getIcon());
                            assertTrue(button.getText() == null || button.getText().isEmpty());
                        }
                    });
            ViewerActionState.apply(actions, new ViewerFrame(null, "Book", null, 10, 10, 10, false,
                    ReadingMode.MANGA, null), FitMode.ORIGINAL, false);
            assertFalse(actions.lastImage().isEnabled());
            assertTrue(actions.nextImage().isEnabled());
            assertFalse(actions.exitFullscreen().isEnabled());
        });
    }

    @Test
    void fileMenuExposesTheSharedLibraryAction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewerActions actions = actions();
            MenuBar menu = new MenuBar(actions, ThemeMode.DARK, _ -> true);
            JMenu file = menu.getMenu(0);
            assertTrue(Arrays.stream(file.getMenuComponents())
                             .filter(JMenuItem.class::isInstance)
                             .map(JMenuItem.class::cast)
                             .anyMatch(item -> item.getAction() == actions.library()));
        });
    }

    @Test
    void homeSelectsRecentAndClearsTheLoadingState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewerActions actions = actions();
            JScrollPane reader = new JScrollPane();
            ReaderSurface surface = new ReaderSurface(reader, actions.openFile());
            JPanel library = new JPanel();
            surface.setLibrary(library);
            surface.showLibrary();
            assertTrue(library.isVisible());
            assertFalse(reader.isVisible());
            assertFalse(children(surface).anyMatch(JTabbedPane.class::isInstance));
            surface.showReading();
            surface.setLoading(true);

            surface.showWelcome();
            assertFalse(library.isVisible());
            assertFalse(reader.isVisible());
            assertTrue(children(surface).filter(JLabel.class::isInstance).map(JLabel.class::cast)
                    .anyMatch(label -> I18n.text("home.formats").equals(label.getText())));
        });
    }

    @Test
    void welcomeOpensWithTheSharedActionAndGivesWayToReader() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewerActions actions = actions();
            JScrollPane reader = new JScrollPane();
            ReaderSurface surface = new ReaderSurface(reader, actions.openFile());
            assertFalse(reader.isVisible());
            assertTrue(children(surface).filter(JButton.class::isInstance).map(JButton.class::cast)
                    .anyMatch(button -> button.getAction() == actions.openFile()));
            surface.setLoading(true);
            assertTrue(children(surface).filter(JLabel.class::isInstance).map(JLabel.class::cast)
                    .anyMatch(label -> "Opening your book…".equals(label.getText())));
            surface.showReading();
            assertTrue(reader.isVisible());
        });
    }

    @Test
    void restoresLastPageFitAfterManualZoom() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ImageComponent component = new ImageComponent();
            JScrollPane scroll = new JScrollPane(component);
            scroll.getViewport().setSize(800, 600);
            component.setFitMode(FitMode.FIT_WIDTH);
            component.setImageAndCenter(new BufferedImage(4, 8, BufferedImage.TYPE_INT_RGB), scroll);
            component.zoomIn();
            assertEquals(FitMode.ORIGINAL, component.getFitMode());
            component.restorePageFit();
            component.setImageAndCenter(new BufferedImage(8, 16, BufferedImage.TYPE_INT_RGB), scroll);
            assertEquals(FitMode.FIT_WIDTH, component.getFitMode());
            component.setFitMode(FitMode.FIT_SCREEN);
            component.zoomOut();
            assertTrue(component.getScale() <= 20);
            assertDoesNotThrow(() -> new ReaderSettings(
                    ViewerTheme.DARK, component.getFitMode(), component.getScale()));
        });
    }

    @Test
    void imageSurfacePaintsTheCurrentThemeBackgroundEvenWithoutAnImage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ImageComponent component = new ImageComponent();
            component.setOpaque(true);
            component.setSize(40, 40);
            BufferedImage canvas = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
            for (Color color : new Color[]{new Color(0x0D1017), new Color(0xF8F9FA)}) {
                component.setBackground(color);
                Graphics2D graphics = canvas.createGraphics();
                try {
                    component.paint(graphics);
                } finally {
                    graphics.dispose();
                }
                assertEquals(color.getRGB(), canvas.getRGB(20, 20));
            }
        });
    }
}
