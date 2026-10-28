package net.vanfleteren.jazygit;

import static com.tngtech.archunit.base.DescribedPredicate.anyElementThat;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Collection;
import java.util.Map;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Revision;

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
     * Only {@code GitArgs} turns a revision, a branch name or a remote into text for git: a branch is given as its
     * full ref, so that a tag of the same name does not win, and nothing can be read as an option (ADR 40). A method
     * reference, like {@code BranchName::value}, is an access too, so it counts as a call does.
     */
    @ArchTest
    static final ArchRule onlyGitArgsWritesRevisionsAndNames = noClasses()
            .that()
            .resideInAPackage("..jazygit.git")
            .and()
            .doNotHaveSimpleName("GitArgs")
            .should()
            .accessTargetWhere(DescribedPredicate.describe(
                    "Revision.toArg(), BranchName.value() or RemoteName.value()",
                    access -> access.getName().equals("toArg")
                                    && access.getTargetOwner().isAssignableTo(Revision.class)
                            || access.getName().equals("value")
                                    && (access.getTargetOwner().isEquivalentTo(BranchName.class)
                                            || access.getTargetOwner().isEquivalentTo(RemoteName.class))));
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

    private static final DescribedPredicate<JavaClass> JAVA_UTIL_COLLECTION =
            assignableTo(Collection.class).or(assignableTo(Map.class));
    /**
     * Collections are Vavr's, which never change: no method takes or returns one of java.util's.
     */
    @ArchTest
    static final ArchRule noJavaUtilCollections = noMethods()
            .that()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should()
            .haveRawReturnType(JAVA_UTIL_COLLECTION)
            .orShould()
            .haveRawParameterTypes(anyElementThat(JAVA_UTIL_COLLECTION));
}
