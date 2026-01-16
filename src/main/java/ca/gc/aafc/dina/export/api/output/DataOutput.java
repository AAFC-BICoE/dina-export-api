package ca.gc.aafc.dina.export.api.output;

import java.io.IOException;

/**
 * Main interface for record output.
 * @param <T>
 */
public interface DataOutput <T> extends AutoCloseable {

  /**
   * Add record without type
   * @param record
   * @throws IOException
   */
  void addRecord(T record) throws IOException;

  void addRecord(String type, T record) throws IOException;
}
