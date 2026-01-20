package ca.gc.aafc.dina.export.api.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link CompositeDataOutput}.
 * Tests the routing of records to multiple TabularOutput instances based on entity type.
 */
public class CompositeDataOutputIT {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void compositeDataOutput_withMultipleEntityTypes_createsCorrectFiles(@TempDir Path tempDir) throws IOException {
    // Given: Configuration for multiple entity types
    Map<String, CompositeDataOutput.EntityConfig> entityConfigs = new LinkedHashMap<>();
    
    entityConfigs.put("samples", CompositeDataOutput.EntityConfig.builder()
        .filename("samples.csv")
        .columns(List.of("id", "name", "type"))
        .separator(TabularOutput.ColumnSeparator.COMMA)
        .build());
    
    entityConfigs.put("projects", CompositeDataOutput.EntityConfig.builder()
        .filename("projects.csv")
        .columns(List.of("id", "title", "description"))
        .separator(TabularOutput.ColumnSeparator.COMMA)
        .build());

    // When: Creating composite output and adding records
    try (CompositeDataOutput<JsonNode> output = new CompositeDataOutput<>(
        entityConfigs, new TypeReference<>() {}, tempDir)) {
      
      // Add sample records
      ObjectNode sample1 = objectMapper.createObjectNode();
      sample1.put("id", "S001");
      sample1.put("name", "Sample 1");
      sample1.put("type", "soil");
      output.addRecord("samples", sample1);
      
      ObjectNode sample2 = objectMapper.createObjectNode();
      sample2.put("id", "S002");
      sample2.put("name", "Sample 2");
      sample2.put("type", "water");
      output.addRecord("samples", sample2);
      
      // Add project records
      ObjectNode project1 = objectMapper.createObjectNode();
      project1.put("id", "P001");
      project1.put("title", "Project Alpha");
      project1.put("description", "First project");
      output.addRecord("projects", project1);
    }

    // Then: Verify files are created with correct content
    Path samplesFile = tempDir.resolve("samples.csv");
    Path projectsFile = tempDir.resolve("projects.csv");
    
    assertTrue(Files.exists(samplesFile), "samples.csv should exist");
    assertTrue(Files.exists(projectsFile), "projects.csv should exist");
    
    List<String> samplesContent = Files.readAllLines(samplesFile);
    assertEquals(3, samplesContent.size()); // header + 2 records
    assertTrue(samplesContent.get(0).contains("id"));
    assertTrue(samplesContent.get(1).contains("S001"));
    assertTrue(samplesContent.get(2).contains("S002"));
    
    List<String> projectsContent = Files.readAllLines(projectsFile);
    assertEquals(2, projectsContent.size()); // header + 1 record
    assertTrue(projectsContent.get(0).contains("id"));
    assertTrue(projectsContent.get(1).contains("P001"));
  }

  @Test
  void compositeDataOutput_withAliases_usesAliasesInHeaders(@TempDir Path tempDir) throws IOException {
    // Given: Configuration with column aliases
    Map<String, CompositeDataOutput.EntityConfig> entityConfigs = new LinkedHashMap<>();
    
    entityConfigs.put("samples", CompositeDataOutput.EntityConfig.builder()
        .filename("samples.csv")
        .columns(List.of("id", "name"))
        .aliases(List.of("Sample ID", "Sample Name"))
        .separator(TabularOutput.ColumnSeparator.COMMA)
        .build());

    // When: Creating composite output and adding records
    try (CompositeDataOutput<JsonNode> output = new CompositeDataOutput<>(
        entityConfigs, new TypeReference<>() {}, tempDir)) {
      
      ObjectNode sample = objectMapper.createObjectNode();
      sample.put("id", "S001");
      sample.put("name", "Test Sample");
      output.addRecord("samples", sample);
    }

    // Then: Verify aliases are used in header
    List<String> content = Files.readAllLines(tempDir.resolve("samples.csv"));
    assertTrue(content.get(0).contains("Sample ID"));
    assertTrue(content.get(0).contains("Sample Name"));
  }

  @Test
  void compositeDataOutput_withTabSeparator_usesTabSeparator(@TempDir Path tempDir) throws IOException {
    // Given: Configuration with TAB separator
    Map<String, CompositeDataOutput.EntityConfig> entityConfigs = new LinkedHashMap<>();
    
    entityConfigs.put("samples", CompositeDataOutput.EntityConfig.builder()
        .filename("samples.tsv")
        .columns(List.of("id", "name"))
        .separator(TabularOutput.ColumnSeparator.TAB)
        .build());

    // When: Creating composite output and adding records
    try (CompositeDataOutput<JsonNode> output = new CompositeDataOutput<>(
        entityConfigs, new TypeReference<>() {}, tempDir)) {
      
      ObjectNode sample = objectMapper.createObjectNode();
      sample.put("id", "S001");
      sample.put("name", "Test Sample");
      output.addRecord("samples", sample);
    }

    // Then: Verify tab separator is used
    List<String> content = Files.readAllLines(tempDir.resolve("samples.tsv"));
    assertTrue(content.get(1).contains("\t"), "Should use tab separator");
  }

