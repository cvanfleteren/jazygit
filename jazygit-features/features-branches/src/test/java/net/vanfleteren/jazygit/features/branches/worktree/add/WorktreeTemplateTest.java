package net.vanfleteren.jazygit.features.branches.worktree.add;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.Worktree;
import org.junit.jupiter.api.Test;

/**
 * Where a new worktree starts out, worked out from a template (ADR 134).
 */
class WorktreeTemplateTest {

    private static final Worktree APP = worktree("/repos/app", true);
    // Next to a bare repository at /repos/proj/.bare: no worktree is the main one.
    private static final Worktree BARE_SHOWN = worktree("/repos/proj/main", false);
    private static final Map<String, String> LOGIN = LinkedHashMap.of("branch", "feature-login");
    private static final Map<String, String> PULL_REQUEST =
            LinkedHashMap.of("branch", "patch-1", "owner", "octo", "name", "repo", "number", "9");

    private static Worktree worktree(String path, boolean main) {
        return new Worktree(
                Path.of(path),
                Option.of(new Head.OnBranch(new BranchName("main"), Option.none())),
                main,
                true,
                Option.none(),
                false);
    }

    private static Option<String> next(String template, Option<Worktree> main, Option<Worktree> current) {
        return WorktreeTemplate.location(Option.of(template), main, current, PULL_REQUEST);
    }

    @Test
    void withoutATemplateItGoesNextToTheMainWorktreeNamedAfterItAndTheBranch() {
        assertEquals(
                Option.of("/repos/app-feature-login"),
                WorktreeTemplate.location(Option.none(), Option.of(APP), Option.of(APP), LOGIN));
    }

    @Test
    void withoutATemplateOrAMainWorktreeItGoesNextToTheOneShownNamedAfterTheBranch() {
        assertEquals(
                Option.of("/repos/proj/feature-login"),
                WorktreeTemplate.location(Option.none(), Option.none(), Option.of(BARE_SHOWN), LOGIN));
    }

    @Test
    void aRelativeTemplateIsNextToTheMainWorktree() {
        assertEquals(
                Option.of("/worktrees/app/patch-1"),
                next("../worktrees/{repo}/{branch}", Option.of(APP), Option.none()));
        // In a bare layout, the repository is named after the directory its worktrees are in.
        assertEquals(
                Option.of("/repos/proj/review-9"),
                next("{branch}/../review-{number}", Option.none(), Option.of(BARE_SHOWN)));
        assertEquals(Option.of("/repos/proj/proj-pr"), next("{repo}-pr", Option.none(), Option.of(BARE_SHOWN)));
    }

    @Test
    void anAbsoluteTemplateIsTakenAsItIsEvenWhenTheWorktreesAreNotKnown() {
        assertEquals(
                Option.of("/home/me/reviews/octo/repo/pr-9"),
                next("/home/me/reviews/{owner}/{name}/pr-{number}", Option.none(), Option.none()));
        assertEquals(Option.none(), next("reviews/pr-{number}", Option.none(), Option.none()));
    }

    @Test
    void aVariableWithoutAValueIsLeftAsItIsWritten() {
        assertEquals(
                Option.of("/repos/app-feature-login-{number}-{nope}-{-}-{"),
                WorktreeTemplate.location(
                        Option.of("{repo}-{branch}-{number}-{nope}-{-}-{"), Option.of(APP), Option.none(), LOGIN));
    }

    @Test
    void whatAVariableIsReplacedWithIsNotLookedAtAgain() {
        assertEquals(
                "x-{repo}-$1\\",
                WorktreeTemplate.filled("x-{branch}", LinkedHashMap.of("branch", "{repo}-$1\\", "repo", "app")));
    }
}
