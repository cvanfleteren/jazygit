package net.vanfleteren.jazygit;

import static com.tngtech.archunit.base.DescribedPredicate.anyElementThat;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * The rules every module of the core keeps: jazygit-process, jazygit-git and jazygit-gh. Each takes them in with
 * {@code ArchTests.in(CoreRules.class)}. That git and gh know nothing of each other, and the process layer of
 * neither, the compiler sees to: they are not on each other's classpath.
 */
public final class CoreRules {

    /**
     * {@code Exec} is the only place that starts a program. Inside jazygit-process there is nothing else to check.
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
            .callMethod(Runtime.class, "exec", String.class)
            .allowEmptyShould(true);
    /**
     * JSON is read through jazygit-json's {@code Json} and {@code Decoder}; Parsson, which comes along with it, is
     * not used here (ADR 42).
     */
    @ArchTest
    static final ArchRule nothingUsesParsson =
            noClasses().should().dependOnClassesThat().resideInAnyPackage("jakarta.json..", "org.eclipse.parsson..");
    /**
     * What git and gh report, and how they fail, are told as values: what the core offers declares no exception.
     */
    @ArchTest
    static final ArchRule failuresAreValues = methods()
            .that()
            .arePublic()
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should(ArchCondition.from(DescribedPredicate.describe(
                    "declare no exception",
                    method -> method.getThrowsClause().getTypes().stream()
                            .noneMatch(thrown -> thrown.isAssignableTo(Exception.class)))));
    /**
     * State is never kept in a static field.
     */
    @ArchTest
    static final ArchRule staticFieldsKeepNothing = fields().that()
            .areStatic()
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should()
            .beFinal()
            .andShould()
            .notHaveRawType(Values.mutable());
    /**
     * A missing value is Vavr's {@code Option}.
     */
    @ArchTest
    static final ArchRule noJavaUtilOptional = noMethods()
            .that()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should()
            .haveRawReturnType(Optional.class)
            .orShould()
            .haveRawParameterTypes(anyElementThat(equivalentTo(Optional.class)));
    /**
     * Collections are Vavr's, which never change: no method takes or returns one of java.util's.
     */
    @ArchTest
    static final ArchRule noJavaUtilCollections = noMethods()
            .that()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should()
            .haveRawReturnType(javaUtilCollection())
            .orShould()
            .haveRawParameterTypes(anyElementThat(javaUtilCollection()));

    private CoreRules() {}

    private static DescribedPredicate<JavaClass> javaUtilCollection() {
        return assignableTo(Collection.class).or(assignableTo(Map.class));
    }
}
