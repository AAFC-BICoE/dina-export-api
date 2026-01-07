package ca.gc.aafc.dina.export.api.output;

import java.io.IOException;

/**
 * Main interface for record output.
 * @param <T>
 */
public interface DataOutput <T> extends AutoCloseable {

  void addRecord(String type, T record) throws IOException;
}
