package ca.gc.aafc.dina.export.api.output;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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

  @TempDir
  Path tempDir;


  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void compositeDataOutput_withMultipleEntityTypes_createsCorrectFiles() throws IOException {

    TabularOutput.TabularOutputArgs sampleArgs = TabularOutput.TabularOutputArgs.builder()
      .headers(List.of("id", "name", "type"))
      .columnSeparator(TabularOutput.ColumnSeparator.COMMA).build();

    TabularOutput.TabularOutputArgs projectArgs = TabularOutput.TabularOutputArgs.builder()
      .headers(List.of("id", "title", "description"))
      .columnSeparator(TabularOutput.ColumnSeparator.COMMA).build();

    // When: Creating composite output and adding records
    try (
      Writer sampleWriter = new FileWriter(tempDir.resolve("samples.csv").toFile(), StandardCharsets.UTF_8);
      Writer projectWriter = new FileWriter(tempDir.resolve("projects.csv").toFile(), StandardCharsets.UTF_8);

      TabularOutput<Object, JsonNode> sampleOutput = TabularOutput.create(sampleArgs, new TypeReference<>() {}, sampleWriter);
      TabularOutput<Object, JsonNode> projectOutput = TabularOutput.create(projectArgs, new TypeReference<>() {}, projectWriter);

      CompositeDataOutput<Integer, JsonNode> output = new CompositeDataOutput<>(
        Map.of("sample", sampleOutput, "project", projectOutput))) {
      
      // Add sample records
      ObjectNode sample1 = objectMapper.createObjectNode();
      sample1.put("id", "S001");
      sample1.put("name", "Sample 1");
      sample1.put("type", "soil");
      output.addRecord("sample", 1, sample1);
      
      ObjectNode sample2 = objectMapper.createObjectNode();
      sample2.put("id", "S002");
      sample2.put("name", "Sample 2");
      sample2.put("type", "water");
      output.addRecord("sample", 2, sample2);
      
      // Add project records
      ObjectNode project1 = objectMapper.createObjectNode();
      project1.put("id", "P001");
      project1.put("title", "Project Alpha");
      project1.put("description", "First project");
      output.addRecord("project", 1, project1);
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
  void compositeDataOutput_withAliases_usesAliasesInHeaders() throws IOException {
    // Given: Configuration with column aliases
    TabularOutput.TabularOutputArgs sampleArgs = TabularOutput.TabularOutputArgs.builder()
      .headers(List.of("id", "name"))
      .receivedHeadersAliases(List.of("Sample ID", "Sample Name"))
      .columnSeparator(TabularOutput.ColumnSeparator.COMMA).build();

    // When: Creating composite output and adding records
    try (
      Writer sampleWriter = new FileWriter(tempDir.resolve("samples.csv").toFile(), StandardCharsets.UTF_8);
      TabularOutput<Object, JsonNode> sampleOutput = TabularOutput.create(sampleArgs, new TypeReference<>() {}, sampleWriter);
      CompositeDataOutput<Integer, JsonNode> output = new CompositeDataOutput<>(
        Map.of("sample", sampleOutput))) {

      ObjectNode sample = objectMapper.createObjectNode();
      sample.put("id", "S001");
      sample.put("name", "Test Sample");
      output.addRecord("sample", 1, sample);
    }

    // Then: Verify aliases are used in header
    List<String> content = Files.readAllLines(tempDir.resolve("samples.csv"));
    assertTrue(content.getFirst().contains("Sample ID"));
    assertTrue(content.getFirst().contains("Sample Name"));
  }

 @Test
 void compositeDataOutput_withTabSeparator_usesTabSeparator() throws IOException {
    // Given: Configuration with column aliases
    TabularOutput.TabularOutputArgs sampleArgs = TabularOutput.TabularOutputArgs.builder()
      .headers(List.of("id", "name"))
      .receivedHeadersAliases(List.of("Sample ID", "Sample Name"))
      .columnSeparator(TabularOutput.ColumnSeparator.TAB).build();

    // When: Creating composite output and adding records
    try (
      Writer sampleWriter = new FileWriter(tempDir.resolve("samples.tsv").toFile(), StandardCharsets.UTF_8);
      TabularOutput<Object, JsonNode> sampleOutput = TabularOutput.create(sampleArgs, new TypeReference<>() {}, sampleWriter);
      CompositeDataOutput<Integer, JsonNode> output = new CompositeDataOutput<>(
        Map.of("sample", sampleOutput))) {

      ObjectNode sample = objectMapper.createObjectNode();
      sample.put("id", "S001");
      sample.put("name", "Test Sample");
      output.addRecord("sample", 1, sample);
    }

    // Then: Verify tab separator is used
    List<String> content = Files.readAllLines(tempDir.resolve("samples.tsv"));
    assertTrue(content.get(1).contains("\t"), "Should use tab separator");
  }

  @Test
  void compositeDataOutput_withUnknownType_throwsException() throws IOException {
    // Given: Configuration for only one entity type
    TabularOutput.TabularOutputArgs sampleArgs = TabularOutput.TabularOutputArgs.builder()
      .headers(List.of("id", "name"))
      .columnSeparator(TabularOutput.ColumnSeparator.COMMA).build();

    // When: Adding record with unknown type
    try (
      Writer sampleWriter = new FileWriter(tempDir.resolve("samples.csv").toFile(), StandardCharsets.UTF_8);
      TabularOutput<Object, JsonNode> sampleOutput = TabularOutput.create(sampleArgs, new TypeReference<>() {}, sampleWriter);
      CompositeDataOutput<Integer, JsonNode> output = new CompositeDataOutput<>(
        Map.of("sample", sampleOutput))) {

      ObjectNode record = objectMapper.createObjectNode();
      record.put("id", "X001");

      // Then: Should throw exception for unknown type
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
        output.addRecord("unknownType", 1, record);
      });

      assertTrue(exception.getMessage().contains("No output configured for entity type: unknownType"));
    }
  }

  @Test
  void compositeDataOutput_addRecordWithoutType_throwsException() throws IOException {
    // Given: Composite output configuration
    TabularOutput.TabularOutputArgs sampleArgs = TabularOutput.TabularOutputArgs.builder()
      .headers(List.of("id", "name"))
      .columnSeparator(TabularOutput.ColumnSeparator.COMMA).build();

    // When: Adding record without specifying type
    try (
      Writer sampleWriter = new FileWriter(tempDir.resolve("samples.csv").toFile(), StandardCharsets.UTF_8);
      TabularOutput<Object, JsonNode> sampleOutput = TabularOutput.create(sampleArgs, new TypeReference<>() {}, sampleWriter);
      CompositeDataOutput<Integer, JsonNode> output = new CompositeDataOutput<>(
        Map.of("sample", sampleOutput))) {

      ObjectNode record = objectMapper.createObjectNode();
      record.put("id", "S001");

      // Then: Should throw exception
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
        output.addRecord(1, record);
      });

      assertTrue(exception.getMessage().contains("type required for CompositeDataOutput"));
    }
  }

  @Test
  void compositeDataOutput_withMultipleRecordsSameType_appendsToSameFile() throws IOException {
    // Given: Configuration for one entity type
    TabularOutput.TabularOutputArgs sampleArgs = TabularOutput.TabularOutputArgs.builder()
      .headers(List.of("id", "name"))
      .columnSeparator(TabularOutput.ColumnSeparator.COMMA).build();

    // When: Adding multiple records of the same type
    try (
      Writer sampleWriter = new FileWriter(tempDir.resolve("samples.csv").toFile(), StandardCharsets.UTF_8);
      TabularOutput<Object, JsonNode> sampleOutput = TabularOutput.create(sampleArgs, new TypeReference<>() {}, sampleWriter);
      CompositeDataOutput<Integer, JsonNode> output = new CompositeDataOutput<>(
        Map.of("sample", sampleOutput))) {

      for (int i = 1; i <= 5; i++) {
        ObjectNode sample = objectMapper.createObjectNode();
        sample.put("id", "S00" + i);
        sample.put("name", "Sample " + i);
        output.addRecord("sample", 1, sample);
      }
    }

    // Then: All records should be in the same file
    List<String> content = Files.readAllLines(tempDir.resolve("samples.csv"));
    assertEquals(6, content.size()); // header + 5 records
  }

  @Test
  void compositeDataOutput_withEmptyConfiguration_createsNoFiles() throws IOException {
    // Given: Empty configuration
    Map<String, TabularOutput<Object, JsonNode>> emptyOutputs = new LinkedHashMap<>();

    // When: Creating composite output with no entity types
    try (CompositeDataOutput<Integer, JsonNode> output = new CompositeDataOutput<>(emptyOutputs)) {
      // Do nothing
    }

    // Then: No files should be created
    assertEquals(0, Files.list(tempDir).count(), "No files should be created with empty configuration");
  }

}
