package org.dustyroom.ui;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.state.FitMode;
import org.dustyroom.be.models.state.ReaderSettings;
import org.dustyroom.be.persistence.ReaderStore;
import org.dustyroom.ui.actions.DefaultViewerActions;
import org.dustyroom.ui.actions.ViewerActions;
import org.dustyroom.ui.components.*;
import org.dustyroom.ui.components.MenuBar;
import org.dustyroom.ui.listeners.ComponentResizeListener;
import org.dustyroom.ui.listeners.WindowCloseListener;
import org.dustyroom.ui.utils.BrandAssets;
import org.dustyroom.ui.utils.DialogUtils;
import org.dustyroom.ui.utils.PlatformUi;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.beans.PropertyChangeListener;

public class ImageViewer extends JFrame {
    private static final int RESIZE_RENDER_DELAY_MS = 80;

    private final ViewerController viewerController;
    private final MenuBar menuBar;
    private final NavigationPanel navigationPanel;
    private final ImageComponent imageComponent;
    private final JScrollPane scrollPane;
    private final ReaderSurface readerSurface;
    private final Timer resizeRenderTimer;

    private final PropertyChangeListener focusChanged;

    public ImageViewer(ReaderStore store, ReaderSettings settings) {
        setTitle(I18n.text("brand.name"));
        setIconImages(BrandAssets.windowIcons());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setPreferredSize(new Dimension(1100, 760));
        setMinimumSize(new Dimension(940, 560));

        GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        imageComponent = new ImageComponent();
        imageComponent.restoreView(settings.getFitMode(), settings.getZoom());
        scrollPane = new JScrollPane(imageComponent);
        viewerController = new ViewerController(this, graphicsDevice, imageComponent, scrollPane, store, settings);
        focusChanged = _ -> viewerController.refreshShortcuts();
        ViewerActions viewerActions = new DefaultViewerActions(viewerController);
        resizeRenderTimer = new Timer(RESIZE_RENDER_DELAY_MS, _ -> applyResizeRender());
        resizeRenderTimer.setRepeats(false);

        menuBar = new MenuBar(viewerActions, settings.getThemeMode(), viewerController::setTheme);
        navigationPanel = new NavigationPanel(viewerActions);
        readerSurface = new ReaderSurface(scrollPane, viewerActions.openFile());
        viewerController.attachPanels(menuBar, navigationPanel, readerSurface, viewerActions);
        PlatformUi.installApplicationHandlers(viewerController);

        initializeUI();
        new FileDropHandler(viewerController::openFile, message -> DialogUtils.showError(this, message))
                .installOn(getRootPane());
        viewerController.configureViewerSurfaces();
        setVisible(true);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("permanentFocusOwner", focusChanged);
        viewerController.refreshShortcuts();
    }

    @Override
    public void removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removePropertyChangeListener("permanentFocusOwner", focusChanged);
        super.removeNotify();
    }

    private void initializeUI() {
        setLayout(new BorderLayout());

        // PlatformUi configures screen-menu integration before AWT starts.
        setJMenuBar(menuBar);
        setContentPane(new ReaderWorkspace(viewerController.createThumbnailPanel(), readerSurface,
                navigationPanel, grid -> {
            viewerController.refreshControls();
            imageComponent.setAnimationPaused(grid);
            if (!grid) {
                requestFocusInWindow();
            }
        }));

        scrollPane.getViewport().addComponentListener(new ComponentResizeListener(() -> {
            if (imageComponent.getFitMode() != FitMode.ORIGINAL) {
                resizeRenderTimer.restart();
            }
        }));
        addWindowListener(new WindowCloseListener(viewerController::exit));

        pack();
        setLocationRelativeTo(null);
        setFocusable(true);
        requestFocusInWindow();
    }

    private void applyResizeRender() {
        imageComponent.updateScale();
        imageComponent.revalidate();
        imageComponent.repaint();
    }

    public void openFile(File file) {
        viewerController.openFile(file);
    }
}
