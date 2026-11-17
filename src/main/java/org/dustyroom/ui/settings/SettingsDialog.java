package org.dustyroom.ui.settings;

import org.dustyroom.be.models.i18n.Language;
import org.dustyroom.ui.i18n.UiText;
import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.models.direction.ReadingMode;
import org.dustyroom.be.models.state.FitMode;
import org.dustyroom.be.models.state.ReaderSettings;
import org.dustyroom.be.models.state.VolumeBoundary;
import org.dustyroom.be.models.state.ThemeMode;
import org.dustyroom.be.settings.ProfileManager;
import org.dustyroom.be.utils.StorageUtils;

import org.dustyroom.ui.components.KeyBindings;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class SettingsDialog extends JDialog {
    private final SettingsHost host;
    private final JComboBox<Language> language = new JComboBox<>(I18n.languages().toArray(Language[]::new));
    private final JComboBox<ThemeMode> theme = new JComboBox<>(ThemeMode.values());
    private final JComboBox<String> direction = UiText.combo("ui.manga_right_to_left_label", "ui.comics_left_to_right_label");
    private final JCheckBox spread = UiText.checkbox("ui.two_page_spreads");
    private final JComboBox<String> scale = UiText.combo("ui.fit_to_window_label", "ui.fit_width_label", "ui.fit_height_label", "ui.original_size_100");
    private final JRadioButton stop = UiText.radio("ui.do_nothing");
    private final JRadioButton wrap = UiText.radio("ui.wrap_pages_within_the_volume");
    private final JRadioButton adjacent = UiText.radio("ui.continue_into_the_adjacent_volume");
    private final JCheckBox thumbnails = UiText.checkbox("ui.show_thumbnails");
    private final JTextField library = new JTextField();
    private final JButton apply = UiText.button("ui.apply");
    private final JButton ok = UiText.button("ui.ok");
    private final JButton cancel = UiText.button("ui.cancel");
    private final JButton changeData = UiText.button("ui.change");
    private final JButton scan = UiText.button("ui.scan_refresh");
    private final ShortcutPanel shortcuts;
    private boolean busy;

    public SettingsDialog(Window owner, SettingsHost host, KeyBindings bindings) {
        super(owner, I18n.text("ui.settings_label"), ModalityType.APPLICATION_MODAL);
        UiText.bind(this, "title", target -> target.setTitle(I18n.text("ui.settings_label")));
        this.host = host;
        shortcuts = new ShortcutPanel(bindings, host.currentSettings().getShortcuts());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(16, 16));
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JList<String> categories = UiText.list("ui.appearance", "ui.reading", "ui.shortcuts", "ui.library", "ui.data");
        categories.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categories.setFixedCellHeight(36);
        categories.setPreferredSize(new Dimension(175, 250));
        categories.setSelectedIndex(0);
        CardLayout layout = new CardLayout();
        JPanel pages = new JPanel(layout);
        pages.add(appearance(), "ui.appearance");
        pages.add(reading(), "ui.reading");
        pages.add(shortcuts, "ui.shortcuts");
        pages.add(library(), "ui.library");
        pages.add(data(), "ui.data");
        categories.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && categories.getSelectedValue() != null) {
                layout.show(pages, categories.getSelectedValue());
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(apply);
        buttons.add(ok);
        cancel.addActionListener(_ -> dispose());
        apply.addActionListener(_ -> save(false));
        ok.addActionListener(_ -> save(true));
        content.add(categories, BorderLayout.WEST);
        content.add(pages, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        load(host.currentSettings());
        setMinimumSize(new Dimension(760, 450));
        pack();
        setLocationRelativeTo(owner);
    }

    private static JPanel top(JPanel content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    private static JTextArea note(String text) {
        JTextArea note = new JTextArea(5, 36);
        UiText.bind(note, "text", target -> target.setText(I18n.text(text)));
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setEditable(false);
        note.setOpaque(false);
        note.setFont(UIManager.getFont("Label.font"));
        return note;
    }

    private JPanel appearance() {
        SettingsForm panel = new SettingsForm();
        panel.row(UiText.label("ui.theme"));
        panel.row(theme);
        UiText.bind(theme, "repaint", Component::repaint);
        panel.row(UiText.label("ui.language"));
        panel.row(language);
        panel.row(note("settings.systemThemeHint"));
        return top(panel);
    }

    private JPanel reading() {
        SettingsForm panel = new SettingsForm();
        panel.row(note("settings.readingHint"));
        panel.row(direction);
        panel.row(spread);
        panel.row(scale);
        panel.row(thumbnails);
        ButtonGroup boundaries = new ButtonGroup();
        boundaries.add(stop);
        boundaries.add(wrap);
        boundaries.add(adjacent);
        panel.row(UiText.label("ui.at_the_first_or_last_page"));
        panel.row(stop);
        panel.row(wrap);
        panel.row(adjacent);
        return top(panel);
    }

    private JPanel library() {
        SettingsForm panel = new SettingsForm();
        panel.row(UiText.label("ui.library_folder"));
        panel.row(library);
        JButton browse = UiText.button("ui.browse");
        browse.addActionListener(_ -> {
            Path selected = chooseDirectory(I18n.text("ui.library_folder"));
            if (selected != null) {
                library.setText(selected.toString());
            }
        });
        panel.row(browse);
        scan.addActionListener(_ -> {
            if (library.getText().isBlank()) {
                error(I18n.text("ui.choose_a_library_folder_first"));
                return;
            }
            try {
                Path root = Path.of(library.getText()).toAbsolutePath().normalize();
                host.scanLibrary(root);
            } catch (RuntimeException exception) {
                error(I18n.text("errors.libraryPath", exception.getMessage()));
            }
        });
        panel.row(scan);
        panel.row(note("settings.scanHint"));
        return top(panel);
    }

    private JPanel data() {
        SettingsForm panel = new SettingsForm();
        Path directory = host.dataDirectory();
        JTextField path = new JTextField(directory == null ? I18n.text("ui.storage_unavailable") : directory.toString());
        path.setEditable(false);
        panel.row(UiText.label("ui.current_data_folder"));
        panel.row(path);
        JButton open = UiText.button("ui.open_folder");
        open.setEnabled(directory != null && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN));
        open.addActionListener(_ -> CompletableFuture.runAsync(() -> {
            try {
                Desktop.getDesktop().open(directory.toFile());
            } catch (IOException | RuntimeException exception) {
                SwingUtilities.invokeLater(() -> error(I18n.text("errors.openData", exception.getMessage())));
            }
        }));
        panel.row(open);
        changeData.setEnabled(directory != null && !StorageUtils.isDirectoryOverridden());
        changeData.addActionListener(_ -> chooseProfile());
        panel.row(changeData);
        panel.row(note(StorageUtils.isDirectoryOverridden()
                ? "settings.fixedDataFolder"
                : "settings.profileHint"));
        return top(panel);
    }

    private void chooseProfile() {
        if (busy) {
            return;
        }
        Path selected = chooseDirectory(I18n.text("ui.choose_data_folder"));
        if (selected == null) {
            return;
        }
        setBusy(true);
        CompletableFuture.supplyAsync(() -> ProfileManager.containsProfile(selected)).whenComplete((existing, failure) -> SwingUtilities.invokeLater(() -> {
            setBusy(false);
            if (failure != null) {
                error(I18n.text("ui.cannot_inspect_the_selected_folder"));
                return;
            }
            String question = existing
                    ? I18n.text("profile.open")
                    : I18n.text("profile.moveQuestion");
            String message = I18n.text("profile.confirm", question);
            if (JOptionPane.showConfirmDialog(this, message, I18n.text("ui.change_data_folder"), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
                host.switchDataDirectory(selected, existing, this::dispose);
            }
        }));
    }

    private void load(ReaderSettings settings) {
        language.setSelectedItem(I18n.language());
        theme.setSelectedItem(settings.getThemeMode());
        direction.setSelectedIndex(settings.getDefaultReadingMode() == ReadingMode.MANGA ? 0 : 1);
        spread.setSelected(settings.isDefaultTwoPageMode());
        scale.setSelectedIndex(switch (settings.getDefaultFitMode()) {
            case FIT_SCREEN -> 0;
            case FIT_WIDTH -> 1;
            case FIT_HEIGHT -> 2;
            case ORIGINAL -> 3;
        });
        stop.setSelected(settings.getVolumeBoundary() == VolumeBoundary.STOP);
        wrap.setSelected(settings.getVolumeBoundary() == VolumeBoundary.WRAP);
        adjacent.setSelected(settings.getVolumeBoundary() == VolumeBoundary.NEXT_VOLUME);
        thumbnails.setSelected(settings.isThumbnailsVisible());
        library.setText(settings.getLibraryDirectory());
    }

    private void save(boolean close) {
        if (busy) {
            return;
        }
        VolumeBoundary boundary = VolumeBoundary.STOP;
        if (wrap.isSelected()) {
            boundary = VolumeBoundary.WRAP;
        } else if (adjacent.isSelected()) {
            boundary = VolumeBoundary.NEXT_VOLUME;
        }
        ReaderSettings selected = host.currentSettings().toBuilder()
                .language(((Language) language.getSelectedItem()).getId())
                .shortcuts(shortcuts.overrides())
                .volumeBoundary(boundary)
                .themeMode((ThemeMode) theme.getSelectedItem())
                .defaultReadingMode(direction.getSelectedIndex() == 0 ? ReadingMode.MANGA : ReadingMode.COMICS)
                .defaultTwoPageMode(spread.isSelected())
                .defaultFitMode(switch (scale.getSelectedIndex()) {
                    case 1 -> FitMode.FIT_WIDTH;
                    case 2 -> FitMode.FIT_HEIGHT;
                    case 3 -> FitMode.ORIGINAL;
                    default -> FitMode.FIT_SCREEN;
                })
                .thumbnailsVisible(thumbnails.isSelected()).libraryDirectory(library.getText().strip()).build();
        setBusy(true);
        host.applySettings(selected, () -> {
            setBusy(false);
            if (close) {
                dispose();
            }
        }, message -> {
            setBusy(false);
            error(message);
        });
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        apply.setEnabled(!busy);
        ok.setEnabled(!busy);
        cancel.setEnabled(!busy);
        scan.setEnabled(!busy);
        changeData.setEnabled(!busy && host.dataDirectory() != null && !StorageUtils.isDirectoryOverridden());
        setDefaultCloseOperation(busy ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
    }

    private Path chooseDirectory(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, I18n.text("ui.settings_label"), JOptionPane.ERROR_MESSAGE);
    }
}
