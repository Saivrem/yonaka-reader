package org.dustyroom.ui.settings;

import org.dustyroom.be.i18n.I18n;
import org.dustyroom.be.i18n.TranslationFiles;
import org.dustyroom.be.models.i18n.Language;
import org.dustyroom.ui.i18n.UiText;
import org.dustyroom.ui.loading.BackgroundTask;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

final class TranslationPanel extends JPanel {
    private final DefaultComboBoxModel<Language> languages;
    private final TranslationFiles files;

    TranslationPanel(Path directory, DefaultComboBoxModel<Language> languages) {
        super(new BorderLayout());
        this.languages = languages;
        JComboBox<Language> catalog = new JComboBox<>(languages);
        UiText.accessible(catalog, "ui.language");
        files = directory == null ? null : new TranslationFiles(directory);
        SettingsForm form = new SettingsForm();
        form.row(UiText.label("ui.language"));
        form.row(catalog);
        button(form, "translations.add", this::addCatalog);
        button(form, "translations.edit", this::edit);
        button(form, "translations.import", this::importCatalog);
        button(form, "translations.export", this::export);
        add(form, BorderLayout.NORTH);
    }

    private void button(SettingsForm form, String key, FileAction action) {
        JButton button = UiText.button(key);
        button.setEnabled(files != null || key.equals("translations.export"));
        button.addActionListener(_ -> {
            try {
                action.run();
            } catch (IOException exception) {
                error(exception);
            }
        });
        form.row(button);
    }

    private void error(IOException exception) {
        JOptionPane.showMessageDialog(this, exception.getLocalizedMessage(),
                I18n.text("ui.error"), JOptionPane.ERROR_MESSAGE);
    }

    private Language selected() {
        return (Language) languages.getSelectedItem();
    }

    private void addCatalog() throws IOException {
        String name = JOptionPane.showInputDialog(this, I18n.text("translations.name"));
        if (name != null) {
            Language created = create(new Language(name, name, I18n.english().getMessages()));
            if (created != null) {
                edit();
            }
        }
    }

    private Language create(Language candidate) throws IOException {
        while (true) {
            Language draft = candidate;
            try {
                Language saved = runFileTask(() -> files.save(draft, false));
                install(saved);
                return saved;
            } catch (FileAlreadyExistsException exception) {
                String name = JOptionPane.showInputDialog(this, I18n.text("translations.rename", draft.getId()));
                if (name == null) {
                    return null;
                }
                candidate = new Language(name, name, draft.getMessages());
            }
        }
    }

    private void install(Language saved) {
        boolean active = I18n.language().getId().equals(saved.getId());
        I18n.install(saved);
        for (int i = 0; i < languages.getSize(); i++) {
            if (languages.getElementAt(i).getId().equals(saved.getId())) {
                languages.removeElementAt(i);
                break;
            }
        }
        languages.addElement(saved);
        languages.setSelectedItem(saved);
        if (active) {
            UiText.refresh();
        }
    }

    private void edit() {
        Language original = selected();
        TranslationEditor editor = new TranslationEditor(original);
        Object[] options = {I18n.text("translations.save"), I18n.text("ui.cancel")};
        while (JOptionPane.showOptionDialog(this, editor, original.getName(), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]) == 0) {
            Language draft = editor.translation();
            try {
                install(runFileTask(() -> files.save(draft, true)));
                return;
            } catch (IOException exception) {
                error(exception);
            }
        }
    }

    private JFileChooser chooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
        return chooser;
    }

    private void importCatalog() throws IOException {
        JFileChooser chooser = chooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            create(runFileTask(() -> TranslationFiles.read(path)));
        }
    }

    private void export() throws IOException {
        Language language = selected();
        JFileChooser chooser = chooser();
        chooser.setSelectedFile(new File(language.getId() + ".json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = TranslationFiles.withJsonExtension(chooser.getSelectedFile().toPath());
        try {
            runFileTask(() -> {
                TranslationFiles.write(path, language, false);
                return null;
            });
        } catch (FileAlreadyExistsException exception) {
            if (JOptionPane.showConfirmDialog(this, I18n.text("translations.replace", path.getFileName()),
                    I18n.text("translations.export"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                runFileTask(() -> {
                    TranslationFiles.write(path, language, true);
                    return null;
                });
            }
        }
    }

    private <T> T runFileTask(Callable<T> operation) throws IOException {
        JDialog progress = new JDialog(SwingUtilities.getWindowAncestor(this), I18n.text("translations.title"),
                Dialog.ModalityType.APPLICATION_MODAL);
        progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        progress.add(bar);
        progress.pack();
        progress.setLocationRelativeTo(this);
        BackgroundTask<T> task = new BackgroundTask<>(_ -> operation.call(), _ -> progress.dispose());
        task.execute();
        progress.setVisible(true);
        try {
            return task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException cause) {
                throw cause;
            }
            throw new IOException(exception.getCause());
        }
    }

    @FunctionalInterface
    private interface FileAction {
        void run() throws IOException;
    }
}
