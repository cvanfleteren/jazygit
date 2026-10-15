package net.vanfleteren.jazygit.feature.repos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import net.vanfleteren.jazygit.feature.commit.CommitPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.ErrorPopup;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

class RecentReposUpdateTest {

    @TempDir
    Path dir;

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

    private static Env env(Path current, RecentRepositories recent, Repositories repositories) {
        return new Env(new StubGitProvider() {
            @Override
            public Path workTree() {
                return current;
            }
        }, StubGitHub.empty(), SettingsStore.NONE, recent, repositories);
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
        Model model = Update.update(TestModels.loaded(), new RecentReposMsg.Listed(List.of("/repos/a"))).model();

        assertEquals(Option.of(new RecentReposPopup(List.of("/repos/a"))), model.popup(RecentReposPopup.class));
    }

    @Test
    void cancellingClosesThePopup() {
        Model open = TestModels.loaded().openPopup(new RecentReposPopup(List.of("/repos/a")));

        assertEquals(Option.none(), Update.update(open, new RecentReposMsg.Cancelled()).model().popup());
    }

    @Test
    void choosingClosesThePopupAndSwitches() {
        Model open = TestModels.loaded().openPopup(new RecentReposPopup(List.of("/repos/a")));

        Next next = Update.update(open, new RecentReposMsg.Chosen("/repos/a"));

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(new RecentReposCmd.SwitchTo("/repos/a")), next.cmds());
    }

    @Test
    void notSwitchingSaysWhy() {
        Failure failure = new Failure.Git(new GitError.NotARepository(Path.of("/repos/a")));

        Model model = Update.update(TestModels.loaded(), new RecentReposMsg.NotSwitched("/repos/a", failure)).model();

        assertEquals(Option.of(new ErrorPopup(new AppError.RepositoryNotOpened("/repos/a", failure))),
                model.popup(ErrorPopup.class));
    }

    @Test
    void listingLeavesOutTheCurrentOneAndForgetsTheOnesThatAreGone() throws Exception {
        Path current = repo("current");
        Path other = repo("other");
        Path gone = dir.resolve("gone");
        InMemory recent = new InMemory(current, gone, other);

        Object listed = new RecentReposCmd.ListRecent().run(env(current, recent, Repositories.NONE));

        assertEquals(new RecentReposMsg.Listed(List.of(other.toString())), listed);
        assertEquals(List.of(current, other), recent.load());
    }

    @Test
    void switchingMakesItTheLastOneOpened() throws Exception {
        Path current = repo("current");
        Path other = repo("other");
        InMemory recent = new InMemory(current, other);
        java.util.List<Path> switched = new ArrayList<>();

        Object msg = new RecentReposCmd.SwitchTo(other.toString()).run(env(current, recent, directory -> {
            switched.add(directory);
            return Either.right(directory);
        }));

        assertEquals(new RecentReposMsg.Switched(other.toString()), msg);
        assertEquals(List.of(other), List.ofAll(switched));
        assertEquals(List.of(other, current), recent.load());
    }

    @Test
    void aRepositoryThatCannotBeOpenedIsForgotten() throws Exception {
        Path current = repo("current");
        Path other = repo("other");
        InMemory recent = new InMemory(current, other);

        Object msg = new RecentReposCmd.SwitchTo(other.toString()).run(env(current, recent, Repositories.NONE));

        RecentReposMsg.NotSwitched notSwitched = assertInstanceOf(RecentReposMsg.NotSwitched.class, msg);
        assertEquals(other.toString(), notSwitched.directory());
        assertEquals(List.of(current), recent.load());
    }
}
