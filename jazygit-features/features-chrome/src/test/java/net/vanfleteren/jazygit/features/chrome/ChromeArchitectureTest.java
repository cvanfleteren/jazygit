package net.vanfleteren.jazygit.features.chrome;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of features-chrome: those of the whole application, and that its features use each other one way.
 * That they use no group of features beside or above them, the compiler sees to.
 */
@AnalyzeClasses(
        packages = "net.vanfleteren.jazygit.features.chrome",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ChromeArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);

    /**
     * A feature may use another, but not the other way round as well.
     */
    @ArchTest
    static final ArchRule partsAreFreeOfCycles = slices().matching("net.vanfleteren.jazygit.features.chrome.(*)..")
            .should()
            .beFreeOfCycles();
}
