package com.peterflanner.twspositionsizer.util;

import javax.swing.JTextField;
import java.awt.Color;

/**
 * Author: Pete
 * Date: 1/8/2017
 * Time: 9:36 PM
 */
public class UIUtils {
    /**
     * Disable a text field, but keep the text black.
     * @param textField the text field to disable
     */
    public static void disableTextField(JTextField textField) {
        textField.setEditable(false);
        textField.setEnabled(false);
        textField.setDisabledTextColor(Color.BLACK);
    }
}
