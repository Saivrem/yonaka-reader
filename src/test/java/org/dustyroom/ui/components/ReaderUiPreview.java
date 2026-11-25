package org.dustyroom.ui.components;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.book.PageKey;
import org.dustyroom.be.models.book.PageRef;
import org.dustyroom.be.models.book.PictureMetadata;
import org.dustyroom.be.models.direction.ReadingMode;
import org.dustyroom.be.models.state.BookState;
import org.dustyroom.be.models.state.FitMode;
import org.dustyroom.be.models.state.ViewerTheme;
import org.dustyroom.be.models.state.ThemeMode;
import org.dustyroom.be.models.view.RecentBook;
import org.dustyroom.be.models.view.ViewerFrame;
import org.dustyroom.ui.actions.ViewerActionState;
import org.dustyroom.ui.actions.ViewerActions;
import org.dustyroom.ui.rendering.ThumbnailRenderer;
import org.dustyroom.ui.utils.BrandAssets;
import org.dustyroom.ui.utils.UiUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Headless visual fixture; writes previews only when explicitly run.
 */
public final class ReaderUiPreview {
    static void main(String[] args) throws Exception {
        File output = new File("build/ui-preview");
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new IllegalStateException("Cannot create " + output);
        }
        BrandAssets.load();
        SwingUtilities.invokeAndWait(() -> {
            for (ViewerTheme theme : ViewerTheme.values()) {
                if (!UiUtils.installTheme(theme)) {
                    throw new IllegalStateException("Cannot install " + theme);
                }
                renderAbout(output, theme);
                render(output, theme, false, 1920, 1080, 1, false);
                render(output, theme, false, 3840, 2160, 1, false);
                render(output, theme, false, 2560, 1440, 1.5, false);
                render(output, theme, false, 1920, 1080, 2, false);
                for (boolean loaded : new boolean[]{false, true}) {
                    render(output, theme, loaded, 1100, false);
                    render(output, theme, loaded, 940, false);
                    if (loaded) {
                        render(output, theme, true, 940, true);
                    }
                }
            }
        });
    }

    private static void renderAbout(File output, ViewerTheme theme) {
        JOptionPane pane = new JOptionPane(I18n.text("about.text", "0.20.0"),
                JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION,
                BrandAssets.aboutIcon());
        pane.setSize(520, 240);
        layout(pane);
        BufferedImage screenshot = new BufferedImage(1040, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = screenshot.createGraphics();
        try {
            graphics.scale(2, 2);
            pane.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(screenshot, "png", new File(output, theme.name().toLowerCase() + "-about.png"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void render(File output, ViewerTheme theme, boolean loaded, int width, boolean grid) {
        render(output, theme, loaded, width, 720, 2, grid);
    }

    private static void render(File output, ViewerTheme theme, boolean loaded, int width, int height,
                               double density, boolean grid) {
        ViewerActions actions = actions();
        ViewerFrame frame = loaded ? new ViewerFrame(samplePage(), "Sample book", null,
                24, 25, 180, true, ReadingMode.MANGA, null) : null;
        ViewerActionState.apply(actions, frame, FitMode.FIT_SCREEN, false);
        ImageComponent image = new ImageComponent();
        image.setOpaque(true);
        image.setBackground(UIManager.getColor("Viewer.background"));
        JScrollPane scroll = new JScrollPane(image);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UIManager.getColor("Viewer.background"));
        ReaderSurface surface = new ReaderSurface(scroll, actions.openFile());
        if (!loaded) {
            List<RecentBook> books = IntStream
                    .range(0, 6)
                    .mapToObj(ReaderUiPreview::sampleBook)
                    .toList();
            surface.showRecentBooks(books);
        }

        NavigationPanel navigation = new NavigationPanel(actions);
        navigation.updateState(frame);
        ThumbnailPanel thumbnails = new ThumbnailPanel((_, _, result) ->
                result.accept(ThumbnailRenderer.render(samplePage())), _ -> {
        }, () -> {
        });
        JPanel content = new ReaderWorkspace(thumbnails, surface, navigation, _ -> {
        });
        thumbnails.setVisible(loaded);
        JRootPane root = new JRootPane();
        root.setJMenuBar(new MenuBar(actions, theme == ViewerTheme.LIGHT ? ThemeMode.LIGHT : ThemeMode.DARK, _ -> true));
        root.setContentPane(content);
        JPanel host = new JPanel(new BorderLayout());
        host.add(root);
        host.setSize(width, height);
        root.setSize(width, height);
        if (loaded) {
            surface.showReading();
            host.addNotify();
            List<PageRef> pages = IntStream
                    .range(0, 180)
                    .mapToObj(index -> {
                        String name = "page" + index;
                        return new PageRef(new PageKey("preview", name), new PictureMetadata(name, "preview", new File(".")));
                    })
                    .toList();
            thumbnails.showPages(pages);
        }
        layout(root);
        if (loaded) {
            thumbnails.showSpread(24, 25);
            image.setImageAndCenter(frame.getImage(), scroll);
            layout(root);
        }
        if (grid) {
            thumbnails.setGridMode(true);
            layout(root);
            JList<?> pageList = (JList<?>) ((JScrollPane) ((BorderLayout) thumbnails.getLayout()).getLayoutComponent(BorderLayout.CENTER)).getViewport().getView();
            pageList.ensureIndexIsVisible(23);
            layout(root);
        }
        if (!loaded && width >= 1920) {
            verifyShelf(root);
        }
        BufferedImage screenshot = new BufferedImage((int) (width * density), (int) (height * density), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = screenshot.createGraphics();
        try {
            graphics.scale(density, density);
            root.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(screenshot, "png", new File(output,
                    theme.name().toLowerCase() + (grid ? "-grid-" : loaded ? "-reading-" : "-welcome-") + width + (height == 720 ? "" : "-" + density + "x") + ".png"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        if (loaded) {
            host.removeNotify();
        }
    }

    private static void verifyShelf(Container root) {
        List<RecentBookCard> cards = new ArrayList<>();
        collectCards(root, cards);
        if (cards.size() != 6) {
            throw new AssertionError("Expected six recent cards");
        }
        for (RecentBookCard card : cards) {
            JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, card);
            JScrollPane scroll = (JScrollPane) viewport.getParent();
            Rectangle bounds = SwingUtilities.convertRectangle(card.getParent(), card.getBounds(), viewport);
            if (scroll.getVerticalScrollBar().isVisible() || scroll.getHorizontalScrollBar().isVisible()
                    || !new Rectangle(viewport.getExtentSize()).contains(bounds)) {
                throw new AssertionError("Recent shelf overflows at " + root.getSize());
            }
        }
    }

    private static void collectCards(Container container, List<RecentBookCard> cards) {
        for (Component child : container.getComponents()) {
            if (child instanceof RecentBookCard card) {
                cards.add(card);
            } else if (child instanceof Container nested) {
                collectCards(nested, cards);
            }
        }
    }

    private static RecentBook sampleBook(int index) {
        String path = index == 0 ? "/books/夜中/Chapter 03.zip" : "/books/Volume " + (index + 1) + ".zip";
        BookState state = new BookState(path, "page-24", 24 + index, 180, true, ReadingMode.MANGA);
        List<BookState> chapters;
        if (index == 0) {
            chapters = IntStream
                    .range(1, 4)
                    .mapToObj(number -> new BookState("/books/夜中/Chapter 0" + number + ".zip",
                            "page", 24, 180, true, ReadingMode.MANGA))
                    .toList();
        } else {
            chapters = List.of(new BookState(path, "page", 24 + index, 180, true, ReadingMode.MANGA));
        }

        boolean available = index != 2;
        BufferedImage cover = null;
        if (available) {
            BufferedImage page = samplePage();
            if (index % 2 == 0) {
                page = page.getSubimage(0, 0, page.getWidth() / 2, page.getHeight());
            }
            cover = ThumbnailRenderer.renderCover(page);
        }
        return new RecentBook(state, available, cover, chapters);
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layout(nested);
            }
        }
        container.doLayout();
    }

    private static BufferedImage samplePage() {
        BufferedImage page = new BufferedImage(850, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        try {
            g.setColor(new Color(0xF3F1EC));
            g.fillRect(0, 0, 850, 600);
            g.setColor(new Color(0x272D35));
            g.setStroke(new BasicStroke(3));
            g.drawRect(28, 28, 380, 540);
            g.drawRect(442, 28, 380, 540);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            g.drawString("CHAPTER 03", 65, 90);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
            g.drawString("The long way home", 65, 123);
            for (int x : new int[]{55, 470}) {
                g.drawRect(x, 160, 325, 180);
                g.drawLine(x, 310, x + 110, 210);
                g.drawLine(x + 110, 210, x + 240, 310);
                g.drawLine(x + 175, 260, x + 245, 195);
                g.drawLine(x + 245, 195, x + 325, 280);
                g.drawOval(x + 250, 180, 30, 30);
                g.drawRect(x, 360, 150, 165);
                g.drawRect(x + 175, 360, 150, 165);
            }
            g.drawString("24", 207, 588);
            g.drawString("25", 622, 588);
        } finally {
            g.dispose();
        }
        return page;
    }

    private static ViewerActions actions() {
        Map<String, Action> values = new HashMap<>();
        return (ViewerActions) Proxy.newProxyInstance(ViewerActions.class.getClassLoader(),
                new Class<?>[]{ViewerActions.class}, (_, method, _) ->
                        values.computeIfAbsent(method.getName(), name -> new AbstractAction(name) {
                            @Override
                            public void actionPerformed(ActionEvent event) {
                            }
                        }));
    }
}
