package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * The commits of a branch, local or remote, as the branches pane lists them in place of the branches. They are the
 * log of the highlighted branch, {@link RepoView#branchLog()}, which the branch is while the list is open; so is what
 * of it is not on its base, {@link RepoView#branchAhead()}. Both are read again as for the highlighted branch.
 *
 * @param branch the branch whose commits are listed
 * @param detail what the commit highlighted in the list changed, once one is highlighted
 */
@With
public record BranchCommitsView(BranchRef branch, Option<Preview<Sha, DiffText>> detail) {

    /**
     * The view of the commits of {@code branch}, none highlighted yet.
     */
    public static BranchCommitsView of(BranchRef branch) {
        return new BranchCommitsView(branch, Option.none());
    }

    /**
     * The log of the branch in {@code model}: loading while the one read is of another branch.
     */
    public Loadable<List<Commit>> commits(Model model) {
        return model.repoView()
                .branchLog()
                .filter(log -> log.key().equals(branch))
                .map(Preview::value)
                .getOrElse(Loadable.loading());
    }

    /**
     * The commit {@code sha}, if it is one of the commits of the branch read in {@code model}: of its log, or of
     * what of it is not on its base and the fork point, which are listed in its place.
     */
    public Option<Commit> commit(Model model, Sha sha) {
        List<Commit> ahead = AheadUpdate.branch(model, branch)
                .map(read -> read.commits().appendAll(read.forkPoint()))
                .getOrElse(List.empty());
        return commits(model)
                .toOption()
                .flatMap(commits ->
                        commits.appendAll(ahead).find(commit -> commit.sha().equals(sha)));
    }
}
