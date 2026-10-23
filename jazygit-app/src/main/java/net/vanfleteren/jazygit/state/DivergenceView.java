package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Divergence;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * The commits a local branch and another one do not share, as the branches pane lists them.
 *
 * @param branch  the local branch
 * @param other   the branch it is compared with: its upstream, or the main branch
 * @param against what {@code other} is to {@code branch}
 * @param commits the commits only on either of them
 * @param detail  what the commit highlighted in the list changed, once one is highlighted
 */
@With
public record DivergenceView(
        Branch.Name branch,
        BranchRef other,
        Against against,
        Loadable<Divergence> commits,
        Option<Preview<Sha, DiffText>> detail) {

    /**
     * The view of {@code branch} against {@code other}, its commits still to be read.
     */
    public static DivergenceView loading(Branch.Name branch, BranchRef other, Against against) {
        return new DivergenceView(branch, other, against, Loadable.loading(), Option.none());
    }

    /**
     * The load that reads the commits of this view.
     */
    public Cmd.LoadDivergence load() {
        return new Cmd.LoadDivergence(branch, other);
    }

    /**
     * The commit {@code sha}, if it is one of the commits read.
     */
    public Option<Commit> commit(Sha sha) {
        return commits.toOption()
                .flatMap(read -> read.ahead()
                        .commits()
                        .appendAll(read.behind().commits())
                        .find(commit -> commit.sha().equals(sha)));
    }

    /**
     * The branches this view compares, whose moving makes its commits outdated.
     */
    public List<BranchRef> refs() {
        return List.of(new BranchRef.Local(branch), other);
    }

    /**
     * What the other branch of a view is.
     */
    public enum Against {
        /**
         * The upstream of the branch.
         */
        UPSTREAM,
        /**
         * The main branch of the repository.
         */
        BASE
    }
}
