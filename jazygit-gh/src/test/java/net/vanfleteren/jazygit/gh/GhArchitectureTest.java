package net.vanfleteren.jazygit.gh;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.CoreRules;
import net.vanfleteren.jazygit.ModelRules;

/**
 * The rules of jazygit-gh: those of the whole core and of the models, and how its packages depend on each other.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.gh", importOptions = ImportOption.DoNotIncludeTests.class)
class GhArchitectureTest {

    @ArchTest
    static final ArchTests coreRules = ArchTests.in(CoreRules.class);

    @ArchTest
    static final ArchTests modelRules = ArchTests.in(ModelRules.class);

    /**
     * The packages of gh depend one way (ADR 120): {@code GitHub} and {@code CliGitHub} on the roles, each role on
     * nothing of another, the roles on the GraphQL they share, and all of them on the process that runs gh.
     */
    @ArchTest
    static final ArchRule packagesDependOneWay = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("GitHub")
            .definedBy("net.vanfleteren.jazygit.gh")
            .layer("PullRequests")
            .definedBy("..gh.pullrequests..")
            .layer("Reviews")
            .definedBy("..gh.reviews..")
            .layer("Checks")
            .definedBy("..gh.checks..")
            .layer("Stacks")
            .definedBy("..gh.stacks..")
            .layer("Account")
            .definedBy("..gh.account..")
            .layer("GraphQL")
            .definedBy("..gh.graphql..")
            .layer("Process")
            .definedBy("..gh.process..")
            .whereLayer("GitHub")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("PullRequests")
            .mayOnlyBeAccessedByLayers("GitHub")
            .whereLayer("Reviews")
            .mayOnlyBeAccessedByLayers("GitHub")
            .whereLayer("Checks")
            .mayOnlyBeAccessedByLayers("GitHub")
            .whereLayer("Stacks")
            .mayOnlyBeAccessedByLayers("GitHub")
            .whereLayer("Account")
            .mayOnlyBeAccessedByLayers("GitHub")
            .whereLayer("GraphQL")
            .mayOnlyBeAccessedByLayers("PullRequests", "Reviews", "Checks", "Stacks", "Account");
}
