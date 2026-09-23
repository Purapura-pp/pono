package org.openpnp.util;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.apache.commons.lang3.SystemUtils;
import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.model.Configuration;
import org.openpnp.model.Location;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.MotionPlanner.CompletionType;
import org.pmw.tinylog.Logger;

import com.google.common.util.concurrent.FutureCallback;

public class UiUtils {
    /**
     * Functional interface for a Runnable that can throw an Exception but returns no value. Splits
     * the difference between Runnable and Callable.
     */
    public interface Thrunnable {
        public void thrun() throws Exception;
    }

    /**
     * This extends the exception class by allowing to specify a task to be executed once the user has agreed.
     */
    public static class ExceptionWithContinuation extends Exception {
        private static final long serialVersionUID = 1L;
    
        protected Thrunnable continuation = null;
        
        public ExceptionWithContinuation(Throwable cause, Thrunnable continuation) {
            super(cause.getMessage(), cause);
            this.continuation = continuation;
        }
        
        public ExceptionWithContinuation(String message, Thrunnable continuation) {
            super(message, null);
            this.continuation = continuation;
        }
        
        public Thrunnable getContinuation() {
            return continuation;
        }
    }

    /**
     * Shortcut for submitMachineTask(Callable) which uses a Thrunnable instead. This allows for
     * simple tasks that may throw an Exception but return nothing.
     * 
     * @param thrunnable
     * @return
     */
    public static Future<Void> submitUiMachineTask(final Thrunnable thrunnable) {
        return submitUiMachineTask(() -> {
            thrunnable.thrun();
            return null;
        });
    }

    /**
     * Wrapper for submitMachineTask(Callable, Consumer, Consumer) which ignores the return value in
     * onSuccess and shows a MessageBox when an Exception is thrown. Handy for simple tasks that
     * don't care about the return value but want to notify the user in case of failure. Ideal for
     * running Machine tasks from ActionListeners.
     * 
     * @param callable
     * @return
     */
    public static <T> Future<T> submitUiMachineTask(final Callable<T> callable) {
        return submitUiMachineTask(callable, (result) -> {
        } , (t) -> {
            showError(t);
        });
    }

