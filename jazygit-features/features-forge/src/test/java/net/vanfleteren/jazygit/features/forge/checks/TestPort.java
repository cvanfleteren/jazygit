package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Two forges for the tests of the checks flow, which show the checks of a remote branch, list them as they were read,
 * and answer with commands that say what was asked of them.
 */
enum TestPort implements ChecksPort {
    ONE,
    OTHER;

    static final Operation OPERATION = Operation.of("forge.dialog.rerun.title", "forge.dialog.rerun.title");

    @Override
    public String forge() {
        return name();
    }

    @Override
    public Option<Opening> ofBranch(Model model, BranchRef branch) {
        return branch instanceof BranchRef.Remote(RemoteBranch remote)
                ? Option.of(new Opening(branch, remote, Option.none(), Option.none()))
                : Option.none();
    }

    @Override
    public Option<Opening> ofPullRequest(Model model) {
        return Option.none();
    }

    @Override
    public Cmd load(Model model, ChecksPopup popup) {
        return new Load(this, popup.onForge());
    }

    @Override
    public List<ForgeCheck> ordered(Model model, ChecksPopup popup) {
        return popup.checksRead();
    }

    @Override
    public Option<ChecksPopup.Reached> reached(Model model, ChecksPopup popup) {
        return Option.none();
    }

    @Override
    public Option<OperationCmd> log(ChecksPopup popup, ForgeCheck check) {
        return Option.of(new Asked(this, "log of " + check.name()));
    }

    @Override
    public OperationCmd rerun(ChecksPopup popup, List<RerunPlan.Run> runs) {
        return new Asked(this, "rerun of " + runs.map(RerunPlan.Run::run).mkString(","));
    }

    @Override
    public Next loaded(Next next, RemoteBranch branch, ChecksRead read) {
        return next.and(new Asked(this, "learn from " + read.checks().size() + " on " + branch.shortName()));
    }

    @Override
    public Next reran(Next next) {
        return next.and(new Asked(this, "read the branches again"));
    }

    /**
     * Reading the checks on {@code branch} from {@code port}.
     */
    record Load(TestPort port, RemoteBranch branch) implements Cmd {

        @Override
        public Msg run(Env env) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Msg failed(Failure failure) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Asking {@code port} for {@code what}.
     */
    record Asked(TestPort port, String what) implements OperationCmd {

        @Override
        public Operation operation() {
            return OPERATION;
        }

        @Override
        public Msg run(Env env) {
            throw new UnsupportedOperationException();
        }
    }
}
