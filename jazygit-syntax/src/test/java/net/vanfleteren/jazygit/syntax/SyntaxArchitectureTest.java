package net.vanfleteren.jazygit.syntax;

import static com.tngtech.archunit.base.DescribedPredicate.anyElementThat;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Rules inside jazygit-syntax, which the core's and the app's rules leave out. That it knows nothing of the rest of
 * jazygit, diffs included, is seen to by the compiler.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.syntax", importOptions = ImportOption.DoNotIncludeTests.class)
class SyntaxArchitectureTest {

    /**
     * Lexing is pure: it uses the JDK and Vavr, and nothing else.
     */
    @ArchTest
    static final ArchRule usesOnlyTheJdkAndVavr = classes()
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "io.vavr..", "net.vanfleteren.jazygit.syntax..");

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
            .notHaveRawType(assignableTo(Collection.class).or(assignableTo(Map.class)));

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

    private static final DescribedPredicate<JavaClass> JAVA_UTIL_COLLECTION =
            assignableTo(Collection.class).or(assignableTo(Map.class));
    /**
     * Collections are Vavr's, which never change: no method takes or returns one of java.util's, as in the core.
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
