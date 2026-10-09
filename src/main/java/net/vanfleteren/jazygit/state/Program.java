package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Cmd.BranchCmd.Checkout;
import net.vanfleteren.jazygit.state.Cmd.BranchCmd.CreateBranch;
import net.vanfleteren.jazygit.state.Cmd.BranchCmd.DeleteBranch;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Cmd.Amend;
import net.vanfleteren.jazygit.state.Cmd.Commit;
import net.vanfleteren.jazygit.state.Cmd.Reword;
import net.vanfleteren.jazygit.state.Cmd.Stage;
import net.vanfleteren.jazygit.state.Cmd.StageForCommit;
import net.vanfleteren.jazygit.state.Cmd.Unstage;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * The runtime around {@link Update}: holds the current {@link Model}, performs the commands
 * {@link Update} asks for on the {@code io} executor, and feeds their outcome back as messages on
 * the {@code ui} executor.
 *
 * <p>The model is only read and replaced on the {@code ui} executor's thread, so it needs no
 * synchronisation. {@link #dispatch(Msg)} and {@link #model()} must be called on that thread.
 */
public final class Program {

    private final GitInfoProvider provider;
    private final Executor io;
    private final Executor ui;
    private Model model;

    private Program(GitInfoProvider provider, Executor io, Executor ui) {
        this.provider = provider;
        this.io = io;
        this.ui = ui;
    }

    /**
     * Creates the program and starts its initial loads.
     *
     * @param io where git is called; a single thread keeps git commands from running concurrently
     * @param ui the thread that owns the model, i.e. the render thread
     */
    public static Program start(GitInfoProvider provider, Executor io, Executor ui) {
        Program program = new Program(provider, io, ui);
        program.apply(Update.init(provider.repositoryName()));
        return program;
    }

    public Model model() {
        return model;
    }

    public void dispatch(Msg msg) {
        apply(Update.update(model, msg));
    }

    private void apply(Next next) {
        model = next.model();
        for (Cmd cmd : next.cmds()) {
            io.execute(() -> {
                Msg result = perform(provider, cmd);
                ui.execute(() -> dispatch(result));
            });
        }
    }

    static Msg perform(GitInfoProvider provider, Cmd cmd) {
        try {
            return switch (cmd) {
                case LoadStatus() -> new LoadMsg.StatusLoaded(provider.status());
                case LoadBranches() -> new LoadMsg.BranchesLoaded(provider.branches());
                case LoadCommits() -> new LoadMsg.CommitsLoaded(provider.commits());
                case LoadBranchLog(String branch) -> new LoadMsg.BranchLogLoaded(branch, provider.log(branch));
                case LoadFileDiff(var files) -> new LoadMsg.FileDiffLoaded(files, provider.diff(files));
                case Checkout(String branch) -> {
                    provider.checkout(branch);
                    yield new CheckoutMsg.Done(branch);
                }
                case CreateBranch(String name, String base) -> {
                    provider.createBranch(name, base);
                    yield new NewBranchMsg.Created(name);
                }
                case DeleteBranch(String branch, DeleteBranch.DeleteScope scope) -> {
                    provider.deleteBranch(branch, scope != DeleteBranch.DeleteScope.REMOTE, scope != DeleteBranch.DeleteScope.LOCAL);
                    yield new DeleteBranchMsg.Deleted(branch);
                }
                case Stage(List<String> paths) -> {
                    provider.stage(paths);
                    yield new StageMsg.Done();
                }
                case StageForCommit(List<String> paths) -> {
                    provider.stage(paths);
                    yield new CommitMsg.StagedForCommit();
                }
                case Commit(String summary, String description) -> {
                    provider.commit(summary, description);
                    yield new CommitMsg.Done();
                }
                case Amend(List<String> stage) -> {
                    provider.stage(stage);
                    provider.amend();
                    yield new AmendMsg.Done();
                }
                case Reword(String summary, String description) -> {
                    provider.reword(summary, description);
                    yield new RewordMsg.Done();
                }
                case Unstage(List<String> added, List<String> others) -> {
                    provider.unstageNew(added);
                    provider.unstage(others);
                    yield new StageMsg.Done();
                }
            };
        } catch (RuntimeException e) {
            return failure(cmd, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private static Msg failure(Cmd cmd, String message) {
        return switch (cmd) {
            case Cmd.Load load -> new LoadMsg.Failed(load, message);
            case Checkout(String branch) -> new CheckoutMsg.Failed(branch, message);
            case CreateBranch(String name, String base) -> new NewBranchMsg.Failed(name, message);
            case DeleteBranch(String branch, DeleteBranch.DeleteScope scope) -> new DeleteBranchMsg.Failed(branch, message);
            case Stage stage -> new StageMsg.Failed(message);
            case Unstage unstage -> new StageMsg.Failed(message);
            case StageForCommit stage -> new CommitMsg.StageForCommitFailed(message);
            case Commit commit -> new CommitMsg.Failed(message);
            case Amend amend -> new AmendMsg.Failed(message);
            case Reword reword -> new RewordMsg.Failed(message);
        };
    }
}
