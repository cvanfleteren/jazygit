package net.vanfleteren.jazygit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Rules about how the parts of jazygit-core depend on each other. That they know nothing of the application is
 * seen to by the compiler: the application is not on this module's classpath.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreArchitectureTest {

    /**
     * The data read from git and GitHub is plain values, with nothing that runs anything.
     */
    @ArchTest
    static final ArchRule modelsArePure = classes()
            .that()
            .resideInAnyPackage("..jazygit.git.model..", "..jazygit.gh.model..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "io.vavr..", "..jazygit.git.model..", "..jazygit.gh.model..");
    /**
     * git and gh are reached each on their own; the features combine them. The process layer runs any program,
     * and knows neither.
     */
    @ArchTest
    static final ArchRule gitKnowsNothingOfGh = noClasses()
            .that()
            .resideInAPackage("..jazygit.git..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..jazygit.gh..");

    @ArchTest
    static final ArchRule ghKnowsNothingOfGit = noClasses()
            .that()
            .resideInAPackage("..jazygit.gh..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..jazygit.git..");

    @ArchTest
    static final ArchRule processKnowsNeitherGitNorGh = noClasses()
            .that()
            .resideInAPackage("..jazygit.process..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..jazygit.git..", "..jazygit.gh..");
    /**
     * {@code Exec} is the only place that starts a program.
     */
    @ArchTest
    static final ArchRule onlyExecStartsPrograms = noClasses()
            .that()
            .resideOutsideOfPackage("..jazygit.process..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.zeroturnaround.exec..")
            .orShould()
            .dependOnClassesThat()
            .belongToAnyOf(ProcessBuilder.class)
            .orShould()
            .callMethod(Runtime.class, "exec", String.class);
    /**
     * The parts of the core depend on each other one way only.
     */
    @ArchTest
    static final ArchRule partsAreFreeOfCycles =
            slices().matching("net.vanfleteren.jazygit.(*)..").should().beFreeOfCycles();
    /**
     * What git and GitHub report are values that never change.
     */
    @ArchTest
    static final ArchRule fieldsAreFinal = fields().that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage("..jazygit.git.model..", "..jazygit.gh.model..")
            .should()
            .beFinal();
}
