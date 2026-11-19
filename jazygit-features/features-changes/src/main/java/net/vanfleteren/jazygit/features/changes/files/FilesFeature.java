package net.vanfleteren.jazygit.features.changes.files;

import net.vanfleteren.jazygit.features.changes.files.all.AllFilesAction;
import net.vanfleteren.jazygit.features.changes.files.filter.FileFilterAction;
import net.vanfleteren.jazygit.features.changes.files.step.FileStepAction;
import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * Filtering the files, listing all of them, and stepping from the diff of one file to the next.
 */
public final class FilesFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE =
            Feature.of(FileFilterAction.ACTION, AllFilesAction.ACTION, FileStepAction.ACTION);

    private FilesFeature() {}
}
