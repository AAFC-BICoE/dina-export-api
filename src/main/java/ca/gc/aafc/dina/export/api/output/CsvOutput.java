package ca.gc.aafc.dina.export.api.output;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

/**
 * Responsible to write a CSV output.
 * @param <T>
 */
public final class CsvOutput<T> implements DataOutput<T> {

  private final SequenceWriter sw;

  /**
   * Returns a CsvOutput instance configured for a specific type.
   * @param headers should match the properties available in the type.
   * @param typeRef the type
   * @param writer won't be closed. Responsibility of the caller.
   * @return
   */
  public static <T> CsvOutput<T> create(List<String> headers, TypeReference<T> typeRef,
                                        Writer writer) throws IOException {
    CsvSchema.Builder builder = CsvSchema.builder()
      .addColumns(headers, CsvSchema.ColumnType.STRING);

    CsvSchema csvSchema = builder.build().withHeader();

    CsvMapper csvMapper = new CsvMapper();
    csvMapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
    return new CsvOutput<>(csvMapper.writerFor(typeRef)
      .with(csvSchema).writeValues(writer));
  }

  /**
   * Same as {@link #create(List, TypeReference, Writer)} but using aliases for headers.
   * @param headers should match the properties available in the type.
   * @param receivedHeadersAliases aliases for headers. Matched by the order in the list. If an entry is empty string,
   *                               it will be replaced by the header value
   * @param typeRef the type
   * @param writer won't be closed. Responsibility of the caller.
   * @return
   */
  public static <T> CsvOutput<T> create(List<String> headers, List<String> receivedHeadersAliases,
                                        TypeReference<T> typeRef, Writer writer) throws IOException {

    if (CollectionUtils.isEmpty(receivedHeadersAliases)) {
      return create(headers, typeRef, writer);
    }

    if (headers == null || headers.size() != receivedHeadersAliases.size()) {
      throw new IllegalArgumentException(
        "headersAliases should match headers size and not be null");
    }

    //Replace empty aliases by header names. Copy the list since we will change it.
    List<String> headersAliases = new ArrayList<>(receivedHeadersAliases);
    for (int i = 0; i < headersAliases.size(); i++) {
      if (StringUtils.isEmpty(headersAliases.get(i))) {
        headersAliases.set(i, headers.get(i));
      }
    }

    // Write all the header aliases first
    CsvSchema.Builder builder = CsvSchema.builder()
      .addColumns(headersAliases, CsvSchema.ColumnType.STRING);
    CsvSchema csvSchema = builder.build().withHeader();
    CsvMapper csvMapper = new CsvMapper();
    SequenceWriter ow = csvMapper.writer().with(csvSchema).writeValues(writer);
    ow.write(null);
    ow.flush();

    //Use the real headers but configure the builder to not write them (since we have the aliases)
    builder = CsvSchema.builder()
      .addColumns(headers, CsvSchema.ColumnType.STRING);
    csvSchema = builder.build().withoutHeader();
    csvMapper = new CsvMapper();
    csvMapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
    return new CsvOutput<>(csvMapper.writerFor(typeRef)
      .with(csvSchema).writeValues(writer));
  }

  private CsvOutput(SequenceWriter sw) {
    this.sw = sw;
  }

  @Override
  public void addRecord(T record) throws IOException {
    sw.write(record);
  }

  @Override
  public void close() throws IOException {
    sw.close();
  }
}
