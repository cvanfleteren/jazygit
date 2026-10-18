package net.vanfleteren.jazygit.feature.branch.movecommits;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.CommitRange;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Notice;
import net.vanfleteren.jazygit.state.Update;

public sealed interface MoveMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return MoveUpdate.update(model, this);
    }

    /**
     * The user asked to move the commit {@code sha}, and the ones after it, to another branch.
     */
    record Requested(Sha sha) implements MoveMsg {}

    /**
     * The commits of {@code from} from {@code oldest} up to its tip were read.
     */
    record RangeRead(Branch.Name from, Sha oldest, CommitRange range) implements MoveMsg {}

    /**
     * Reading the commits of {@code from} that would move failed.
     */
    record RangeFailed(Branch.Name from, Failure failure) implements MoveMsg {}

    /**
     * The user chose where the commits move to: the existing {@code branch}, or else a new branch.
     */
    record TargetChosen(Option<Branch.Name> branch) implements MoveMsg {}

    record TargetCancelled() implements MoveMsg {}

    /**
     * The user entered the name of the new branch.
     */
    record NameConfirmed(String name) implements MoveMsg {}

    record NameCancelled() implements MoveMsg {}

    /**
     * The user chose where the new branch starts.
     */
    record BaseChosen(MoveBase base) implements MoveMsg {}

    record BaseCancelled() implements MoveMsg {}

    /**
     * The user agreed to move commits that are pushed already, and to force-push the branch they are taken off
     * afterwards, or not.
     */
    record PushedConfirmed(boolean forcePush) implements MoveMsg {}

    record PushedCancelled() implements MoveMsg {}

    /**
     * {@code cmd} ran {@code commands} and moved the commits.
     */
    record Moved(MoveCmd.Move cmd, List<ExecutedCommand> commands) implements MoveMsg {}

    /**
     * {@code cmd} ran {@code commands}, and stopped on conflicts while copying the commits onto the new branch.
     */
    record Stopped(MoveCmd.Move cmd, List<ExecutedCommand> commands, Notice.StoppedOnConflicts notice)
            implements MoveMsg {}
}
