package net.vanfleteren.jazygit.kernel.ui;

/**
 * What the features may give to a part of the app, declared by that part beside itself (ADR 172, 202), as a part of
 * the model is declared by its feature: a pane, as the line above a list, or the kernel, as the pictures a view may
 * draw ({@link KernelSlots}). The part asks the {@link Fills} it is given for what the features give for the slot, and
 * knows no feature. Two slots are the same when their names and types are.
 *
 * @param name what it is known by, under the root of the module that declares it, as {@code changes.panes.commits.line}
 * @param type what the features give for it
 * @param <T>  what the features give for it
 */
public record Slot<T>(String name, Class<T> type) {

    /**
     * The slot {@code name}, for which the features give a {@code type}.
     */
    public static <T> Slot<T> of(String name, Class<T> type) {
        return new Slot<>(name, type);
    }
}
