package org.dustyroom.ui.components;

import org.dustyroom.ui.i18n.UiText;
import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.view.RecentBook;
import org.dustyroom.ui.utils.BrandAssets;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Welcome and reading views share the same window and open action.
 */
public final class ReaderSurface extends JPanel {
    private final CardLayout cards = new CardLayout();
    private final JLabel status = new JLabel(I18n.text("home.formats"), JLabel.CENTER);
    private final RecentBooksPanel recent = new RecentBooksPanel();
    private final JPanel hero = new JPanel();
    private final JPanel compact = new JPanel(new BorderLayout(24, 0));
    private final JTabbedPane home = new JTabbedPane();
    private final Timer hintTimer = new Timer(1000, _ -> hideBoundaryHint());
    private Popup boundaryHint;
    private boolean reading;
    private boolean loading;

    public ReaderSurface(JScrollPane reader, Action open) {
        hintTimer.setRepeats(false);
        setLayout(cards);
        JPanel welcome = new JPanel(new BorderLayout());
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        JLabel title = UiText.label("brand.name");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 32f));
        JLabel slogan = UiText.label("brand.slogan");
        slogan.setFont(slogan.getFont().deriveFont(15f));
        JLabel subtitle = UiText.label("home.dropHint");
        subtitle.setFont(subtitle.getFont().deriveFont(15f));
        for (Component item : new Component[]{new JLabel(BrandAssets.welcomeIcon()),
                Box.createVerticalStrut(24), title, Box.createVerticalStrut(16), slogan,
                Box.createVerticalStrut(10), subtitle, Box.createVerticalStrut(24), openButton(open)}) {
            if (item instanceof JComponent component) {
                component.setAlignmentX(CENTER_ALIGNMENT);
            }
            hero.add(item);
        }
        JPanel identity = new JPanel(new BorderLayout(14, 0));
        identity.add(new JLabel(BrandAssets.aboutIcon()), BorderLayout.WEST);
        JPanel words = new JPanel(new GridLayout(2, 1));
        JLabel compactTitle = UiText.label("brand.name");
        compactTitle.setFont(compactTitle.getFont().deriveFont(Font.BOLD, 24f));
        words.add(compactTitle);
        words.add(UiText.label("brand.slogan"));
        identity.add(words, BorderLayout.CENTER);
        compact.add(identity, BorderLayout.CENTER);
        compact.add(openButton(open), BorderLayout.EAST);
        compact.setMaximumSize(new Dimension(760, 72));
        compact.setVisible(false);
        for (JComponent component : new JComponent[]{hero, compact, status, recent}) {
            component.setAlignmentX(CENTER_ALIGNMENT);
        }
        content.add(hero);
        content.add(compact);
        content.add(Box.createVerticalStrut(16));
        content.add(status);
        content.add(Box.createVerticalStrut(24));
        content.add(recent);
        // Anchor at the top so the first row is visible even in the smallest window.
        welcome.add(content, BorderLayout.NORTH);
        JScrollPane welcomeScroll = new JScrollPane(welcome);
        welcomeScroll.setBorder(BorderFactory.createEmptyBorder());
        welcomeScroll.getVerticalScrollBar().setUnitIncrement(24);
        home.addTab(I18n.text("ui.recently_read"), welcomeScroll);
        add(home, "welcome");
        add(reader, "reader");
        cards.show(this, "welcome");
        UiText.bind(this, "labels", ReaderSurface::refreshLanguage);
    }

    @Override
    public void doLayout() {
        if (compact.isVisible()) {
            int shelfWidth = Math.min(2048, Math.max(0, getWidth() - 80));
            if (shelfWidth != compact.getMaximumSize().width) {
                compact.setMaximumSize(new Dimension(shelfWidth, 72));
            }
            recent.fitShelf(shelfWidth, getHeight());
        }
        super.doLayout();
    }

    private static JButton openButton(Action open) {
        JButton button = new JButton(open);
        UiText.text(button, "ui.open_a_book");
        button.setIcon(new ReaderIcon(ReaderIconKind.OPEN));
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty("FlatLaf.style", "background: $Component.accentColor; foreground: #0D1017");
        button.setMaximumSize(new Dimension(190, 44));
        button.setPreferredSize(new Dimension(190, 44));
        UiText.accessible(button, "ui.open_an_image_zip_cbz_archive_or_folder");
        return button;
    }

    public void setLibrary(JComponent library, Runnable refresh) {
        home.addTab(I18n.text("ui.library"), library);
        home.addChangeListener(_ -> {
            if (home.getSelectedComponent() == library) {
                refresh.run();
            }
        });
    }

    public void setRecentGrouping(boolean grouped, Consumer<Boolean> change) {
        recent.setGrouping(grouped, change);
    }

    public void setRecentHandlers(Consumer<String> open, Consumer<List<String>> remove) {
        recent.setHandlers(open, remove);
    }

    public void showRecentBooks(List<RecentBook> books) {
        hero.setVisible(books.isEmpty());
        compact.setVisible(!books.isEmpty());
        recent.showBooks(books);
    }

    public void showBoundaryHint(String message) {
        hideBoundaryHint();
        if (!reading || !isShowing()) {
            return;
        }
        JToolTip tip = createToolTip();
        tip.setTipText(message);
        Dimension size = tip.getPreferredSize();
        Point anchor = new Point((getWidth() - size.width) / 2, getHeight() - size.height - 16);
        SwingUtilities.convertPointToScreen(anchor, this);
        boundaryHint = PopupFactory.getSharedInstance().getPopup(this, tip, anchor.x, anchor.y);
        boundaryHint.show();
        hintTimer.restart();
    }

    private void hideBoundaryHint() {
        hintTimer.stop();
        if (boundaryHint != null) {
            boundaryHint.hide();
            boundaryHint = null;
        }
    }

    public void showWelcome() {
        reading = false;
        setLoading(false);
        cards.show(this, "welcome");
        UiText.bind(this, "labels", ReaderSurface::refreshLanguage);
    }

    public void showReading() {
        reading = true;
        cards.show(this, "reader");
    }

    private void refreshLanguage() {
        home.setTitleAt(0, I18n.text("ui.recently_read"));
        if (home.getTabCount() > 1) {
            home.setTitleAt(1, I18n.text("ui.library"));
        }
        setLoading(loading);
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        hideBoundaryHint();
        if (!reading) {
            status.setText(loading ? I18n.text("ui.opening_your_book") : I18n.text("home.formats"));
        }
    }
}
