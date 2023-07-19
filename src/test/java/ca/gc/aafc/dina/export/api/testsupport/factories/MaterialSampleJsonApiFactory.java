package ca.gc.aafc.dina.export.api.testsupport.factories;

import java.util.Map;
import java.util.UUID;

import ca.gc.aafc.dina.testsupport.factories.TestableEntityFactory;

/**
 * Mimic a material sample in JSON:API, as map
 */
public class MaterialSampleJsonApiFactory {

  /**
   * Starts at the attributes level.
   * @return
   */
  public static Map<String, Object> newMaterialSample() {
    return Map.of("attributes",
        Map.of("uuid", UUID.randomUUID(),
          "materialSampleName", TestableEntityFactory.generateRandomNameLettersOnly(12)));
  }
}
