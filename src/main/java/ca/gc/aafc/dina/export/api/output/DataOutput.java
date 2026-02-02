package ca.gc.aafc.dina.export.api.output;

import java.io.IOException;

/**
 * Main interface for record output.
 * @param <I> record identifier class
 * @param <T> record class
 */
public interface DataOutput <I, T> extends AutoCloseable {

  /**
   * Add record without type
   * @param id identifier of the record
   * @param record the record
   * @throws IOException
   */
  void addRecord(I id, T record) throws IOException;

  void addRecord(String type, I id, T record) throws IOException;
}
