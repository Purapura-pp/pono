/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
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

package org.openpnp.gui.support;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.Converter;
import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.JBindings.WrappedBinding;
import org.openpnp.model.Configuration;
import org.openpnp.model.DisplayPreferences;
import org.openpnp.model.Identifiable;
import org.openpnp.spi.Machine;
import org.openpnp.util.BeanUtils;

public abstract class AbstractConfigurationWizard extends JPanel implements Wizard, Identifiable {
    protected WizardContainer wizardContainer;
    private JButton btnApply;
    private JButton btnReset;
    protected JPanel contentPanel;
    private JScrollPane scrollPane;
    private JPanel panelActions;
    
    private List<AutoBinding> autoBindings = new ArrayList<>();
    private List<WrappedBinding> wrappedBindings = new ArrayList<>();
    private ApplyResetBindingListener listener;
    
    protected String id;
    private boolean forceApplyResetButtonsVisible = false;

    public AbstractConfigurationWizard() {
        setLayout(new BorderLayout());

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        scrollPane = new JScrollPane(contentPanel);

        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        panelActions = new JPanel();
        panelActions.setLayout(new FlowLayout(FlowLayout.RIGHT));
        add(panelActions, BorderLayout.SOUTH);

        btnReset = new JButton(resetAction);
        panelActions.add(btnReset);

        btnApply = new JButton(applyAction);
        panelActions.add(btnApply);
    }

    public abstract void createBindings();

    public void validateInput() throws Exception {

    }

    /**
     * This method should be called when the caller wishes to notify the user that there has been a
     * change to the state of the wizard. This is done automatically for wrapped bindings but this
     * method is provided for operations that do not use wrapped bindings.
     */
    protected void notifyChange() {
        applyAction.setEnabled(true);
        resetAction.setEnabled(true);
    }
    
    /**
     * This method is provided for wizards that do not use wrapped bindings but still want to use 
     * the Apply and Reset buttons to save and/or reset the changes. Normally the buttons are only
     * displayed by wizards that use wrapped bindings. Calling this method forces the buttons to be
     * displayed.
     */
    protected void forceApplyResetButtonsVisible() {
        forceApplyResetButtonsVisible = true;
    }

    /**
     * When overriding this method you should call super.loadFromModel() AFTER doing any work that
     * you need to do, not before.
     */
    protected void loadFromModel() {
        for (WrappedBinding wrappedBinding : wrappedBindings) {
            wrappedBinding.reset();
        }
        applyAction.setEnabled(false);
        resetAction.setEnabled(false);
    }

    /**
     * When overriding this method you should call super.loadFromModel() AFTER doing any work that
     * you need to do, not before.
     */
    protected void saveToModel() {
        try {
            validateInput();
        }
        catch (Exception e) {
            MessageBoxes.errorBox(getTopLevelAncestor(),
                    Translations.getString("AbstractConfigurationWizard.Validation.ErrorBox.Title"), //$NON-NLS-1$
                    e.getMessage());
            // Nothing is written: the values that failed are still on screen to be corrected,
            // and Apply stays available. It used to write them anyway after saying they were
            // wrong, and grey out Apply as if all were well.
            return;
        }
        for (WrappedBinding wrappedBinding : wrappedBindings) {
            wrappedBinding.save();
        }
        applyAction.setEnabled(false);
        resetAction.setEnabled(false);
        if (modifiesConfiguration()) {
            markConfigurationChanged();
        }
    }

    /** Tells the configuration of the machine being configured that it has something to save. */
    private void markConfigurationChanged() {
        try {
            Machine machine = getMachine();
            if (machine instanceof org.openpnp.spi.base.AbstractMachine) {
                Configuration configuration = ((org.openpnp.spi.base.AbstractMachine) machine).getConfiguration();
                if (configuration != null) {
                    configuration.setDirty(true);
                }
            }
        }
        catch (Exception | Error e) {
            // No machine to ask, which is a wizard shown outside the running application: the
            // configuration then reports that it is not initialised with an Error.
        }
    }

    /**
     * Whether applying this form changes what the configuration saves - true for the settings of
     * the machine, its parts and packages. A form that edits the open job instead says no, so
     * that applying it does not report the configuration as unsaved.
     */
    protected boolean modifiesConfiguration() {
        return true;
    }
    
    /**
     * Override this method if the wizard needs to do any additional cleanup like removing property
     * change listeners that may have been added during the wizard's construction. Be sure to also
     * call super.dispose() if this method is overridden.
     */
    public void dispose() {
        for (WrappedBinding wb : wrappedBindings) {
            wb.dispose();
        }
        for (AutoBinding ab : autoBindings) {
            ab.unbind();
        }
    }

    public WrappedBinding addWrappedBinding(Object source, String sourceProperty,
            Object target, String targetProperty, Converter converter) {
        return addWrappedBinding(
                JBindings.bind(source, sourceProperty, target, targetProperty, converter));
    }

