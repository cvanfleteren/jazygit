package net.vanfleteren.jazygit.feature.repos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.commit.create.CommitPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecentReposUpdateTest {

    private static final RecentRepo A = new RecentRepo("/repos/a", "a", Option.none());

    @TempDir
    Path dir;

    private static Env env(Path current, RecentRepositories recent, Repositories repositories) {
        return new Env(
                new StubGitProvider() {
                    @Override
                    public Path workTree() {
                        return current;
                    }
                },
                StubGitHub.empty(),
                SettingsStore.NONE,
                recent,
                repositories);
    }

    private Path repo(String name) throws Exception {
        return Files.createDirectories(dir.resolve(name));
    }

    @Test
    void requestingListsTheRecentRepositories() {
        Next next = Update.update(TestModels.loaded(), new RecentReposMsg.Requested());

        assertEquals(List.of(new RecentReposCmd.ListRecent()), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void requestingWhileADialogIsOpenLeavesThatDialogOpen() {
        Model typing = TestModels.loaded().openPopup(new CommitPopup());

        Next next = Update.update(typing, new RecentReposMsg.Requested());

        assertEquals(typing, next.model());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void theListedRepositoriesOpenThePopup() {
        Model model = Update.update(TestModels.loaded(), new RecentReposMsg.Listed(List.of(A)))
                .model();

        assertEquals(Option.of(new RecentReposPopup(List.of(A))), model.popup(RecentReposPopup.class));
    }

    @Test
    void cancellingClosesThePopup() {
        Model open = TestModels.loaded().openPopup(new RecentReposPopup(List.of(A)));

        assertEquals(
                Option.none(),
                Update.update(open, new RecentReposMsg.Cancelled()).model().popup());
    }

    @Test
    void choosingClosesThePopupAndSwitches() {
        Model open = TestModels.loaded().openPopup(new RecentReposPopup(List.of(A)));

        Next next = Update.update(open, new RecentReposMsg.Chosen("/repos/a"));

        assertEquals(Option.none(), next.model().popup());
        // A recent repository starts out as the app does.
        assertEquals(List.of(new RecentReposCmd.SwitchTo("/repos/a", Landing.START)), next.cmds());
    }

    @Test
    void notSwitchingSaysWhy() {
        Failure failure = new Failure.Git(new GitError.NotARepository(Path.of("/repos/a")));

        Model model = Update.update(TestModels.loaded(), new RecentReposMsg.NotSwitched("/repos/a", failure))
                .model();

        assertEquals(
                Option.of(new ErrorPopup(new AppError.RepositoryNotOpened("/repos/a", failure))),
                model.popup(ErrorPopup.class));
    }

    @Test
    void listingLeavesOutTheCurrentOneAndForgetsTheOnesThatAreGone() throws Exception {
        Path current = repo("current");
        Path other = repo("other");
        Path gone = dir.resolve("gone");
        InMemory recent = new InMemory(current, gone, other);

        Object listed = new RecentReposCmd.ListRecent().run(env(current, recent, Repositories.NONE));

        assertEquals(
                new RecentReposMsg.Listed(List.of(new RecentRepo(other.toString(), "other", Option.none()))), listed);
        assertEquals(List.of(current, other), recent.load());
    }

    @Test
    void listingReadsWhatTheirHeadPointsTo() throws Exception {
        Path current = repo("current");
        Path other = repo("other");
        Files.createDirectories(other.resolve(".git"));
        Files.writeString(other.resolve(".git/HEAD"), "ref: refs/heads/develop\n");

        Object listed =
                new RecentReposCmd.ListRecent().run(env(current, new InMemory(current, other), Repositories.NONE));

        assertEquals(
                new RecentReposMsg.Listed(List.of(new RecentRepo(
                        other.toString(),
                        "other",
                        Option.of(new Head.OnBranch(new Branch.Name("develop"), Option.none()))))),
                listed);
    }

    @Test
    void switchingMakesItTheLastOneOpened() throws Exception {
        Path current = repo("current");
        Path other = repo("other");
        InMemory recent = new InMemory(current, other);
        Landing landing = new Landing(BranchesTab.WORKTREES, Option.of("branches"));
        java.util.List<Tuple2<Path, Landing>> switched = new ArrayList<>();

        Object msg = new RecentReposCmd.SwitchTo(other.toString(), landing)
                .run(env(current, recent, (directory, shownAt) -> {
                    switched.add(Tuple.of(directory, shownAt));
                    return Either.right(directory);
                }));

        assertEquals(new RecentReposMsg.Switched(other.toString()), msg);
        assertEquals(List.of(Tuple.of(other, landing)), List.ofAll(switched));
        assertEquals(List.of(other, current), recent.load());
    }

    @Test
    void aRepositoryThatCannotBeOpenedIsForgotten() throws Exception {
        Path current = repo("current");
        Path other = repo("other");
        InMemory recent = new InMemory(current, other);

        Object msg = new RecentReposCmd.SwitchTo(other.toString(), Landing.START)
                .run(env(current, recent, Repositories.NONE));

        RecentReposMsg.NotSwitched notSwitched = assertInstanceOf(RecentReposMsg.NotSwitched.class, msg);
        assertEquals(other.toString(), notSwitched.directory());
        assertEquals(List.of(current), recent.load());
    }

    /**
     * Recent repositories kept in memory.
     */
    private static final class InMemory implements RecentRepositories {

        private List<Path> recent;

        InMemory(Path... recent) {
            this.recent = List.of(recent);
        }

        @Override
        public List<Path> load() {
            return recent;
        }

        @Override
        public Try<List<Path>> opened(Path workTree) {
            recent = RecentRepositories.opened(recent, workTree);
            return Try.success(recent);
        }

        @Override
        public Try<List<Path>> forget(List<Path> workTrees) {
            recent = recent.removeAll(workTrees);
            return Try.success(recent);
        }
    }
}
