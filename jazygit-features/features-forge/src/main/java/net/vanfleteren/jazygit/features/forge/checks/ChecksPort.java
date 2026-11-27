package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * What a forge gives the checks flow (ADR 209): where its checks are, the commands that read them, read a log and run
 * failed jobs again, and what else it does with what they bring. The forge gives it as a constant, an enum of one,
 * which travels in the messages and the popup of the flow, so the flow knows no forge: the key {@code C} of the forge
 * that is on sends it (ADR 175).
 */
public interface ChecksPort {

    /**
     * What the forge is called, as {@code GitHub}, in what the checks popup says.
     */
    String forge();

    /**
     * Where the checks of {@code branch} are: the branch on the forge it is, or follows; none when that is not on the
     * forge, or has no checks.
     */
    Option<Opening> ofBranch(Model model, BranchRef branch);

    /**
     * Where the checks of the pull request the open popup shows are, once its details are read; none while there are
     * none to show.
     */
    Option<Opening> ofPullRequest(Model model);

    /**
     * Reads the checks {@code popup} shows, which answers with {@link ChecksMsg.Read}.
     */
    Cmd load(Model model, ChecksPopup popup);

    /**
     * The checks read by {@code popup} in the order they are listed in {@code model}: the failed ones first.
     */
    List<ForgeCheck> ordered(Model model, ChecksPopup popup);

    /**
     * How far the checks of {@code popup} are shown to be in {@code model}, to keep while they are read again, so they
     * are not shown going back; none for a forge that does not tell how long they take.
     */
    Option<ChecksPopup.Reached> reached(Model model, ChecksPopup popup);

    /**
     * Reads the log of {@code check}, one of those read by {@code popup}, to open it in an editor, which answers with
     * {@link ChecksMsg.LogRead}; none when it has none.
     */
    Option<OperationCmd> log(ChecksPopup popup, ForgeCheck check);

    /**
     * Runs the failed jobs of {@code runs} again, of the checks of {@code popup}, which answers with
     * {@link ChecksMsg.Rerun}.
     */
    OperationCmd rerun(ChecksPopup popup, List<RerunPlan.Run> runs);

    /**
     * {@code next}, once the checks on {@code branch} were read as {@code read}: what else the forge does with them,
     * as learning how long they take.
     */
    Next loaded(Next next, RemoteBranch branch, ChecksRead read);

    /**
     * {@code next}, once the forge ran some failed jobs again: what else the forge reads again of what that changed.
     */
    Next reran(Next next);

    /**
     * Where the checks popup opens.
     *
     * @param branch the branch the user asked for the checks of, local or remote
     * @param onForge the branch on the forge whose checks those are: the one a local branch follows
     * @param commit the commit to read the checks of, for a forge that reads them by commit
     * @param from   the popup it was opened from, which Escape goes back to
     */
    record Opening(BranchRef branch, RemoteBranch onForge, Option<Sha> commit, Option<Popup> from) {}
}
