package net.vanfleteren.jazygit.feature.files.all;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.LoadAllFiles;

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
        boolean all = !model.repoView().allFiles();
        return new Next(
                model.mapRepoView(view -> view.withAllFiles(all).withUnchanged(all ? view.unchanged() : Option.none())),
                all ? List.<Cmd>of(new LoadAllFiles()) : List.empty());
    }
}
