/*
 * Copyright (C) 2026 Pono contributors
 * 
 * This file is part of Pono, a modified version of OpenPnP.
 * 
 * Pono is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Pono is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Pono. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.openpnp.gui.shell;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;


/**
 * One row of buttons standing for a short list of choices, where a drop-down would otherwise hide
 * all but one of them.
 * <p>
 * This is what selects the camera. It was a full width combo box above the image - a click, a list
 * and a second click to change view, and a whole strip of the camera area given to showing the one
 * name that was already visible in the view below it. The choices here are few enough to show.
 * <p>
 * The item API is deliberately the subset of {@link javax.swing.JComboBox} that the camera panel
 * uses, so that the panel's own bookkeeping - which items exist, which is selected, what gets
 * written to the preference - did not have to be rewritten to move the control onto the image.
 */
@SuppressWarnings("serial")
public class PillBar extends JPanel {
    private final List<Object> items = new ArrayList<>();
    private final List<JToggleButton> buttons = new ArrayList<>();
    private final ButtonGroup group = new ButtonGroup();
    private final List<ActionListener> listeners = new ArrayList<>();

    private Object selected;

    /** Set while a selection is being applied, so a button's own event is not taken as input. */
    private boolean applying;

    /** What a pill is labelled with. An item's own text, unless a caller knows better. */
    private Function<Object, String> labeller = String::valueOf;

    /** Items that stay in the model but get no pill; the stylesheet shows fewer choices. */
    private java.util.function.Predicate<Object> hidden = item -> false;

    /**
     * Where an item's pill goes: pills are kept in ascending order of this key, and in model order
     * within a key. The stylesheet lists the cameras before the side-by-side view, which is not
     * the order the model adds them in.
     */
    private Function<Object, Integer> order = item -> 0;

    public void setOrder(Function<Object, Integer> order) {
        this.order = order;
        removeAll();
        for (int key : items.stream().map(order).sorted().distinct().collect(java.util.stream.Collectors.toList())) {
            for (int index = 0; index < items.size(); index++) {
                if (order.apply(items.get(index)) == key) {
                    add(buttons.get(index));
                }
            }
        }
        revalidate();
    }

    public void setHidden(java.util.function.Predicate<Object> hidden) {
        this.hidden = hidden;
        for (int index = 0; index < items.size(); index++) {
            buttons.get(index).setVisible(!hidden.test(items.get(index)));
        }
        revalidate();
    }

    public PillBar() {
        // One row, never wrapped: a wrapping layout asked for a column when nothing had told it
        // how wide it was allowed to be, and this is a bar over an image, not a form.
        setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);
    }

    /**
     * @param labeller Produces the text on the pill. Items keep their own text for identity and
     *                 for the preference that remembers the choice, which is why this is separate.
     */
    public void setLabeller(Function<Object, String> labeller) {
        this.labeller = labeller;
        for (int index = 0; index < items.size(); index++) {
            buttons.get(index).setText(labeller.apply(items.get(index)));
        }
    }

    private Function<Object, javax.swing.Icon> iconer = item -> null;

    /** The icon before a pill's text, such as the camera on a camera's pill; null for none. */
    public void setIconer(Function<Object, javax.swing.Icon> iconer) {
        this.iconer = iconer;
        for (int index = 0; index < items.size(); index++) {
            buttons.get(index).setIcon(iconer.apply(items.get(index)));
        }
    }

    public void addItem(Object item) {
        insertItemAt(item, items.size());
    }

    public void insertItemAt(Object item, int index) {
        JToggleButton button = new Ui.ToggleButton(labeller.apply(item), iconer.apply(item));
        Ui.pill(button);
        button.setVisible(!hidden.test(item));
        button.addActionListener(e -> setSelectedItem(item));
        items.add(index, item);
        buttons.add(index, button);
        group.add(button);
        add(button, index);
        if (order != null) {
            setOrder(order);
        }
        if (items.size() == 1) {
            setSelectedItem(item);
        }
        revalidate();
    }

    public void removeItemAt(int index) {
        Object removed = items.remove(index);
        JToggleButton button = buttons.remove(index);
        group.remove(button);
        remove(button);
        revalidate();
        if (removed == selected) {
            // Leave the selection on something that exists, as a combo box does.
            selected = null;
            if (!items.isEmpty()) {
                setSelectedIndex(Math.min(index, items.size() - 1));
            }
        }
    }

    public int getItemCount() {
        return items.size();
    }

    public Object getItemAt(int index) {
        return items.get(index);
    }

    public Object getSelectedItem() {
        return selected;
    }

    public void setSelectedIndex(int index) {
        setSelectedItem(items.get(index));
    }

    public void setSelectedItem(Object item) {
        int index = items.indexOf(item);
        if (index < 0) {
            return;
        }
        buttons.get(index).setSelected(true);
        if (item == selected || applying) {
            return;
        }
        selected = item;
        applying = true;
        try {
            ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
            for (ActionListener listener : new ArrayList<>(listeners)) {
                listener.actionPerformed(event);
            }
        }
        finally {
            applying = false;
        }
    }

    public void addActionListener(ActionListener listener) {
        listeners.add(listener);
    }

    public void removeActionListener(ActionListener listener) {
        listeners.remove(listener);
    }
}
