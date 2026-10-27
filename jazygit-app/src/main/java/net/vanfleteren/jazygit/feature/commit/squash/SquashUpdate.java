package net.vanfleteren.jazygit.feature.commit.squash;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Squashing a commit into the one below it.
 */
public final class SquashUpdate {

    private SquashUpdate() {}

    public static Next update(Model model, SquashMsg msg) {
        return switch (msg) {
            case SquashMsg.Requested(Sha sha) -> requested(model, sha);
        };
    }

    /**
     * A commit with exactly one parent can be squashed into it: a root commit has nothing to squash into, and
     * a merge has no single commit below it.
     */
    private static Next requested(Model model, Sha sha) {
        return model.commit(sha)
                .flatMap(commit -> onlyParent(commit).map(into -> new SquashPopup(commit, into)))
                .transform(popup -> Update.ask(model, popup));
    }

    /**
     * The parent of {@code commit}, if it has exactly one: a root commit has nothing to squash into, and a
     * merge has no single commit below it.
     */
    public static Option<Sha> onlyParent(Commit commit) {
        return commit.parents().headOption().filter(parent -> commit.parents().size() == 1);
    }
}
