package org.openpnp.vision.pipeline.ui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;
import javax.swing.JOptionPane;

import org.openpnp.Translations;

public class CvPipelineEditorDialog extends JDialog {
    private CvPipelineEditor editor;

    public CvPipelineEditorDialog(Frame owner, String title, CvPipelineEditor editor) {
        super(owner, title);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (editor.isDirty()) {
                    int chosen = org.openpnp.gui.shell.Dialogs.ask(CvPipelineEditorDialog.this,
                            org.openpnp.gui.shell.Dialogs.Tone.Info, "info", //$NON-NLS-1$
                            Translations.getString("CvPipelineEditorDialog.Closing.Title"), //$NON-NLS-1$
                            Translations.getString("CvPipelineEditorDialog.Closing.What"), //$NON-NLS-1$
                            null,
                            org.openpnp.gui.shell.Dialogs.Choice.plain(
                                    Translations.getString("CvPipelineEditorDialog.Closing.Discard")), //$NON-NLS-1$
                            org.openpnp.gui.shell.Dialogs.Choice.primary(
                                    Translations.getString("CvPipelineEditorDialog.Closing.Save"))); //$NON-NLS-1$
                    int selection = chosen == 1 ? JOptionPane.YES_OPTION
                            : chosen == 0 ? JOptionPane.NO_OPTION : JOptionPane.CANCEL_OPTION;
                    switch (selection) {
                        case JOptionPane.YES_OPTION:
                            super.windowClosing(e);
                            CvPipelineEditorDialog.this.dispose();
                            pipelineChanged();
                            return;
                        case JOptionPane.NO_OPTION:
                            editor.undoEdits();
                            super.windowClosing(e);
                            CvPipelineEditorDialog.this.dispose();
                            return;
                        case JOptionPane.CANCEL_OPTION:
                            return;
                    }
                }
                else {
                    super.windowClosing(e);
                    CvPipelineEditorDialog.this.dispose();
                }
            }
        });
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(editor);
        Rectangle rect = owner.getBounds();
        int wmargin = rect.width/20;
        int hmargin = rect.height/20;
        setBounds(new Rectangle(
                rect.x+wmargin, 
                rect.y+hmargin, 
                rect.width-2*wmargin,
                rect.height-2*hmargin));    
        this.editor = editor;
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        editor.initializeFocus();
    }

    public void pipelineChanged() {
    }
}