    public WrappedBinding addWrappedBinding(Object source, String sourceProperty,
            Object target, String targetProperty) {
        return addWrappedBinding(
                JBindings.bind(source, sourceProperty, target, targetProperty));
    }

    public AutoBinding bind(UpdateStrategy updateStrategy, Object source, String sourceProperty,
            Object target, String targetProperty) {
        AutoBinding autoBinding = BeanUtils.bind(updateStrategy, source, sourceProperty, target, targetProperty);
        autoBindings.add(autoBinding);
        return autoBinding;
    }

    public AutoBinding bind(UpdateStrategy updateStrategy, Object source, String sourceProperty,
            Object target, String targetProperty, Converter converter) {
        AutoBinding autoBinding = BeanUtils.bind(updateStrategy, source, sourceProperty, target, targetProperty, converter);
        autoBindings.add(autoBinding);
        return autoBinding;
    }

    public WrappedBinding addWrappedBinding(WrappedBinding binding) {
        binding.addBindingListener(listener);
        wrappedBindings.add(binding);
        return binding;
    }

    /**
     * The units and formats the fields of this wizard are shown in.
     * <p>
     * A wizard needs these to build its converters, and nothing else about the configuration. Going
     * through here rather than reaching for the singleton in each createBindings() puts every
     * wizard's answer in one place, so that a wizard can eventually be shown values other than the
     * ones the running application happens to hold.
     * 
     * @return
     */
    protected DisplayPreferences getDisplayPreferences() {
        return Configuration.get();
    }

    /**
     * The machine the object being configured belongs to.
     * <p>
     * Most wizards are built for one feeder, camera, axis, driver or nozzle tip, and each of those
     * knows its own machine, so those wizards override this. The rest configure something that is
     * not part of a machine - a package, a set of vision settings - and have only the machine of
     * the moment to go on, which is what every wizard used to ask for.
     * 
     * @return
     */
    protected Machine getMachine() {
        return Configuration.get().getMachine();
    }

    @Override
    public void setWizardContainer(WizardContainer wizardContainer) {
        this.wizardContainer = wizardContainer;
        if (listener == null) { //only allow bindings to be created the first time
            scrollPane.getVerticalScrollBar()
                    .setUnitIncrement(getDisplayPreferences().getVerticalScrollUnitIncrement());
            listener = new ApplyResetBindingListener(applyAction, resetAction);
            createBindings();
            if ((wrappedBindings.isEmpty() && !forceApplyResetButtonsVisible) || actionsHidden) {
                //Since we don't have any wrapped bindings, there is no need to show the panel with
                //the reset and apply buttons unless the forceApplyResetButtonsVisible flag is set
                panelActions.setVisible(false);
            }
            loadFromModel();
        }
    }

    public WizardContainer getWizardContainer() {
        return wizardContainer;
    }
    
    @Override
    public JPanel getWizardPanel() {
        return this;
    }

    @Override
    public String getWizardName() {
        return null;
    }

    protected Action applyAction = new AbstractAction(Translations.getString(
            "AbstractConfigurationWizard.Action.Apply")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            saveToModel();
            wizardContainer.wizardCompleted(AbstractConfigurationWizard.this);
        }
    };

    protected Action resetAction = new AbstractAction(Translations.getString(
            "AbstractConfigurationWizard.Action.Reset")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            loadFromModel();
        }
    };
    
    public Boolean isDirty() {
        return btnApply.isEnabled();
    }

    public void apply() {
        applyAction.actionPerformed(null);
    }

    public void reset() {
        resetAction.actionPerformed(null);
    }

    /** The scroll pane around the content, for a wizard that wants to fit the width it is given. */
    protected JScrollPane getScrollPane() {
        return scrollPane;
    }

    /**
     * Shown on a page among other forms rather than in a column of its own: no scroll bar of its
     * own and as tall as its fields, the wheel going to the page that scrolls them all.
     */
    public void setEmbedded() {
        scrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setWheelScrollingEnabled(false);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        setOpaque(false);
    }

    /**
     * Hide this wizard's own Reset and Apply. The properties column shows one pair for everything
     * it holds, so a pair per sheet would be two rows of the same two buttons.
     */
    public void setActionsShown(boolean shown) {
        actionsHidden = !shown;
        panelActions.setVisible(shown && (!wrappedBindings.isEmpty() || forceApplyResetButtonsVisible));
    }

    private boolean actionsHidden;

    /** The Apply action, for a footer elsewhere to follow: enabled while there are edits to apply. */
    public Action getApplyAction() {
        return applyAction;
    }

    public Action getResetAction() {
        return resetAction;
    }
    
    @Override
    public String getId() {
        return id;
    }

    /**
     * Registers the wizard as having an active process.
     */
    public void processStarting() {
        MainFrame.get().setWizardWithActiveProcess(this);
    }
    
    /**
     * Unregisters the wizard as having an active process.
     */
    public void processCompleted() {
        MainFrame.get().clearWizardWithActiveProcess(this);
    }
}
