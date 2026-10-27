package net.vanfleteren.jazygit.feature.cherrypick;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface CherryPickMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CherryPickUpdate.update(model, this);
    }

    /**
     * The user copied the commit {@code sha}, to cherry-pick it onto the branch checked out later.
     */
    record Copied(Sha sha) implements CherryPickMsg {}

    /**
     * The user let go of the copied commit.
     */
    record Dropped() implements CherryPickMsg {}

    /**
     * The user asked to cherry-pick the copied commit onto the checked out branch.
     */
    record Requested() implements CherryPickMsg {}

    /**
     * The commit {@code cmd} cherry-picked is on the checked out branch now.
     */
    record Picked(CherryPickCmd cmd, List<ExecutedCommand> commands) implements CherryPickMsg {}
}
