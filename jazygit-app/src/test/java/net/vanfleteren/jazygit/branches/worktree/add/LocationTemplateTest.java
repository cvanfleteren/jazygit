package net.vanfleteren.jazygit.branches.worktree.add;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.branches.branch.checkout.CheckoutModels;
import net.vanfleteren.jazygit.branches.branch.rename.RenameBranchPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.branches.StubConfig;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.ConfigValue;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.NewBranchStart;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import org.junit.jupiter.api.Test;

/**
 * Where a new worktree starts out, from the git configuration (ADR 134).
 */
class LocationTemplateTest {

    private static final BranchName LOGIN = new BranchName("feature/login");
    private static final WorktreeSource BRANCH = new WorktreeSource.Created(
            LOGIN, new NewBranchStart.From(new BranchRef.Local(new BranchName("main")), Option.none()));
    private static final PullRequestSummary OWN = new PullRequestSummary(
            "octo/repo",
            new PrNumber(9),
            "Title",
            "bob",
            new GhBranch("patch-1"),
            false,
            false,
            PullRequest.Checks.NONE,
            PullRequest.Review.NONE,
            Instant.EPOCH,
            "https://github.com/octo/repo/pull/9");
    private static final WorktreeSource PULL_REQUEST = new WorktreeSource.PullRequest(OWN);

    /**
     * A repository whose main worktree is {@code /repos/app}, with {@code /repos/app-patch-1} taken by another.
     */
    private static Model model() {
        return Update.update(
                        TestModels.loaded(SampleData.git()),
                        new LoadMsg.WorktreesLoaded(List.of(
                                worktree("/repos/app", true, true), worktree("/repos/app-patch-1", false, false))))
                .model();
    }

    private static Worktree worktree(String path, boolean main, boolean current) {
        return new Worktree(
                Path.of(path),
                Option.of(new Head.OnBranch(new BranchName("main"), Option.of(new Sha("aaaa")))),
                main,
                current,
                Option.none(),
                false);
    }

    private static Env config(String key, Either<GitError, List<ConfigValue>> values) {
        return config(key, values, Either.right(List.empty()));
    }

    private static Env config(
            String key, Either<GitError, List<ConfigValue>> values, Either<GitError, List<ConfigValue>> others) {
        return new Env(
                StubGit.of().withConfig(new StubConfig() {
                    @Override
                    public Either<GitError, List<ConfigValue>> all(String asked) {
                        return asked.equals(key) ? values : others;
                    }
                }),
                StubGitHub.of());
    }

    private static Either<GitError, List<ConfigValue>> values(String... values) {
        return Either.right(List.of(values).map(value -> new ConfigValue(false, "/home/me/.gitconfig", value)));
    }

    private static Option<String> read(WorktreeSource source, Env env) {
        return ((AddWorktreeMsg.LocationTemplateRead) new ReadWorktreeTemplate(source, false).run(env)).template();
    }

    @Test
    void theTemplateIsReadBeforeTheLocationIsAskedFor() {
        Next next = AddWorktreeUpdate.ask(model(), PULL_REQUEST);

        assertEquals(List.of(new ReadWorktreeTemplate(PULL_REQUEST, false)), next.cmds());
        assertEquals(Option.none(), next.model().popup());
        assertEquals(
                List.of(new ReadWorktreeTemplate(PULL_REQUEST, true)),
                AddWorktreeUpdate.askSwitching(model(), PULL_REQUEST).cmds());
    }

    @Test
    void theLocationStartsOutWhereTheTemplateLeads() {
        Model read = Update.update(
                        model(),
                        new AddWorktreeMsg.LocationTemplateRead(
                                PULL_REQUEST, true, Option.of("../reviews/{owner}-{name}/{repo}-{number}-{branch}")))
                .model();

        assertEquals(
                Option.of(new NewWorktreePopup(PULL_REQUEST, "/reviews/octo-repo/app-9-patch-1", true, true)),
                read.popup());
    }

    @Test
    void withoutATemplateItStartsOutAsBeforeAndATakenLocationGetsANumber() {
        assertEquals(
                Option.of(new NewWorktreePopup(PULL_REQUEST, "/repos/app-patch-1-2")),
                LocationAsked.located(AddWorktreeUpdate.ask(model(), PULL_REQUEST))
                        .model()
                        .popup());
        assertEquals(
                Option.of(new NewWorktreePopup(BRANCH, "/repos/app-feature-login")),
                LocationAsked.located(AddWorktreeUpdate.ask(model(), BRANCH))
                        .model()
                        .popup());
        // A branch has no number: that variable is left as it is written.
        assertEquals(
                Option.of(new NewWorktreePopup(BRANCH, "/repos/feature-login-{number}", false, true)),
                LocationAsked.located(AddWorktreeUpdate.ask(model(), BRANCH), Option.of("{branch}-{number}"))
                        .model()
                        .popup());
    }

