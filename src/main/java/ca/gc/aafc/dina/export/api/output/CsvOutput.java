package ca.gc.aafc.dina.export.api.output;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

/**
 * Responsible to write a CSV output.
 * @param <T>
 */
public final class CsvOutput<T> implements AutoCloseable {

  private final SequenceWriter sw;

  /**
   * Returns a CsvOutput instance configured for a specific type.
   * @param headers should match the properties available in the type.
   * @param typeRef the type
   * @param writer won't be closed. Responsibility of the caller.
   * @return
   */
  public static <T> CsvOutput<T> create(List<String> headers, TypeReference<T> typeRef, Writer writer)
    throws IOException {
    CsvSchema.Builder builder = CsvSchema.builder()
      .addColumns(headers, CsvSchema.ColumnType.STRING);

    CsvSchema csvSchema = builder.build().withHeader();

    CsvMapper csvMapper = new CsvMapper();
    csvMapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
    return new CsvOutput<>(csvMapper.writerFor(typeRef)
      .with(csvSchema).writeValues(writer));
  }

  private CsvOutput(SequenceWriter sw) {
    this.sw = sw;
  }

  public void addRow(T row) throws IOException {
    sw.write(row);
  }

  @Override
  public void close() throws IOException {
    sw.close();
  }
}
