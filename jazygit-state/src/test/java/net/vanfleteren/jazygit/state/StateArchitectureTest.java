package net.vanfleteren.jazygit.state;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of jazygit-state: those of the whole application, and that its parts depend on each other one way.
 * That the state knows neither the features nor the views, the compiler sees to.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.state", importOptions = ImportOption.DoNotIncludeTests.class)
class StateArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);

    /**
     * The parts of the state, each a subpackage of it, depend on each other one way only. The core, in {@code state}
     * itself, is no part: the {@code Model} holds them all, and they all work on it (ADR 107).
     */
    @ArchTest
    static final ArchRule partsAreFreeOfCycles =
            slices().matching("net.vanfleteren.jazygit.state.(*)..").should().beFreeOfCycles();
}
