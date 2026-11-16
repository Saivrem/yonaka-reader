package org.dustyroom.ui.components;

import org.dustyroom.ui.listeners.MouseClickListener;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public abstract class BookCard extends JPanel {
    private final JLabel cover = new JLabel();
    private final JButton title = new JButton();
    private final JLabel progress = new JLabel();
    private final JLabel availability = new JLabel();
    private final JLabel subtitle = new JLabel("", SwingConstants.CENTER);
    private final JPanel footer = new JPanel(new BorderLayout());
    private final JProgressBar bar = new JProgressBar(0, 100);
    private BufferedImage image;

    protected BookCard(String path, Runnable open) {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        cover.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel heading = new JPanel(new BorderLayout());
        heading.add(title, BorderLayout.NORTH);
        heading.add(subtitle, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.add(cover, BorderLayout.WEST);
        add(body, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.putClientProperty("JButton.buttonType", "borderless");
        title.addActionListener(_ -> open.run());
        title.setToolTipText(path);
        title.putClientProperty("html.disable", true);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
        subtitle.putClientProperty("html.disable", true);
        MouseClickListener activate = new MouseClickListener(event -> {
            if (SwingUtilities.isLeftMouseButton(event) && title.isEnabled()) {
                title.doClick(0);
            }
        });
        for (JComponent surface : List.of(this, heading, body, footer, details, cover, progress, subtitle, availability, bar)) {
            surface.addMouseListener(activate);
        }
        details.add(Box.createVerticalGlue());
        for (JComponent item : List.of(progress, bar, availability)) {
            item.setAlignmentX(LEFT_ALIGNMENT);
            details.add(item);
        }
        details.add(Box.createVerticalGlue());
        body.add(details, BorderLayout.CENTER);
    }

    protected final void setFooter(JComponent leading, JComponent trailing) {
        footer.add(leading, BorderLayout.WEST);
        footer.add(trailing, BorderLayout.EAST);
    }

    protected final void setHeading(String heading, String detail, boolean enabled, String description) {
        title.setText(heading);
        title.setEnabled(enabled);
        title.getAccessibleContext().setAccessibleName(description);
        subtitle.setText(detail);
        subtitle.setVisible(!detail.isBlank());
    }

    protected final void setProgress(String text, int page, int count) {
        progress.setText(text);
        bar.setValue((int) (100L * page / Math.max(1, count)));
    }

    protected final void setAvailability(String message) {
        availability.setText(message);
        availability.setVisible(!message.isBlank());
        availability.setToolTipText(title.getToolTipText());
    }

    final void setCoverSize(Dimension size) {
        cover.setPreferredSize(size);
        setPreferredSize(new Dimension(350, size.height + 108));
        setCover(image);
    }

    public void setCover(BufferedImage image) {
        this.image = image;
        cover.setIcon(image == null ? new ReaderIcon(ReaderIconKind.OPEN)
                : new BookCoverIcon(image, cover.getPreferredSize()));
    }
}
