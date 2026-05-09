package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class WeightInputParserTest {

  @Test
  void parsesNoneAndBlankAsNoneMode() {
    WeightInputParser.WeightPrefill blank = WeightInputParser.parseWeightPrefill("   ");
    WeightInputParser.WeightPrefill none = WeightInputParser.parseWeightPrefill("none");

    assertEquals("none", blank.mode());
    assertEquals("none", none.mode());
  }

  @Test
  void parsesSimpleWeightAndNormalizesUnit() {
    WeightInputParser.WeightPrefill parsed = WeightInputParser.parseWeightPrefill("225 LB");

    assertEquals("weight", parsed.mode());
    assertEquals("225", parsed.weightValue());
    assertEquals("lb", parsed.weightUnit());
    assertEquals("225 LB", parsed.customWeight());
  }

  @Test
  void parsesLeftRightWeightAndFallsBackToDefaultUnitWhenMissing() {
    WeightInputParser.WeightPrefill parsed = WeightInputParser.parseWeightPrefill("45|50");

    assertEquals("lr", parsed.mode());
    assertEquals("45", parsed.leftValue());
    assertEquals("50", parsed.rightValue());
    assertEquals("lb", parsed.lrUnit());
  }

  @Test
  void parsesAccommodatingChainsFromBarAndChainWeight() {
    WeightInputParser.WeightPrefill parsed = WeightInputParser.parseWeightPrefill("185 kg+40c");

    assertEquals("accom", parsed.mode());
    assertEquals("185", parsed.accomBar());
    assertEquals("kg", parsed.accomUnit());
    assertEquals("chains", parsed.accomMode());
    assertEquals("40", parsed.accomChain());
  }

  @Test
  void parsesBandsOnlyAndNormalizesCase() {
    WeightInputParser.WeightPrefill parsed = WeightInputParser.parseWeightPrefill("Red+BLUE");

    assertEquals("bands", parsed.mode());
    assertEquals(List.of("red", "blue"), parsed.bands());
  }

  @Test
  void parsesAccommodatingBandsAndRejectsUnknownBandNames() {
    WeightInputParser.WeightPrefill valid = WeightInputParser.parseWeightPrefill("225 lb+red+blue");
    WeightInputParser.WeightPrefill invalid =
        WeightInputParser.parseWeightPrefill("225 lb+red+chartreuse");

    assertEquals("accom", valid.mode());
    assertEquals("bands", valid.accomMode());
    assertEquals(List.of("red", "blue"), valid.accomBands());

    assertEquals("custom", invalid.mode());
    assertEquals("225 lb+red+chartreuse", invalid.customWeight());
  }

  @Test
  void unknownValuesFallBackToCustomMode() {
    WeightInputParser.WeightPrefill parsed = WeightInputParser.parseWeightPrefill("mystery load");

    assertEquals("custom", parsed.mode());
    assertEquals("mystery load", parsed.customWeight());
  }
}
