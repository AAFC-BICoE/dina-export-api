package ca.gc.aafc.dina.export.api.output;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.Builder;
import lombok.Getter;

/**
 * Composite DataOutput that routes records to different TabularOutput instances based on entity type.
 * Implements the Composite pattern for DataOutput, delegating to multiple TabularOutput instances.
 * 
 * @param <T> the record type
 */
public class CompositeDataOutput<T> implements DataOutput<T> {

  private final Map<String, TabularOutput<T>> outputsByType;
  private final Map<String, Writer> writersByType;

  /**
   * Creates a composite data output with multiple entity configurations.
   * 
   * @param entityConfigs configuration for each entity type (type name -> config)
   * @param typeRef the type reference for JSON serialization
   * @param exportDir the directory where CSV files will be created
   * @throws IOException if file creation fails
   */
  public CompositeDataOutput(
      Map<String, EntityConfig> entityConfigs,
      TypeReference<T> typeRef,
      Path exportDir) throws IOException {
    
    this.outputsByType = new LinkedHashMap<>();
    this.writersByType = new LinkedHashMap<>();
    
    for (Map.Entry<String, EntityConfig> entry : entityConfigs.entrySet()) {
      String entityType = entry.getKey();
      EntityConfig config = entry.getValue();
      
      // Create writer for this entity type
      String filename = config.getFilename() != null ? 
          config.getFilename() : entityType + ".csv";
      Writer writer = new FileWriter(
          exportDir.resolve(filename).toFile(), 
          StandardCharsets.UTF_8);
      
      // Create TabularOutput for this entity type
      TabularOutput<T> output = TabularOutput.create(
          TabularOutput.TabularOutputArgs.builder()
              .headers(config.getColumns())
              .receivedHeadersAliases(config.getAliases())
              .columnSeparator(config.getSeparator())
              .build(),
          typeRef,
          writer);
      
      outputsByType.put(entityType, output);
      writersByType.put(entityType, writer);
    }
  }

  @Override
  public void addRecord(String type, T record) throws IOException {
    TabularOutput<T> output = outputsByType.get(type);
    if (output == null) {
      throw new IllegalArgumentException(
          "No output configured for entity type: " + type);
    }
    output.addRecord(type, record);
  }

  @Override
  public void close() throws IOException {
    IOException firstException = null;
    
    // Close all outputs
    for (TabularOutput<T> output : outputsByType.values()) {
      try {
        output.close();
      } catch (IOException e) {
        if (firstException == null) {
          firstException = e;
        }
      }
    }
    
    // Close all writers
    for (Writer writer : writersByType.values()) {
      try {
        writer.close();
      } catch (IOException e) {
        if (firstException == null) {
          firstException = e;
        }
      }
    }
    
    if (firstException != null) {
      throw firstException;
    }
  }

  /**
   * Configuration for a single entity type's CSV output.
   */
  @Builder
  @Getter
  public static class EntityConfig {
    private final String filename;
    private final List<String> columns;
    private final List<String> aliases;
    private final TabularOutput.ColumnSeparator separator;
  }
}
