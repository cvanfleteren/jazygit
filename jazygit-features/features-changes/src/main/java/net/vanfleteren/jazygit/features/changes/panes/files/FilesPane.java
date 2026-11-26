package net.vanfleteren.jazygit.features.changes.panes.files;

import net.vanfleteren.jazygit.kernel.ui.Slot;

/**
 * What the features can fill in the files pane (ADR 172), which knows no feature. Every mark and every part of the
 * title given is shown, in the order of the features.
 */
public final class FilesPane {

    /**
     * What a feature shows of a file of the working tree, after what git says of it, as the resolving of a conflict
     * marks the files resolved (ADR 198).
     */
    public static final Slot<FileMark> MARK = Slot.of("changes.panes.files.mark");

    /**
     * What the title of the files of the working tree says after what it is, as {@code · 2 of 4 resolved} (ADR 198).
     */
    public static final Slot<String> TITLE = Slot.of("changes.panes.files.title");

    private FilesPane() {}
}
