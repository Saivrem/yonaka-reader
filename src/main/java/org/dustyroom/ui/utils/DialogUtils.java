package org.dustyroom.ui.utils;

import org.dustyroom.be.i18n.I18n;
import lombok.experimental.UtilityClass;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;

@UtilityClass
public class DialogUtils {
    public static String aboutText() {
        String version = ResourceBundle.getBundle("yonaka").getString("version");
        return I18n.text("about.text", version);
    }

    public static void showAbout(JFrame parent) {
        JOptionPane.showMessageDialog(parent,
                aboutText(),
                I18n.text("ui.about_yonaka_reader"), JOptionPane.INFORMATION_MESSAGE,
                BrandAssets.aboutIcon());
    }

    public static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, I18n.text("ui.error"), JOptionPane.ERROR_MESSAGE);
    }
}
