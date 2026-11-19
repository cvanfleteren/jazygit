package net.vanfleteren.jazygit.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * A group of modules uses only the groups before it: lib, core, kernel, features, then the app (ADR 159). The build
 * only stops a cycle between two modules, so a kernel module could still use a feature that does not use it; the app
 * sees every module, so this test reads them all.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit", importOptions = ImportOption.DoNotIncludeTests.class)
class GroupsTest {

    private static final String LIB = "..jazygit.lib..";
    private static final String CORE = "..jazygit.core..";
    private static final String KERNEL = "..jazygit.kernel..";
    private static final String FEATURES = "..jazygit.features..";
    private static final String APP = "..jazygit.app..";

    @ArchTest
    static final ArchRule libUsesNoOtherGroup = noClasses()
            .that()
            .resideInAPackage(LIB)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(CORE, KERNEL, FEATURES, APP);

    @ArchTest
    static final ArchRule coreUsesOnlyLib = noClasses()
            .that()
            .resideInAPackage(CORE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(KERNEL, FEATURES, APP);

    @ArchTest
    static final ArchRule kernelUsesNoFeatureNorTheApp = noClasses()
            .that()
            .resideInAPackage(KERNEL)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FEATURES, APP);

    @ArchTest
    static final ArchRule featuresDoNotUseTheApp = noClasses()
            .that()
            .resideInAPackage(FEATURES)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(APP);
}
