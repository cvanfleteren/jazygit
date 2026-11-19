package net.vanfleteren.jazygit.xml;

import static com.tngtech.archunit.base.DescribedPredicate.anyElementThat;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
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
 * Rules inside jazygit-xml, which the core's and the app's rules leave out. That it knows nothing of the rest of
 * jazygit is seen to by the compiler.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.xml", importOptions = ImportOption.DoNotIncludeTests.class)
class XmlArchitectureTest {

    /**
     * XML is read by hand (ADR 151): not by the JDK's readers, which read a DTD and fetch what it names unless told not
     * to.
     */
    @ArchTest
    static final ArchRule noReaderOfTheJdk =
            noClasses().should().dependOnClassesThat().resideInAnyPackage("javax.xml..", "org.w3c..", "org.xml..");

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
