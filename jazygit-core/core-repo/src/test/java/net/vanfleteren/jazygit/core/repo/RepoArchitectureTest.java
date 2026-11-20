package net.vanfleteren.jazygit.core.repo;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.CoreRules;
import net.vanfleteren.jazygit.ModelRules;

/**
 * The rules of core-repo: those of the whole core and of the models. It is made of values, which git and gh both use.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.core.repo", importOptions = ImportOption.DoNotIncludeTests.class)
class RepoArchitectureTest {

    @ArchTest
    static final ArchTests coreRules = ArchTests.in(CoreRules.class);

    @ArchTest
    static final ArchTests modelRules = ArchTests.in(ModelRules.class);
}
