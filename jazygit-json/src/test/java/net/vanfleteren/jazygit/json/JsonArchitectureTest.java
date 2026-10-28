package net.vanfleteren.jazygit.json;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Rules inside jazygit-json. That it knows nothing of the rest of jazygit is seen to by the compiler.
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
}
