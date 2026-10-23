package net.vanfleteren.jazygit.feature.start;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.repos.RecentRepo;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class StartUpdateTest {

    private static final RecentRepo A = new RecentRepo("/repos/a", "a", Option.none());
    private static final NoRepositoryPopup OUTSIDE =
            new NoRepositoryPopup("/home/me/notes", true, List.of(A), Option.none());
    private static final Failure FAILED = new Failure.Git(new GitError.NoWorktree(java.nio.file.Path.of("/bare")));

    private static Model starting(boolean exists, List<RecentRepo> recent) {
        return StartUpdate.init("/home/me/notes", exists, recent, Settings.DEFAULT, Option.none());
    }

    private static Model starting() {
        return starting(true, List.of(A));
    }

    private static Model after(Model model, Msg... msgs) {
        return List.of(msgs)
                .foldLeft(model, (before, msg) -> Update.update(before, msg).model());
    }

    @Test
    void itStartsAskingAboutTheFolder() {
        assertEquals(Option.of(OUTSIDE), starting().popup(NoRepositoryPopup.class));
    }

    @Test
    void initMakesTheFolderARepository() {
        Next next = Update.update(starting(), new StartMsg.InitChosen());

        assertEquals(List.of(new StartCmd.Init("/home/me/notes")), next.cmds());
    }

    @Test
    void aFolderThatDoesNotExistIsNotMadeARepository() {
        Next next = Update.update(starting(false, List.of(A)), new StartMsg.InitChosen());

        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void anotherFolderIsOpenedFromTheOneAskedAbout() {
        Model typed = after(starting(), new StartMsg.OpenFolderChosen(), new Msg.TextEdited(" ../code "));

        Next next = Update.update(typed, new StartMsg.FolderConfirmed());

        assertEquals(List.of(new StartCmd.Open("/home/me/notes", "../code")), next.cmds());
    }

    @Test
    void noPathOpensNothing() {
        Model typing = after(starting(), new StartMsg.OpenFolderChosen());

        assertEquals(
                List.empty(),
                Update.update(typing, new StartMsg.FolderConfirmed()).cmds());
    }

    @Test
    void escapeOnThePathGoesBackToTheChoice() {
        Model back = after(starting(), new StartMsg.OpenFolderChosen(), new StartMsg.FolderCancelled());

        assertEquals(Option.of(OUTSIDE), back.popup(NoRepositoryPopup.class));
    }

    @Test
    void aPathToNoFolderIsSaidUnderItAndEditingItClearsThat() {
        Model typed = after(starting(), new StartMsg.OpenFolderChosen(), new Msg.TextEdited("typo"));

        Model notFound = after(typed, new StartMsg.NoRepository("/home/me/notes/typo", false));

        assertEquals(
                Option.of(new OpenFolderPopup(OUTSIDE, "typo", Option.of("/home/me/notes/typo"), Option.none())),
                notFound.popup(OpenFolderPopup.class));
        assertEquals(
                Option.of(new OpenFolderPopup(OUTSIDE, "typos", Option.none(), Option.none())),
                after(notFound, new Msg.TextEdited("typos")).popup(OpenFolderPopup.class));
    }

    @Test
    void aPathGitRefusesIsSaidUnderItAndEditingItClearsThat() {
        Model typed = after(starting(), new StartMsg.OpenFolderChosen(), new Msg.TextEdited("/srv/bare.git"));

        Model failed = after(typed, new StartMsg.Failed(FAILED));

        assertEquals(
                Option.of(new OpenFolderPopup(OUTSIDE, "/srv/bare.git", Option.none(), Option.of(FAILED))),
                failed.popup(OpenFolderPopup.class));
        assertEquals(
                Option.of(new OpenFolderPopup(OUTSIDE, "/srv/bare", Option.none(), Option.none())),
                after(failed, new Msg.TextEdited("/srv/bare")).popup(OpenFolderPopup.class));
    }

    @Test
    void aFolderInNoRepositoryIsAskedAboutInstead() {
        Model typed = after(starting(), new StartMsg.OpenFolderChosen(), new Msg.TextEdited("/tmp/new"));

        Model asked = after(typed, new StartMsg.NoRepository("/tmp/new", true));

        assertEquals(
                Option.of(new NoRepositoryPopup("/tmp/new", true, List.of(A), Option.none())),
                asked.popup(NoRepositoryPopup.class));
    }

    @Test
    void aRecentRepositoryIsOpenedAsItIs() {
        Model picking = after(starting(), new StartMsg.OpenRecentChosen());

        Next next = Update.update(picking, new StartMsg.RecentPicked("/repos/a"));

        assertEquals(Option.of(new OpenRecentPopup(OUTSIDE)), picking.popup(OpenRecentPopup.class));
        assertEquals(List.of(new StartCmd.Open("/repos/a", "/repos/a")), next.cmds());
    }

    @Test
    void withoutRecentRepositoriesThereIsNoneToPick() {
        Model model = starting(true, List.empty());

        assertEquals(model, after(model, new StartMsg.OpenRecentChosen()));
    }

    @Test
    void escapeOnTheRecentRepositoriesGoesBackToTheChoice() {
        Model back = after(starting(), new StartMsg.OpenRecentChosen(), new StartMsg.RecentCancelled());

        assertEquals(Option.of(OUTSIDE), back.popup(NoRepositoryPopup.class));
    }

    @Test
    void aFailureIsShownWithTheChoiceUntilAnotherFolderIsAskedAbout() {
        Model failed = after(starting(), new StartMsg.InitChosen(), new StartMsg.Failed(FAILED));

        assertEquals(Option.of(OUTSIDE.failing(FAILED)), failed.popup(NoRepositoryPopup.class));
        assertEquals(
                Option.of(OUTSIDE.about("/tmp/new", true)),
                after(failed, new StartMsg.OpenFolderChosen(), new StartMsg.NoRepository("/tmp/new", true))
                        .popup(NoRepositoryPopup.class));
    }
}
