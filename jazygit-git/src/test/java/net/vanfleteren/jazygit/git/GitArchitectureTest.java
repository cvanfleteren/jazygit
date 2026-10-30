package net.vanfleteren.jazygit.git;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.CoreRules;
import net.vanfleteren.jazygit.ModelRules;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Revision;

/**
 * The rules of jazygit-git: those of the whole core and of the models, and its own.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.git", importOptions = ImportOption.DoNotIncludeTests.class)
class GitArchitectureTest {

    @ArchTest
    static final ArchTests coreRules = ArchTests.in(CoreRules.class);

    @ArchTest
    static final ArchTests modelRules = ArchTests.in(ModelRules.class);

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
}