    @Test
    void anyOtherWorktreeIsOfTheRepositoryThePullRequestsWereReadFor() {
        Option<String> template = Option.of("{owner}-{name}-{branch}");
        Model read = model().withPrOverview(model().prOverview().withRepositoryName(Option.of("octo/repo")));
        assertEquals(
                Option.of(new NewWorktreePopup(BRANCH, "/repos/octo-repo-feature-login", false, true)),
                LocationAsked.located(AddWorktreeUpdate.ask(read, BRANCH), template)
                        .model()
                        .popup());

        // Before they are read, or without gh, they are left as they are written.
        assertEquals(
                Option.of(new NewWorktreePopup(BRANCH, "/repos/{owner}-{name}-feature-login", false, true)),
                LocationAsked.located(AddWorktreeUpdate.ask(model(), BRANCH), template)
                        .model()
                        .popup());

        // So are they for a name that is not owner/name.
        Model odd = model().withPrOverview(model().prOverview().withRepositoryName(Option.of("repo")));
        assertEquals(
                Option.of(new NewWorktreePopup(BRANCH, "/repos/{owner}-{name}-feature-login", false, true)),
                LocationAsked.located(AddWorktreeUpdate.ask(odd, BRANCH), template)
                        .model()
                        .popup());
    }

    @Test
    void aPopupThatOpenedWhileTheTemplateWasReadStaysOpen() {
        RenameBranchPopup typing = new RenameBranchPopup(LOGIN, "typed");

        Model read = Update.update(
                        model().openPopup(typing),
                        new AddWorktreeMsg.LocationTemplateRead(BRANCH, false, Option.of("/elsewhere")))
                .model();

        assertEquals(Option.of(typing), read.popup());
    }

    @Test
    void anErrorShownWhileTheTemplateWasReadStays() {
        Model asked = AddWorktreeUpdate.ask(CheckoutModels.withCheckoutError(model(), "old", "stale"), BRANCH)
                .model();
        assertEquals(Option.none(), asked.outcome().error());
        Model failed = CheckoutModels.withCheckoutError(asked, "feature", "boom");

        Model read = Update.update(failed, new AddWorktreeMsg.LocationTemplateRead(BRANCH, false, Option.none()))
                .model();

        assertEquals(failed.outcome().error(), read.outcome().error());
    }

    @Test
    void aPullRequestTakesItsOwnTemplateOrElseTheOneForEveryWorktree() {
        assertEquals(
                Option.of("/reviews/{number}"),
                read(
                        PULL_REQUEST,
                        config(ReadWorktreeTemplate.PULL_REQUEST_KEY, values("/reviews/{number}"), values("/all"))));
        assertEquals(Option.of("/all"), read(PULL_REQUEST, config(ReadWorktreeTemplate.KEY, values("/all"))));
        // One of a branch does not take the template of a pull request.
        assertEquals(
                Option.none(),
                read(BRANCH, config(ReadWorktreeTemplate.PULL_REQUEST_KEY, values("/reviews/{number}"))));
        assertEquals(Option.of("/all"), read(BRANCH, config(ReadWorktreeTemplate.KEY, values("/all"))));
    }

    @Test
    void theValueThatHoldsIsTheLastGitReadsAndABlankOneIsNone() {
        assertEquals(Option.of("/local"), read(BRANCH, config(ReadWorktreeTemplate.KEY, values("/global", "/local"))));
        assertEquals(Option.none(), read(BRANCH, config(ReadWorktreeTemplate.KEY, values("/global", " "))));
        // A blank one for pull requests gives way to the one for every worktree.
        assertEquals(
                Option.of("/all"),
                read(PULL_REQUEST, config(ReadWorktreeTemplate.PULL_REQUEST_KEY, values(""), values("/all"))));
    }

    @Test
    void aLeadingTildeIsReadAsTheHomeDirectory() {
        assertEquals(
                Option.of(System.getProperty("user.home") + "/reviews"),
                read(BRANCH, config(ReadWorktreeTemplate.KEY, values("~/reviews"))));
    }

    @Test
    void aConfigurationThatCannotBeReadIsNoTemplate() {
        Either<GitError, List<ConfigValue>> broken = Either.left(new GitError.CommandFailed("bad config line 3", 128));

        assertEquals(Option.none(), read(PULL_REQUEST, config(ReadWorktreeTemplate.KEY, broken, broken)));
        assertEquals(
                new AddWorktreeMsg.LocationTemplateRead(BRANCH, true, Option.none()),
                new ReadWorktreeTemplate(BRANCH, true).failed(new Failure.Git(new GitError.CommandFailed("boom", 1))));
    }
}
