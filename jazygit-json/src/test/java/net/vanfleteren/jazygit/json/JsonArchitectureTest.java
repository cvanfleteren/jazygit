package net.vanfleteren.jazygit.json;

import static com.tngtech.archunit.base.DescribedPredicate.anyElementThat;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
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

/**
 * Rules inside jazygit-json, which the core's and the app's rules leave out. That it knows nothing of the rest of
 * jazygit is seen to by the compiler.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.json", importOptions = ImportOption.DoNotIncludeTests.class)
class JsonArchitectureTest {

    /**
     * Jackson is only how text is parsed: {@code JsonReader} uses it, behind {@code Json.parse} (ADR 42). The class
     * the compiler makes for its switch on Jackson's tokens counts as part of it.
     */
    @ArchTest
    static final ArchRule onlyTheReaderUsesJackson = noClasses()
            .that(DescribedPredicate.describe(
                    "are not JsonReader or nested in it",
                    type -> !type.getName().startsWith(JsonReader.class.getName())))
            .should()
            .dependOnClassesThat()
            .resideInAPackage("tools.jackson..");

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
