package net.vanfleteren.jazygit.features.changes.files.all;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.LoadAllFiles;
import net.vanfleteren.jazygit.kernel.state.view.FilesShown;

/**
 * Listing all files of the working tree in the files pane, or only its changed files (ADR 108). All of them are read
 * when they are first listed, and again with each change to the status while they are; the changed files are listed
 * meanwhile.
 */
public final class AllFilesUpdate {

    private AllFilesUpdate() {}

    public static Next update(Model model, AllFilesMsg msg) {
        return switch (msg) {
            case AllFilesMsg.Toggled() -> toggled(model);
        };
    }

    /**
     * The other of the two lists. Leaving all files drops the node without changes the content panel showed: the
     * tree of changed files highlights a node with changes.
     */
    private static Next toggled(Model model) {
        FilesShown shown = model.repoView().files().shown().toggled();
        return new Next(
                model.mapRepoView(view -> view.mapFiles(files ->
                        files.withShown(shown).withUnchanged(shown.all() ? files.unchanged() : Option.none()))),
                shown.all() ? List.<Cmd>of(new LoadAllFiles()) : List.empty());
    }
}
