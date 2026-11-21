package net.vanfleteren.jazygit.features.agent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of features-agent: those of the whole application, and that it knows no forge (ADR 161). It reaches a
 * failed build only through the keys the views of a forge host.
 */
@AnalyzeClasses(
        packages = "net.vanfleteren.jazygit.features.agent",
        importOptions = ImportOption.DoNotIncludeTests.class)
class AgentArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);

    @ArchTest
    static final ArchRule knowsNoForge = noClasses()
            .that()
            .resideInAPackage("net.vanfleteren.jazygit.features.agent..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "net.vanfleteren.jazygit.core.gh..",
                    "net.vanfleteren.jazygit.features.github..",
                    "net.vanfleteren.jazygit.kernel.state.github..");
}
