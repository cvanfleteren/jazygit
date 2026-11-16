package net.vanfleteren.jazygit.state.load;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.view.Followed;
import net.vanfleteren.jazygit.state.view.Unlisted;

/**
 * The files the files pane lists for a local branch highlighted in the branches (ADR 141).
 */
public final class BranchFiles {

    private BranchFiles() {}

    /**
     * The files of the local branch {@code branch}, at the commits the branches read last have: against its base (ADR
     * 61), read locally whether it has a pull request or not, or, for the main branch, those its tip commit changed. A
     * branch whose base is not known has none, and says so. The branch checked out here lists the files of the working
     * tree, its changes staged or not. None while its tip is not known.
     */
    public static Option<Followed> of(Model model, BranchName branch) {
        if (model.repo().branch(branch).exists(Branch::current)) {
            return Option.of(new Followed.WorkingTree());
        }
        LoadAhead load = AheadUpdate.of(model, new BranchRef.Local(branch));
        if (AheadUpdate.main(model).contains(branch)) {
            return load.tip().map(tip -> new Followed.Files(new Changes.OfTip(branch, tip)));
        }
        return load.tip()
                .map(tip -> load.base()
                        .<Followed>map(base -> new Followed.Files(new Changes.OfBranch(branch, base, tip)))
                        .getOrElse(() -> new Followed.Nothing(new Unlisted.NoBase(branch))));
    }
}
