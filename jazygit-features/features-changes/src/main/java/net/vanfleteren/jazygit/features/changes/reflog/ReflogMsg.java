package net.vanfleteren.jazygit.features.changes.reflog;

import io.vavr.collection.Set;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Reading the reflog of HEAD the commits pane shows in a tab (ADR 186).
 */
public sealed interface ReflogMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ReflogUpdate.update(model, this);
    }

    /**
     * {@code cmd} read {@code reflog}.
     */
    record Read(ReadReflog cmd, Reflog reflog) implements ReflogMsg {}

    /**
     * {@code cmd} could not read the reflog, for {@code failure}.
     */
    record Failed(ReadReflog cmd, Failure failure) implements ReflogMsg {}

    /**
     * The entry at {@code place} was highlighted: the next page is read once it is in the last page read.
     */
    record Highlighted(int place) implements ReflogMsg {}

    /**
     * {@code cmd} found that no branch, tag or remote reaches {@code lost}, of the commits it looked at.
     */
    record LostFound(FindLost cmd, Set<Sha> lost) implements ReflogMsg {}

    /**
     * {@code cmd} could not find which commits are lost, for {@code failure}: they are not marked.
     */
    record LostUnknown(FindLost cmd, Failure failure) implements ReflogMsg {}
}
