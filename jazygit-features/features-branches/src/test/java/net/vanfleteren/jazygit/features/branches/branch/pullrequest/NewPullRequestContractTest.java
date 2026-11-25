package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.history.StubHistory;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestCmd.RereadTitle;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * {@link NewPullRequestContract} on a forge of the test's own, which asks what a pull request says with the title read
 * for main: what the forges share, without a forge. Also what git is asked for the title.
 */
class NewPullRequestContractTest extends NewPullRequestContract {

    private static BranchRef ref(BranchName base) {
        return new BranchRef.Remote(new RemoteBranch("origin", base));
    }

    @Override
    protected Model model(List<Branch> branches) {
        return TestModels.localBranchesRead(TestModels.loaded(), branches).model();
    }

    @Override
    protected Next asked(Model model, BranchName branch) {
        return model.idleBranch(branch)
                .map(known -> NewPullRequestUpdate.askPushed(model, known, afterPush()))
                .getOrElse(() -> Next.of(model));
    }

    @Override
    protected AfterPush afterPush() {
        return new Ask();
    }

    @Override
    protected boolean asksWhatItSays(Next next, BranchName branch) {
        return next.cmds()
                .exists(cmd -> cmd instanceof ReadTitle read && read.branch().equals(branch));
    }

    @Override
    protected FormOpening opening(BranchName branch, List<BranchName> bases) {
        return new Opening(branch, bases);
    }

    @Override
    protected BranchRef baseRef(BranchName base) {
        return ref(base);
    }

    @Override
    protected Msg typed(Model model, String title) {
        return new Typed(title);
    }

    @Override
    protected Msg confirmed() {
        return new Confirmed();
    }

    @Test
    void theTitleIsTheSubjectOfTheOldestCommitNotOnTheBase() {
        Git git = StubGit.of().withHistory(new StubHistory() {
            @Override
            public Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base) {
                return Either.right(Option.of(
                        branch.equals(new BranchRef.Local(FEATURE.name())) && base.equals(ref(RELEASE.name()))
                                ? "Fix release"
                                : "wrong"));
            }
        });
        Opening opening = new Opening(FEATURE.name(), List.of(RELEASE.name()));

        assertEquals(
                new NewPullRequestMsg.TitleRead(FEATURE.name(), opening, Option.of("Fix release")),
                new ReadTitle(FEATURE.name(), ref(RELEASE.name()), opening).run(new Env(git)));
        assertEquals(
                new NewPullRequestMsg.BaseTitleRead(FEATURE.name(), RELEASE.name(), Option.of("Fix release")),
                new RereadTitle(FEATURE.name(), RELEASE.name(), ref(RELEASE.name())).run(new Env(git)));
    }

    @Test
    void withoutASubjectTheUserStillGetsToEnterATitle() {
        Git git = StubGit.of().withHistory(new StubHistory() {
            @Override
            public Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base) {
                return Either.left(new GitError.CommandFailed("boom", 128));
            }
        });
        Opening opening = new Opening(FEATURE.name(), List.of(MAIN.name()));
        ReadTitle read = new ReadTitle(FEATURE.name(), ref(MAIN.name()), opening);

        assertEquals(new NewPullRequestMsg.TitleRead(FEATURE.name(), opening, Option.none()), read.run(new Env(git)));
        assertEquals(
                new NewPullRequestMsg.TitleRead(FEATURE.name(), opening, Option.none()),
                read.failed(TestModels.refused("boom")));
    }

    /**
     * The popup of the test's forge.
     */
    record Form(BranchName branch, List<BranchName> bases, BranchName base, String titleRead, String title)
            implements NewPullRequestForm {

        @Override
        public Form withBase(BranchName base) {
            return new Form(branch, bases, base, titleRead, title);
        }

        @Override
        public Form titled(String title) {
            return new Form(branch, bases, base, title, title);
        }

        @Override
        public Option<BranchRef> baseRef(Model model, BranchName base) {
            return Option.of(ref(base));
        }
    }

    record Opening(BranchName branch, List<BranchName> bases) implements FormOpening {

        @Override
        public Form titled(String title) {
            return new Form(branch, bases, bases.head(), title, title);
        }
    }

    /**
     * Once pushed, the title is read for main.
     */
    record Ask() implements AfterPush {

        @Override
        public Next next(Model model, Branch branch) {
            return Next.of(
                    model,
                    new ReadTitle(branch.name(), ref(MAIN.name()), new Opening(branch.name(), List.of(MAIN.name()))));
        }
    }

    record Typed(String title) implements Msg {

        @Override
        public Next apply(Model model) {
            return Next.of(model.popup(Form.class)
                    .map(form -> model.openPopup(
                            new Form(form.branch(), form.bases(), form.base(), form.titleRead(), title)))
                    .getOrElse(model));
        }
    }

    record Confirmed() implements Msg {

        @Override
        public Next apply(Model model) {
            return model.popup(Form.class)
                    .filter(form -> !form.title().isBlank())
                    .map(form -> Next.of(model.withoutPopup(Form.class), new Create(form.branch())))
                    .getOrElse(() -> Next.of(model));
        }
    }

    /**
     * What the test's forge does to create a pull request: nothing.
     */
    record Create(BranchName branch) implements Cmd {

        @Override
        public Msg run(Env env) {
            return new Typed(branch.value());
        }

        @Override
        public Msg failed(Failure failure) {
            return new Typed(branch.value());
        }
    }
}
