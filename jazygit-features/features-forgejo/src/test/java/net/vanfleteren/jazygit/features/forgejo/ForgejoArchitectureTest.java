package net.vanfleteren.jazygit.features.forgejo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of features-forgejo: those of the whole application, and that it knows nothing of GitHub, so that either
 * can be deleted without the other (ADR 171, 174).
 */
@AnalyzeClasses(
        packages = "net.vanfleteren.jazygit.features.forgejo",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ForgejoArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);

    @ArchTest
    static final ArchRule knowsNoGitHub = noClasses()
            .that()
            .resideInAPackage("net.vanfleteren.jazygit.features.forgejo..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.vanfleteren.jazygit.core.gh..", "net.vanfleteren.jazygit.features.github..");
}
