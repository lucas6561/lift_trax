package com.lifttrax.ui;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

@FunctionalInterface
public interface SimpleDocumentListener extends DocumentListener {
    void onChange();

    static SimpleDocumentListener of(Runnable callback) {
        return callback::run;
    }

    @Override
    default void insertUpdate(DocumentEvent e) {
        onChange();
    }

    @Override
    default void removeUpdate(DocumentEvent e) {
        onChange();
    }

    @Override
    default void changedUpdate(DocumentEvent e) {
        onChange();
    }
}
