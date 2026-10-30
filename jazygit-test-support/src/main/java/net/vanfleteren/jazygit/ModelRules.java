package net.vanfleteren.jazygit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The rules of the models of jazygit-git and jazygit-gh, the data read from git and GitHub. Each takes them in with
 * {@code ArchTests.in(ModelRules.class)}.
 */
public final class ModelRules {

    /**
     * The data read from git and GitHub is plain values, with nothing that runs anything. jazygit-syntax, which lexes
     * the code of a diff, is pure too.
     */
    @ArchTest
    static final ArchRule modelsArePure = classes()
            .that()
            .resideInAnyPackage("..jazygit.git.model..", "..jazygit.gh.model..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "io.vavr..",
                    "lombok..",
                    "..jazygit.git.model..",
                    "..jazygit.gh.model..",
                    "net.vanfleteren.jazygit.syntax..");
    /**
     * What git and GitHub report are values that never change.
     */
    @ArchTest
    static final ArchRule fieldsAreFinal = fields().that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage("..jazygit.git.model..", "..jazygit.gh.model..")
            .should()
            .beFinal();

    /**
     * What git and GitHub report are values all the way down: what a field holds never changes either.
     */
    @ArchTest
    static final ArchRule modelsAreValuesAllTheWayDown = fields().that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage("..jazygit.git.model..", "..jazygit.gh.model..")
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should(Values.holdAValue());
    /**
     * The models are made of records and interfaces, not of classes that extend each other.
     */
    @ArchTest
    static final ArchRule modelsExtendNothing = classes()
            .that()
            .resideInAnyPackage("..jazygit.git.model..", "..jazygit.gh.model..")
            .should(Values.extendNothing());
    /**
     * A method of a model works something out and returns it: one that returns nothing could only have done
     * something.
     */
    @ArchTest
    static final ArchRule modelsReturnValues = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage("..jazygit.git.model..", "..jazygit.gh.model..")
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should()
            .haveRawReturnType(void.class);

    private ModelRules() {}
}
