package com.group7.airport.ui;

import com.group7.airport.util.SqlErrors;
import com.group7.airport.util.Validators;

import javax.swing.JOptionPane;
import java.sql.SQLException;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/* Runs database work off the UI thread so the window never freezes. */
public final class UiTask {

    @FunctionalInterface
    public interface Work<T> {
        T call() throws Exception;
    }

    private UiTask() { }

    public static <T> void run(Component owner, Work<T> work, Consumer<T> onSuccess) {
        run(owner, work, onSuccess, () -> { });
    }

    /* Same as above; {@code always} runs afterwards whether the work succeeded or failed. */
    public static <T> void run(Component owner, Work<T> work, Consumer<T> onSuccess, Runnable always) {
        owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                owner.setCursor(Cursor.getDefaultCursor());
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError(owner, e.getCause());
                } finally {
                    always.run();
                }
            }
        }.execute();
    }

    /**
     * Input problem  -> warning "Please check your input"
     * SQL problem    -> plain-language explanation of the MySQL error (never a stack trace)
     * anything else  -> its message
     */
    public static void showError(Component owner, Throwable t) {
        if (t instanceof Validators.ValidationException) {
            JOptionPane.showMessageDialog(owner, t.getMessage(), "Please check your input",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (t instanceof SQLException e) {
            JOptionPane.showMessageDialog(owner, SqlErrors.friendly(e), "The database refused this action",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        String msg = t.getMessage() == null ? t.toString() : t.getMessage();
        JOptionPane.showMessageDialog(owner, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
