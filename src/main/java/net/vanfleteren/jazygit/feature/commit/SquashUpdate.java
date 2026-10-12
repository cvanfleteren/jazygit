package net.vanfleteren.jazygit.feature.commit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Sha;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.Squash;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Squashing a commit into the one below it.
 */
public final class SquashUpdate {

    private SquashUpdate() {
    }

    public static Next update(Model model, SquashMsg msg) {
        return switch (msg) {
            case SquashMsg.Requested(int index) -> requested(model, index);
            case SquashMsg.Cancelled() -> Next.of(model.withoutPopup(SquashPopup.class));
            case SquashMsg.Confirmed() -> model.popup(SquashPopup.class)
                    .map(popup -> Next.of(model.withoutPopup(SquashPopup.class).clearError(),
                            new Squash(popup.commit().sha())))
                    .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * A commit with exactly one parent can be squashed into it: a root commit has nothing to squash into, and
     * a merge has no single commit below it.
     */
    private static Next requested(Model model, int index) {
        return model.commitAt(index)
                .flatMap(commit -> onlyParent(commit)
                        .flatMap(parent -> model.commits().getOrElse(List.empty()).find(c -> c.sha().equals(parent)))
                        .map(into -> new SquashPopup(commit, into)))
                .map(popup -> Next.of(model.clearError().openPopup(popup)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The parent of {@code commit}, if it has exactly one: a root commit has nothing to squash into, and a
     * merge has no single commit below it.
     */
    private static Option<Sha> onlyParent(Commit commit) {
        return commit.parents().headOption().filter(parent -> commit.parents().size() == 1);
    }
}
