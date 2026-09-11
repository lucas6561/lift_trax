package com.lifttrax.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PackageDependencyTest {
  private static JavaClasses production;

  @BeforeAll
  static void importProductionClasses() {
    // Main sources are also compiled into test output; scan only Gradle's main output.
    String paths = System.getProperty("lifttrax.mainClasses", "build/classes/java/main");
    production =
        new ClassFileImporter()
            .importPaths(
                Arrays.stream(paths.split(Pattern.quote(File.pathSeparator)))
                    .map(Path::of)
                    .toList());
    assertFalse(
        production.isEmpty(), "Architecture checks must inspect compiled production classes");
    assertFalse(
        production.contain(PackageDependencyTest.class), "Test helpers must not be scanned");
  }

  @Test
  void modelsRemainIndependentOfApplicationLayers() {
    noClasses()
        .that()
        .resideInAPackage("com.lifttrax.models..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.lifttrax.cli..",
            "com.lifttrax.db..",
            "com.lifttrax.workout..",
            "com.lifttrax.config..",
            "java.sql..",
            "javax.sql..",
            "com.sun.net.httpserver..")
        .because(
            "training values must remain independent of delivery, persistence, and configuration")
        .check(production);
  }

  @Test
  void workoutLogicDoesNotDependOnWebOrSql() {
    noClasses()
        .that()
        .resideInAPackage("com.lifttrax.workout..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.lifttrax.cli..", "java.sql..", "javax.sql..", "com.sun.net.httpserver..")
        .because("workout generation and schemas must be usable without HTTP or SQL")
        .check(production);
  }

  @Test
  void workoutLogicUsesOnlyDatastoreContracts() {
    noClasses()
        .that()
        .resideInAPackage("com.lifttrax.workout..")
        .should()
        .dependOnClassesThat(
            describe(
                "belong to persistence outside the Database and TrainingDataStore contracts",
                target ->
                    target.getPackageName().startsWith("com.lifttrax.db")
                        && !target.getName().equals("com.lifttrax.db.Database")
                        && !target.getName().equals("com.lifttrax.db.TrainingDataStore")))
        .because("training logic must not be coupled to a concrete datastore")
        .check(production);
  }

  @Test
  void schemaContractsDoNotDependOnPersistence() {
    noClasses()
        .that()
        .resideInAPackage("com.lifttrax.workout..")
        .and()
        .haveNameMatching(".*\\.(ProgramSchema.*|PlannedWorkout(File|Json|SchemaVersions).*)")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.lifttrax.db..")
        .because("program and planned-workout file contracts must validate without a datastore")
        .check(production);
  }

  @Test
  void persistenceDoesNotDependOnDeliveryOrWorkoutLogic() {
    noClasses()
        .that()
        .resideInAPackage("com.lifttrax.db..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.lifttrax.cli..", "com.lifttrax.workout..", "com.sun.net.httpserver..")
        .because("datastores map models without owning routes or training behavior")
        .check(production);
  }

  @Test
  void htmlRenderersDoNotIssueSql() {
    noClasses()
        .that()
        .resideInAPackage("com.lifttrax.cli..")
        .and()
        .haveNameMatching(".*Html(?:\\$.*)?")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("java.sql..", "javax.sql..", "org.postgresql..", "org.sqlite..")
        .because("page rendering consumes models and datastore contracts rather than issuing SQL")
        .check(production);
  }

  @Test
  void configurationDoesNotOwnProductBehavior() {
    noClasses()
        .that()
        .resideInAPackage("com.lifttrax.config..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.lifttrax.models..",
            "com.lifttrax.cli..",
            "com.lifttrax.db..",
            "com.lifttrax.workout..")
        .because("configuration resolves settings independently of product behavior")
        .check(production);
  }
}
