package com.lifttrax.quality;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.reporting.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSizePolicyTest {
  private static final Path PRODUCTION_RULESET =
      Path.of("config", "pmd", "ruleset.xml").toAbsolutePath();
  private static final Path TEST_RULESET =
      Path.of("config", "pmd", "ruleset-test.xml").toAbsolutePath();

  @TempDir Path directory;

  @Test
  void productionClassAtPhysicalLimitPassesAndOneLineOverFails() throws IOException {
    assertFalse(
        violations(PRODUCTION_RULESET, "ProductionClassAtLimit", classSource(675))
            .contains("ExcessivePhysicalClassLength"));
    assertTrue(
        violations(PRODUCTION_RULESET, "ProductionClassOverLimit", classSource(676))
            .contains("ExcessivePhysicalClassLength"));
  }

  @Test
  void productionMethodAtPhysicalLimitPassesAndOneLineOverFails() throws IOException {
    assertFalse(
        violations(PRODUCTION_RULESET, "ProductionMethodAtLimit", methodSource(85))
            .contains("ExcessivePhysicalMethodLength"));
    assertTrue(
        violations(PRODUCTION_RULESET, "ProductionMethodOverLimit", methodSource(86))
            .contains("ExcessivePhysicalMethodLength"));
  }

  @Test
  void testClassAtPhysicalLimitPassesAndOneLineOverFails() throws IOException {
    assertFalse(
        violations(TEST_RULESET, "TestClassAtLimit", classSource(950))
            .contains("ExcessivePhysicalTestClassLength"));
    assertTrue(
        violations(TEST_RULESET, "TestClassOverLimit", classSource(951))
            .contains("ExcessivePhysicalTestClassLength"));
  }

  @Test
  void testMethodAtPhysicalLimitPassesAndOneLineOverFails() throws IOException {
    assertFalse(
        violations(TEST_RULESET, "TestMethodAtLimit", methodSource(90))
            .contains("ExcessivePhysicalTestMethodLength"));
    assertTrue(
        violations(TEST_RULESET, "TestMethodOverLimit", methodSource(91))
            .contains("ExcessivePhysicalTestMethodLength"));
  }

  private Set<String> violations(Path ruleset, String className, String source) throws IOException {
    Path sourceFile = directory.resolve(className + ".java");
    Files.writeString(sourceFile, source.replace("Probe", className));

    PMDConfiguration configuration = new PMDConfiguration();
    configuration.setDefaultLanguageVersion(
        LanguageRegistry.PMD.getLanguageById("java").getVersion("17"));
    configuration.addInputPath(sourceFile);
    configuration.addRuleSet(ruleset.toString());

    try (PmdAnalysis analysis = PmdAnalysis.create(configuration)) {
      Report report = analysis.performAnalysisAndCollectReport();
      assertTrue(
          report.getConfigurationErrors().isEmpty(), report.getConfigurationErrors().toString());
      assertTrue(report.getProcessingErrors().isEmpty(), report.getProcessingErrors().toString());
      return report.getViolations().stream()
          .map(violation -> violation.getRule().getName())
          .collect(Collectors.toSet());
    }
  }

  private static String classSource(int lines) {
    StringBuilder source = new StringBuilder("final class Probe {\n");
    source.append("  // boundary filler\n".repeat(lines - 2));
    return source.append("}\n").toString();
  }

  private static String methodSource(int methodLines) {
    StringBuilder source = new StringBuilder("final class Probe {\n  void boundary() {\n");
    source.append("    // boundary filler\n".repeat(methodLines - 2));
    return source.append("  }\n}\n").toString();
  }
}
