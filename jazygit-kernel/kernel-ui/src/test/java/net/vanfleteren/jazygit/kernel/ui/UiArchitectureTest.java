package net.vanfleteren.jazygit.kernel.ui;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of kernel-ui: those of the whole application, and that its parts depend on each other one way.
 * That the views know no feature, the compiler sees to.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.kernel.ui", importOptions = ImportOption.DoNotIncludeTests.class)
class UiArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);

    /**
     * The parts of the ui, each a subpackage of it, depend on each other one way only: the panes of a feature on the
     * widgets, the text and the theme, the content on the panes and the diff. The shell, in {@code ui} itself, is no
     * part: it lays the parts out.
     */
    @ArchTest
    static final ArchRule partsAreFreeOfCycles = slices().matching("net.vanfleteren.jazygit.kernel.ui.(*)..")
            .should()
            .beFreeOfCycles();

    /**
     * The parts of the ui do not use the shell that lays them out, so the shell depends on them one way only.
     */
    @ArchTest
    static final ArchRule partsDoNotUseTheShell = noClasses()
            .that()
            .resideInAPackage("net.vanfleteren.jazygit.kernel.ui.*..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("net.vanfleteren.jazygit.kernel.ui");

    /**
     * What a kind of content is ({@code ui.content}) uses none of the panes, nor the widgets that draw them, so a group
     * of features can add a kind of its own without them.
     */
    @ArchTest
    static final ArchRule contentUsesNoPane = noClasses()
            .that()
            .resideInAPackage("net.vanfleteren.jazygit.kernel.ui.content..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "net.vanfleteren.jazygit.kernel.ui.branches..",
                    "net.vanfleteren.jazygit.kernel.ui.commits..",
                    "net.vanfleteren.jazygit.kernel.ui.files..",
                    "net.vanfleteren.jazygit.kernel.ui.stash..",
                    "net.vanfleteren.jazygit.kernel.ui.github..",
                    "net.vanfleteren.jazygit.kernel.ui.markdown..",
                    "net.vanfleteren.jazygit.kernel.ui.widgets..",
                    "net.vanfleteren.jazygit.kernel.ui.edit..");
}