  @Test
  void compositeDataOutput_withDefaultFilename_usesEntityTypeAsFilename(@TempDir Path tempDir) throws IOException {
    // Given: Configuration without explicit filename
    Map<String, CompositeDataOutput.EntityConfig> entityConfigs = new LinkedHashMap<>();
    
    entityConfigs.put("organisms", CompositeDataOutput.EntityConfig.builder()
        .columns(List.of("id", "scientificName"))
        .separator(TabularOutput.ColumnSeparator.COMMA)
        .build());

    // When: Creating composite output and adding records
    try (CompositeDataOutput<JsonNode> output = new CompositeDataOutput<>(
        entityConfigs, new TypeReference<>() {}, tempDir)) {
      
      ObjectNode organism = objectMapper.createObjectNode();
      organism.put("id", "O001");
      organism.put("scientificName", "Homo sapiens");
      output.addRecord("organisms", organism);
    }

    // Then: Verify default filename is used
    Path defaultFile = tempDir.resolve("organisms.csv");
    assertTrue(Files.exists(defaultFile), "Should use entity type as filename");
  }

  @Test
  void compositeDataOutput_withUnknownType_throwsException(@TempDir Path tempDir) throws IOException {
    // Given: Configuration for only one entity type
    Map<String, CompositeDataOutput.EntityConfig> entityConfigs = new LinkedHashMap<>();
    
    entityConfigs.put("samples", CompositeDataOutput.EntityConfig.builder()
        .columns(List.of("id", "name"))
        .separator(TabularOutput.ColumnSeparator.COMMA)
        .build());

    // When: Adding record with unknown type
    try (CompositeDataOutput<JsonNode> output = new CompositeDataOutput<>(
        entityConfigs, new TypeReference<>() {}, tempDir)) {
      
      ObjectNode record = objectMapper.createObjectNode();
      record.put("id", "X001");
      
      // Then: Should throw exception for unknown type
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
        output.addRecord("unknownType", record);
      });
      
      assertTrue(exception.getMessage().contains("No output configured for entity type: unknownType"));
    }
  }

  @Test
  void compositeDataOutput_addRecordWithoutType_throwsException(@TempDir Path tempDir) throws IOException {
    // Given: Composite output configuration
    Map<String, CompositeDataOutput.EntityConfig> entityConfigs = new LinkedHashMap<>();
    
    entityConfigs.put("samples", CompositeDataOutput.EntityConfig.builder()
        .columns(List.of("id", "name"))
        .separator(TabularOutput.ColumnSeparator.COMMA)
        .build());

    // When: Adding record without specifying type
    try (CompositeDataOutput<JsonNode> output = new CompositeDataOutput<>(
        entityConfigs, new TypeReference<>() {}, tempDir)) {
      
      ObjectNode record = objectMapper.createObjectNode();
      record.put("id", "S001");
      
      // Then: Should throw exception
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
        output.addRecord(record);
      });
      
      assertTrue(exception.getMessage().contains("type required for CompositeDataOutput"));
    }
  }

  @Test
  void compositeDataOutput_withMultipleRecordsSameType_appendsToSameFile(@TempDir Path tempDir) throws IOException {
    // Given: Configuration for one entity type
    Map<String, CompositeDataOutput.EntityConfig> entityConfigs = new LinkedHashMap<>();
    
    entityConfigs.put("samples", CompositeDataOutput.EntityConfig.builder()
        .filename("samples.csv")
        .columns(List.of("id", "name"))
        .separator(TabularOutput.ColumnSeparator.COMMA)
        .build());

    // When: Adding multiple records of the same type
    try (CompositeDataOutput<JsonNode> output = new CompositeDataOutput<>(
        entityConfigs, new TypeReference<>() {}, tempDir)) {
      
      for (int i = 1; i <= 5; i++) {
        ObjectNode sample = objectMapper.createObjectNode();
        sample.put("id", "S00" + i);
        sample.put("name", "Sample " + i);
        output.addRecord("samples", sample);
      }
    }

    // Then: All records should be in the same file
    List<String> content = Files.readAllLines(tempDir.resolve("samples.csv"));
    assertEquals(6, content.size()); // header + 5 records
  }

  @Test
  void compositeDataOutput_withEmptyConfiguration_createsNoFiles(@TempDir Path tempDir) throws IOException {
    // Given: Empty configuration
    Map<String, CompositeDataOutput.EntityConfig> entityConfigs = new LinkedHashMap<>();

    // When: Creating composite output with no entity types
    try (CompositeDataOutput<JsonNode> output = new CompositeDataOutput<>(
        entityConfigs, new TypeReference<>() {}, tempDir)) {
      // Do nothing
    }

    // Then: No files should be created
    assertEquals(0, Files.list(tempDir).count(), "No files should be created with empty configuration");
  }

  @Test
  void compositeDataOutput_closeMultipleTimes_handlesGracefully(@TempDir Path tempDir) throws IOException {
    // Given: Configuration for one entity type
    Map<String, CompositeDataOutput.EntityConfig> entityConfigs = new LinkedHashMap<>();
    
    entityConfigs.put("samples", CompositeDataOutput.EntityConfig.builder()
        .columns(List.of("id"))
        .separator(TabularOutput.ColumnSeparator.COMMA)
        .build());

    // When: Closing output multiple times
    CompositeDataOutput<JsonNode> output = new CompositeDataOutput<>(
        entityConfigs, new TypeReference<>() {}, tempDir);
    
    ObjectNode sample = objectMapper.createObjectNode();
    sample.put("id", "S001");
    output.addRecord("samples", sample);
    
    // Then: Should handle multiple closes gracefully
    output.close();
    output.close(); // Should not throw exception
    
    assertTrue(Files.exists(tempDir.resolve("samples.csv")));
  }
}
