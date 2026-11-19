package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * The rules of {@link ContentArchitectureTest} that no class tells kinds of content apart catch a pattern switch as
 * well as {@code instanceof}: the switch compiles to no {@code instanceof}, which ArchUnit alone would see.
 */
class ContentRulesCatchTest {

    private static final JavaClasses SWITCHES = new ClassFileImporter().importClasses(SwitchesOnKinds.class);

    @Test
    void aSwitchOnAKindOfContentIsCaught() {
        assertTrue(ContentArchitectureTest.noneAsksWhichContentItIs
                .evaluate(SWITCHES)
                .hasViolation());
    }

    @Test
    void aSwitchOnAKindOfBodyIsCaught() {
        assertTrue(
                ContentArchitectureTest.noneAsksWhichBodyItIs.evaluate(SWITCHES).hasViolation());
    }
}
