package ca.gc.aafc.dina.export.api.output;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TabularOutputIT {

  @Test
  void tabularOutput_onTabSeparator_rightContentWritten() throws IOException {

    Path tmpFile = Files.createTempFile(null, null);
    try (Writer w = new FileWriter(tmpFile.toFile(),
      StandardCharsets.UTF_8);
         TabularOutput<Integer, JsonNode> output =
           TabularOutput.create(TabularOutput.TabularOutputArgs.builder()
             .headers(List.of("col1", "col2"))
             .columnSeparator(TabularOutput.ColumnSeparator.TAB).build(), new TypeReference<>() {
           }, w)) {

      ObjectMapper om = new ObjectMapper();
      ObjectNode jNode = om.createObjectNode();
      jNode.put("col1", "a");
      jNode.put("col2", "b");
      output.addRecord(1, jNode);
      jNode = om.createObjectNode();
      jNode.put("col1", "y");
      jNode.put("col2", "z");
      output.addRecord(2, jNode);
    }
    List<String> fileContent = Files.readAllLines(tmpFile);
    assertTrue(fileContent.getFirst().startsWith("col1"));
    assertTrue(fileContent.get(1).startsWith("a\t"));
  }


  @Test
  void tabularOutput_onHeaderAliases_rightContentWritten() throws IOException {

    Path tmpFile = Files.createTempFile(null, null);
    try (Writer w = new FileWriter(tmpFile.toFile(),
         StandardCharsets.UTF_8);
         TabularOutput<Integer, JsonNode> output =
           TabularOutput.create(TabularOutput.TabularOutputArgs.builder()
             .headers(List.of("col1", "col2"))
             .receivedHeadersAliases(List.of("c1", "c2")).build(), new TypeReference<>() {
           }, w)) {

      ObjectMapper om = new ObjectMapper();
      ObjectNode jNode = om.createObjectNode();
      jNode.put("col1", "a");
      jNode.put("col2", "b");
      output.addRecord(1, jNode);
      jNode = om.createObjectNode();
      jNode.put("col1", "y");
      jNode.put("col2", "z");
      output.addRecord(2, jNode);
    }
    List<String> fileContent = Files.readAllLines(tmpFile);
    assertTrue(fileContent.getFirst().startsWith("c1"));
  }

  @Test
  void tabularOutput_onHeaderPartialAliases_rightContentWritten() throws IOException {

    Path tmpFile = Files.createTempFile(null, null);
    try (Writer w = new FileWriter(tmpFile.toFile(),
      StandardCharsets.UTF_8);
         TabularOutput<Integer, JsonNode> output =
           TabularOutput.create(TabularOutput.TabularOutputArgs.builder()
             .headers(List.of("col1", "col2"))
             .receivedHeadersAliases(List.of("", "c2")).build(), new TypeReference<>() {
           }, w)) {

      ObjectMapper om = new ObjectMapper();
      ObjectNode jNode = om.createObjectNode();
      jNode.put("col1", "a");
      jNode.put("col2", "b");
      output.addRecord(1, jNode);
      jNode = om.createObjectNode();
      jNode.put("col1", "y");
      jNode.put("col2", "z");
      output.addRecord(2, jNode);
    }
    List<String> fileContent = Files.readAllLines(tmpFile);
    //the real name should be used since the alias is empty
    assertTrue(fileContent.getFirst().startsWith("col1"));
  }

  @Test
  void tabularOutput_withIdTracking_skipsDuplicates() throws IOException {
    Path tmpFile = Files.createTempFile(null, null);
    try (Writer w = new FileWriter(tmpFile.toFile(), StandardCharsets.UTF_8);
         TabularOutput<UUID, JsonNode> output =
           TabularOutput.create(TabularOutput.TabularOutputArgs.builder()
             .headers(List.of("id", "name"))
             .enableIdTracking(true)
             .build(), new TypeReference<>() {
           }, w)) {

      ObjectMapper om = new ObjectMapper();
      
      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      UUID id3 = UUID.randomUUID();
      
      // Add first record with id1
      ObjectNode record1 = om.createObjectNode();
      record1.put("id", id1.toString());
      record1.put("name", "First");
      output.addRecord(id1, record1);
      
      // Add second record with id2
      ObjectNode record2 = om.createObjectNode();
      record2.put("id", id2.toString());
      record2.put("name", "Second");
      output.addRecord(id2, record2);
      
      // Try to add duplicate with id1 - should be skipped
      ObjectNode duplicate = om.createObjectNode();
      duplicate.put("id", id1.toString());
      duplicate.put("name", "Duplicate");
      output.addRecord(id1, duplicate);
      
      // Add third unique record with id3
      ObjectNode record3 = om.createObjectNode();
      record3.put("id", id3.toString());
      record3.put("name", "Third");
      output.addRecord(id3, record3);
    }
    
    List<String> fileContent = Files.readAllLines(tmpFile);
    // Should have header + 3 records (duplicate was skipped)
    assertEquals(4, fileContent.size(), "Should have header + 3 unique records");
    assertTrue(fileContent.get(0).contains("id"));
    assertTrue(fileContent.get(1).contains("First"));
    assertTrue(fileContent.get(2).contains("Second"));
    assertTrue(fileContent.get(3).contains("Third"));
  }
}
