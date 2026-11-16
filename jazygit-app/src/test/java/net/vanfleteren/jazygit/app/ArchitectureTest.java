package net.vanfleteren.jazygit.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of jazygit-app: those of the whole application, and its own. That each module of the application uses only
 * those below it the compiler sees to (ADR 136); each keeps the rules of the whole application on its own classes.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.app", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);

    /**
     * The command line stands on its own: it reads how jazygit is asked to start, and knows nothing of what is
     * started.
     */
    @ArchTest
    static final ArchRule cliKnowsNothingOfTheApplication = classes()
            .that()
            .resideInAPackage("..jazygit.app.cli..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "io.vavr..", "picocli..", "..jazygit.app.cli..", "..jazygit.settings..");
    /**
     * The parts of the app depend on each other one way only.
     */
    @ArchTest
    static final ArchRule partsAreFreeOfCycles =
            slices().matching("net.vanfleteren.jazygit.app.(*)..").should().beFreeOfCycles();
}
