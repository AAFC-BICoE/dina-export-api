package ca.gc.aafc.dina.export.api.output;

import java.io.IOException;
import java.util.Map;

/**
 * Composite DataOutput that routes records to different TabularOutput instances based on its type.
 * Implements the Composite pattern for DataOutput, delegating to multiple TabularOutput instances.
 * 
 * @param <T> the record type
 */
public class CompositeDataOutput<T> implements DataOutput<T> {

  private final Map<String, TabularOutput<Object, T>> outputsByType;

  /**
   * Creates a composite data output with multiple entity configurations.
   * 
   * @param outputsByType tabular output for each type (type name -> TabularOutput)
   * @throws IOException if file creation fails
   */
  public CompositeDataOutput(Map<String, TabularOutput<Object, T>> outputsByType) {
    this.outputsByType = Map.copyOf(outputsByType);
  }

  @Override
  public void addRecord(T record) throws IOException {
    throw new IllegalArgumentException("type required for CompositeDataOutput");
  }

  @Override
  public void addRecord(String type, T record) throws IOException {
    TabularOutput<Object, T> output = outputsByType.get(type);
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
    for (TabularOutput<Object, T> output : outputsByType.values()) {
      try {
        output.close();
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

}
