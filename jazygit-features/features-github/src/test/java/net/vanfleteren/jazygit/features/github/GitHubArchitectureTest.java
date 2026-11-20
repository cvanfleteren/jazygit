package net.vanfleteren.jazygit.features.github;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of features-github: those of the whole application, and that what it draws in the panes of others is
 * drawn from its parts, not the other way round.
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
}