    /**
     * Show an error using a message box, if the GUI is present, otherwise just log the error.
     * @param parent
     * @param title
     * @param t
     */
    public static void showError(Component parent, String title, Throwable t) {
        
        // Go through all causes, creating a combined continuation
        Thrunnable combinedContinuation = null;
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof ExceptionWithContinuation) {
                Thrunnable continuation = ((ExceptionWithContinuation)cause).getContinuation();
                if (continuation != null) {
                    if (combinedContinuation == null) {
                        combinedContinuation = continuation; // just take the first one
                    } else {
                        final Thrunnable outerCombinedContinuation = combinedContinuation;
                        combinedContinuation = (() -> { 
                            // combine the two
                            try {
                                continuation.thrun(); // inner first
                            }
                            catch (ExceptionWithContinuation e) {
                                throw new ExceptionWithContinuation(e, () -> {
                                    outerCombinedContinuation.thrun(); // requeue outer, in case of exception
                                });
                            }
                            outerCombinedContinuation.thrun(); // outer
                        });
                    }
                }
            }
        }

        if (parent != null) {
            boolean haveContinuations = combinedContinuation != null;
            boolean execContinuations = MessageBoxes.errorBox(parent, title, t, haveContinuations);

            // execution continuation, if user agrees
            if (haveContinuations && execContinuations) {
                submitUiMachineTask(combinedContinuation);
            }
        }
        else {
            Logger.error(t);
        }
    }

    /**
     * Show an error using a message box. This version provides the simplified interface where the
     * title is fixed to "Error" and the parent is the main frame.
     */
    public static void showError(Throwable t) {
        showError(MainFrame.get(), "Error", t);
    }
    
    /**
     * Functional version of Machine.submit which guarantees that the the onSuccess and onFailure
     * handlers will be run on the Swing event thread.
     * 
     * @param callable
     * @param onSuccess
     * @param onFailure
     * @return
     */
    public static <T> Future<T> submitUiMachineTask(final Callable<T> callable,
            final Consumer<T> onSuccess, final Consumer<Throwable> onFailure) {
        return submitUiMachineTask(callable, onSuccess, onFailure, false);
    }

    /**
     * Functional version of Machine.submit which guarantees that the the onSuccess and onFailure
     * handlers will be run on the Swing event thread. Includes the ignoreEnabled argument.
     * 
     * @param callable
     * @param onSuccess
     * @param onFailure
     * @param ignoreEnabled
     * @return
     */
    public static <T> Future<T> submitUiMachineTask(final Callable<T> callable,
            final Consumer<T> onSuccess, final Consumer<Throwable> onFailure, boolean ignoreEnabled) {
        return Configuration.get().getMachine().submit(callable, new FutureCallback<T>() {
            @Override
            public void onSuccess(T result) {
                try {
                    SwingUtilities.invokeLater(() -> onSuccess.accept(result));
                }
                catch (Exception e) {
                    Logger.error(e, "Could not dispatch the success handler of a machine task to the UI thread.");
                }
            }

            @Override
            public void onFailure(Throwable t) {
                try {
                    SwingUtilities.invokeLater(() -> onFailure.accept(t));
                }
                catch (Exception e) {
                    Logger.error(e, "Could not dispatch the failure handler of a machine task to the UI thread.");
                }
            }
        }, ignoreEnabled);
    }

    /**
     * Functional wrapper for actions that may throw an Exception. Presents an error box to the user
     * with the Exception contents if one is thrown. Basically saves like 5 lines of boilerplate in
     * actions.
     * 
     * @param thrunnable
     */
    public static void messageBoxOnException(Thrunnable thrunnable) {
        try {
            thrunnable.thrun();
        }
        catch (Exception e) {
            showError(e);
        }
    }

    /**
     * Functional wrapper for actions that may throw an Exception. Presents an error box to the user
     * with the Exception contents if one is thrown. The action is performed later on the GUI thread.
     * 
     * @param thrunnable
     */
    public static void messageBoxOnExceptionLater(Thrunnable thrunnable) {
        SwingUtilities.invokeLater(() -> messageBoxOnException(thrunnable));
    }

    /**
     * Some UI actions require the machine to move to a certain location as a prerequisite (e.g. editing a pipeline). 
     * For some actions this might be unexpected for the user. This wrapper asks the user to confirm the move, if not 
     * already at the location. 
     * It also handles a disabled machine and lets the user proceed, if this is an option.
     * It then moves to the location at safe Z, and executes the action thrunnable.
     * The wrapper also handles all the proper GUI/machine task dispatching and shows message boxes on Exceptions.
     * 
     * @param parentComponent 
     * @param moveBeforeActionDescription
     * @param movable
     * @param location
     * @param allowWithoutMove
     * @param actionThrunnable
     */
    public static void confirmMoveToLocationAndAct(Component parentComponent, 
            String moveBeforeActionDescription, HeadMountable movable, 
            Location location, boolean allowWithoutMove,
            final Thrunnable actionThrunnable) {

        messageBoxOnException(() -> {
            if (movable == null || location == null || location.equals(movable.getLocation())) {
                // Already there, just act.
                actionThrunnable.thrun();
            }
            else  {
                confirmMoveToLocationAndAct(parentComponent, moveBeforeActionDescription, allowWithoutMove,
                        () -> {
                            MovableUtils.moveToLocationAtSafeZ(movable, location);
                            MovableUtils.fireTargetedUserAction(movable);
                            movable.waitForCompletion(CompletionType.WaitForStillstand);
                        },
                        actionThrunnable);
            }
        });
    }

    public static void confirmMoveToLocationAndAct(Component parentComponent, 
            String moveBeforeActionDescription, boolean allowWithoutMove,
            final Thrunnable motionThrunnable,
            final Thrunnable actionThrunnable) {
    
        messageBoxOnException(() -> {
            if (moveBeforeActionDescription == null) {
                // No motion given.
                actionThrunnable.thrun();
            }
            else if (Configuration.get().getMachine().isEnabled()) {
                // We need to move there, ask the user to confirm. The buttons say what they do:
                // this used to be Yes, No and Cancel, where No meant "do it without moving".
                org.openpnp.gui.shell.Dialogs.Choice move = org.openpnp.gui.shell.Dialogs.Choice.primary(
                        Translations.getString(allowWithoutMove ? "UiUtils.ConfirmMove.MoveAndGo" //$NON-NLS-1$
                                : "UiUtils.ConfirmMove.Move")) //$NON-NLS-1$
                        .movesMachine();
                String title = String.format(Translations.getString(allowWithoutMove
                        ? "UiUtils.ConfirmMove.Question" : "UiUtils.ConfirmMove.Proceed"), //$NON-NLS-1$ //$NON-NLS-2$
                        moveBeforeActionDescription).replace("\n", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$
                int result;
                if (allowWithoutMove) {
                    result = org.openpnp.gui.shell.Dialogs.ask(parentComponent,
                            org.openpnp.gui.shell.Dialogs.Tone.Warn, "move", title, //$NON-NLS-1$
                            Translations.getString("UiUtils.ConfirmMove.What"), null, //$NON-NLS-1$
                            org.openpnp.gui.shell.Dialogs.Choice.plain(
                                    Translations.getString("UiUtils.ConfirmMove.GoWithoutMove")), //$NON-NLS-1$
                            move);
                    // 1 is the move, 0 going on where it stands.
                }
                else {
                    result = org.openpnp.gui.shell.Dialogs.ask(parentComponent,
                            org.openpnp.gui.shell.Dialogs.Tone.Warn, "move", title, //$NON-NLS-1$
                            Translations.getString("UiUtils.ConfirmMove.What"), null, move) == 0 ? 1 : -1; //$NON-NLS-1$
                }
                if (result == 1) {
                    // Move wanted.
                    UiUtils.submitUiMachineTask(() -> {
                        motionThrunnable.thrun();
                        UiUtils.messageBoxOnExceptionLater(actionThrunnable);
                    });
                }
                else if (result == 0 && allowWithoutMove) {
                    // No move wanted.
                    actionThrunnable.thrun();
                }
            }
            else {
                // We can't move but should.
                if (!allowWithoutMove) {
                    // Just say we can't. 
                    throw new Exception(String.format(
                            Translations.getString("UiUtils.MachineNotEnabled"), //$NON-NLS-1$
                            moveBeforeActionDescription));
                }
                else {
                    // Ask the user if it is OK to proceed without moving. 
                    int result = org.openpnp.gui.shell.Dialogs.ask(parentComponent,
                            org.openpnp.gui.shell.Dialogs.Tone.Warn, "power", //$NON-NLS-1$
                            String.format(Translations.getString("UiUtils.MachineNotEnabled"), //$NON-NLS-1$
                                    moveBeforeActionDescription),
                            Translations.getString("UiUtils.MachineNotEnabled.What"), null, //$NON-NLS-1$
                            org.openpnp.gui.shell.Dialogs.Choice.primary(
                                    Translations.getString("UiUtils.ConfirmMove.GoWithoutMove"))); //$NON-NLS-1$
                    if (result == 0) {
                        actionThrunnable.thrun();
                    }
                }
            }
        });
    }

    /**
     * Browse to the given uri, trying different methods.
     * 
     * @param uri
     */
    /** Shows a folder in the system's file manager, and says so when it cannot. */
    public static void openFolder(java.awt.Component parent, java.io.File folder) {
        try {
            Desktop.getDesktop().open(folder);
        }
        catch (Exception e) {
            MessageBoxes.errorBox(parent, Translations.getString("UiUtils.OpenFolder.Failed"), e); //$NON-NLS-1$
        }
    }

    public static void browseUri(String uri) {
        UiUtils.messageBoxOnException(() -> {
            Logger.trace("Browse to "+uri);
            try {
                // First try the official desktop method.
                if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(uri));
                }
                else {
                    // No luck, try a more direct & hacky method. 
                    // Adapted from Teocci's https://stackoverflow.com/a/51758886 CC BY-SA 4.0
                    Runtime rt = Runtime.getRuntime();
                    if (SystemUtils.IS_OS_WINDOWS) {
                        rt.exec("rundll32 url.dll,FileProtocolHandler " + uri).waitFor();
                    } 
                    else if (SystemUtils.IS_OS_MAC) {
                        String[] cmd = {"open", uri};
                        rt.exec(cmd).waitFor();
                    } 
                    else {
                        // Default to Unix flavor.
                        // See https://portland.freedesktop.org/doc/xdg-open.html
                        String[] cmd = {"xdg-open", uri};
                        rt.exec(cmd).waitFor();
                    }
                }
            }
            catch (Exception e) {
                try {
                    // Still no luck, at least copy the uri to the clipboard.
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(uri), null);
                    // And tell the user.
                    MessageBoxes.infoBox(Translations.getString("UiUtils.Browse.Title"), //$NON-NLS-1$
                            String.format(Translations.getString("UiUtils.Browse.Copied"), uri)); //$NON-NLS-1$
                }
                catch (Exception e1) {
                    // Even that failed, nothing left but to lament.
                    throw new Exception(String.format(Translations.getString("UiUtils.Browse.Failed"), uri), e1); //$NON-NLS-1$
                }
            }
        });
    }

    public static boolean isModalDialogBoxOpen() {
        for( Window w : Window.getWindows() ) {
            if( w.isShowing() && w instanceof Dialog && ((Dialog)w).isModal() ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the currently focused component is a text input component.
     * This is used to prevent global hotkeys (like jog commands) from being
     * triggered when the user is editing text.
     *
     * @return true if a text input component has focus
     */
    public static boolean isTextInputFocused() {
        return isTextInput(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
    }

    /**
     * Whether keys typed into this component are text. A table whose cell editing was started by
     * typing into it keeps the focus itself rather than handing it to the editor, so the table
     * counts too while it is editing: Ctrl+Left there is the caret moving a word, not the machine
     * jogging.
     */
    public static boolean isTextInput(Component focusOwner) {
        if (focusOwner == null) {
            return false;
        }
        // Check for text components (JTextField, JTextArea, JEditorPane, etc.)
        if (focusOwner instanceof JTextComponent) {
            return true;
        }
        if (focusOwner instanceof javax.swing.JTable && ((javax.swing.JTable) focusOwner).isEditing()) {
            return true;
        }
        // Check for spinner editors which contain text fields
        if (focusOwner instanceof JSpinner.DefaultEditor) {
            return true;
        }
        // Check if focused component is inside a JSpinner
        Component parent = focusOwner.getParent();
        while (parent != null) {
            if (parent instanceof JSpinner) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }
}
