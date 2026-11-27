package net.vanfleteren.jazygit.features.github;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.AppRules;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.features.github.state.GitHubState;

/**
 * The rules of features-github: those of the whole application, that what it draws in the panes of others is drawn
 * from its parts, not the other way round, and that its packages use each other without going round in a circle.
 */
@AnalyzeClasses(
        packages = "net.vanfleteren.jazygit.features.github",
        importOptions = ImportOption.DoNotIncludeTests.class)
class GitHubArchitectureTest {

    private static final String PANES = "net.vanfleteren.jazygit.features.github.panes..";

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);

    /**
     * The panes use the parts of the feature, so no part uses the panes; only the feature itself hands them over
     * (ADR 167).
     */
    @ArchTest
    static final ArchRule noPartUsesThePanes = noClasses()
            .that()
            .resideInAPackage("net.vanfleteren.jazygit.features.github.*..")
            .and()
            .resideOutsideOfPackage(PANES)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PANES);

    /**
     * The packages under the feature use each other one way (#706): the state of what GitHub said at the bottom, the
     * pull request dialog and the panes on top, which list what the packages under them do. A package that needs
     * something of one above it declares it, as {@code OpensChecks} or {@code FollowsPullRequest}, for that one to
     * implement. The feature itself, in the root package, wires them all and is left out.
     *
     * <p>One edge stays: the part of GitHub's state lists a branch's files by the message of the pull request
     * dialog, as the kernel only asks a part for that (ADR 61, 171).
     */
    @ArchTest
    static final ArchRule partsAreFreeOfCycles = slices().matching("net.vanfleteren.jazygit.features.github.(*)..")
            .should()
            .beFreeOfCycles()
            .ignoreDependency(GitHubState.class, PullRequestMsg.FilesRequested.class);
}
