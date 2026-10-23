package net.vanfleteren.jazygit.feature.repos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.ui.widgets.PickDialog.Cell;
import org.junit.jupiter.api.Test;

class RecentReposDialogTest {

    private static final String HOME = System.getProperty("user.home");

    @Test
    void aRepositoryShowsItsNameBranchAndWhereItIs() {
        String directory = HOME + "/repos/jazygit";

        PickDialog.Row<String> row = RecentReposDialog.row(new RecentRepo(
                directory, "jazygit", Option.of(new Head.OnBranch(new Branch.Name("main"), Option.none()))));

        assertEquals(directory, row.value());
        assertEquals(
                List.of(
                        Cell.of("jazygit").capped(30),
                        Cell.styled("main", Theme.Classes.REPO_BRANCH).capped(30),
                        Cell.styled("~/repos", Theme.Classes.REPO_LOCATION)),
                row.cells());
    }

    @Test
    void aLinkedWorktreeShowsItsRepositoryAndItself() {
        assertEquals(
                Cell.of("jazygit(jazygit-feature)").capped(30),
                RecentReposDialog.row(new RecentRepo("/srv/jazygit-feature", "jazygit(jazygit-feature)", Option.none()))
                        .cells()
                        .head());
    }

    @Test
    void aDetachedHeadShowsItsCommitAndAnUnreadableOneNothing() {
        Option<Head> detached = Option.of(new Head.Detached(new Sha("0123456789abcdef")));

        assertEquals(
                Cell.styled("0123456", Theme.Classes.DIM).capped(30),
                RecentReposDialog.row(new RecentRepo("/srv/repo", "repo", detached))
                        .cells()
                        .get(1));
        assertEquals(
                Cell.of("").capped(30),
                RecentReposDialog.row(new RecentRepo("/srv/repo", "repo", Option.none()))
                        .cells()
                        .get(1));
    }

    @Test
    void aRepositoryOutsideTheHomeDirectoryIsShownInFull() {
        assertEquals(
                Cell.styled("/srv", Theme.Classes.REPO_LOCATION),
                RecentReposDialog.row(new RecentRepo("/srv/repo", "repo", Option.none()))
                        .cells()
                        .get(2));
        assertEquals("~", RecentReposDialog.shown(HOME));
    }
}
