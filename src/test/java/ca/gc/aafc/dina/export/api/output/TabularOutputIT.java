package ca.gc.aafc.dina.export.api.output;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TabularOutputIT {

  @Test
  void tabularOutput_onTabSeparator_rightContentWritten() throws IOException {

    Path tmpFile = Files.createTempFile(null, null);
    try (Writer w = new FileWriter(tmpFile.toFile(),
      StandardCharsets.UTF_8);
         TabularOutput<JsonNode> output =
           TabularOutput.create(TabularOutput.TabularOutputArgs.builder()
             .headers(List.of("col1", "col2"))
             .columnSeparator(TabularOutput.ColumnSeparator.TAB).build(), new TypeReference<>() {
           }, w)) {

      ObjectMapper om = new ObjectMapper();
      ObjectNode jNode = om.createObjectNode();
      jNode.put("col1", "a");
      jNode.put("col2", "b");
      output.addRecord(jNode);
      jNode = om.createObjectNode();
      jNode.put("col1", "y");
      jNode.put("col2", "z");
      output.addRecord(jNode);
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
         TabularOutput<JsonNode> output =
           TabularOutput.create(TabularOutput.TabularOutputArgs.builder()
             .headers(List.of("col1", "col2"))
             .receivedHeadersAliases(List.of("c1", "c2")).build(), new TypeReference<>() {
           }, w)) {

      ObjectMapper om = new ObjectMapper();
      ObjectNode jNode = om.createObjectNode();
      jNode.put("col1", "a");
      jNode.put("col2", "b");
      output.addRecord(jNode);
      jNode = om.createObjectNode();
      jNode.put("col1", "y");
      jNode.put("col2", "z");
      output.addRecord(jNode);
    }
    List<String> fileContent = Files.readAllLines(tmpFile);
    assertTrue(fileContent.getFirst().startsWith("c1"));
  }

  @Test
  void tabularOutput_onHeaderPartialAliases_rightContentWritten() throws IOException {

    Path tmpFile = Files.createTempFile(null, null);
    try (Writer w = new FileWriter(tmpFile.toFile(),
      StandardCharsets.UTF_8);
         TabularOutput<JsonNode> output =
           TabularOutput.create(TabularOutput.TabularOutputArgs.builder()
             .headers(List.of("col1", "col2"))
             .receivedHeadersAliases(List.of("", "c2")).build(), new TypeReference<>() {
           }, w)) {

      ObjectMapper om = new ObjectMapper();
      ObjectNode jNode = om.createObjectNode();
      jNode.put("col1", "a");
      jNode.put("col2", "b");
      output.addRecord(jNode);
      jNode = om.createObjectNode();
      jNode.put("col1", "y");
      jNode.put("col2", "z");
      output.addRecord(jNode);
    }
    List<String> fileContent = Files.readAllLines(tmpFile);
    //the real name should be used since the alias is empty
    assertTrue(fileContent.getFirst().startsWith("col1"));
  }
}
