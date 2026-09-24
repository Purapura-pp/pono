/*
 * Copyright (C) 2026 Pono
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.gui.calibration;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Ui;

/** A line across the page that says what happened, tinted by how much it matters, with its buttons. */
public final class Banner extends RoundedPanel {
    public enum Tone {
        Info, Warn
    }

    /** The tone the fill and outline are drawn in, handed to the panel before there is a banner. */
    private static final class Shade {
        volatile Tone tone = Tone.Info;
    }

    private final JLabel icon = new JLabel();
    private final JLabel title = new JLabel();
    private final JTextArea text = Forms.paragraph(""); //$NON-NLS-1$
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    private final Shade shade;

    public Banner() {
        this(new Shade());
    }

    private Banner(Shade shade) {
        super(8, () -> shade.tone == Tone.Warn ? Ui.warnSoft() : Ui.accentSoft(),
                () -> Ui.alpha(shade.tone == Tone.Warn ? Ui.warn() : Ui.accent(), 0.35));
        this.shade = shade;
        setLayout(new BorderLayout(10, 0));
        setBorder(new EmptyBorder(8, 12, 8, 10));
        setOpaque(false);
        icon.setVerticalAlignment(JLabel.TOP);
        icon.setBorder(new EmptyBorder(1, 0, 0, 0));
        actions.setOpaque(false);
        title.setFont(Ui.weighted(Ui.BASE, 600));
        title.setVisible(false);
        JPanel words = new JPanel(new BorderLayout(0, 2));
        words.setOpaque(false);
        words.add(title, BorderLayout.NORTH);
        words.add(text, BorderLayout.CENTER);
        add(icon, BorderLayout.WEST);
        add(words, BorderLayout.CENTER);
        add(actions, BorderLayout.EAST);
        setTone(Tone.Info);
    }

    /** The line in bold above the text; none when empty. */
    public void setTitle(String title) {
        this.title.setText(title);
        this.title.setVisible(title != null && !title.isEmpty());
    }

    public void setTone(Tone tone) {
        shade.tone = tone;
        icon.setIcon(Ui.icon(tone == Tone.Warn ? "alert" : "info", 15, tone == Tone.Warn ? Ui.warn() : Ui.accent())); //$NON-NLS-1$ //$NON-NLS-2$
        text.setForeground(Ui.text2());
        title.setForeground(Ui.text());
        repaint();
    }

    public Tone getTone() {
        return shade.tone;
    }

    public void setText(String text) {
        this.text.setText(text);
    }

    public String getText() {
        return text.getText();
    }

    public void setActions(JButton... buttons) {
        actions.removeAll();
        for (JButton button : buttons) {
            actions.add(button);
        }
        actions.setVisible(buttons.length > 0);
        revalidate();
        repaint();
    }
}
