package net.vanfleteren.jazygit.content;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of jazygit-content: those of the whole application, and that its parts depend on each other one way.
 * That no feature draws the content panel, and that the content knows no feature, the compiler sees to (ADR 136).
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.content", importOptions = ImportOption.DoNotIncludeTests.class)
class ContentArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);

    /**
     * The parts of the content, each a subpackage of it, depend on each other one way only: the content panel, in
     * {@code content} itself, uses the rich diff, never the other way round.
     */
    @ArchTest
    static final ArchRule partsAreFreeOfCycles =
            slices().matching("net.vanfleteren.jazygit.content.(*)..").should().beFreeOfCycles();
}
