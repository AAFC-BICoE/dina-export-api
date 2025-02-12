package ca.gc.aafc.dina.export.api.output;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

/**
 * Responsible to write a tabular output (csv, tsv).
 * @param <T>
 */
public final class TabularOutput<T> implements DataOutput<T> {

  public static final String OPTION_COLUMN_SEPARATOR = "columnSeparator";

  public enum ColumnSeparator {
    COMMA(","), TAB("\t");
    private final String separatorChar;

    ColumnSeparator(String separatorChar) {
      this.separatorChar = separatorChar;
    }

    /**
     * More lenient version of {@link #valueOf(String)}.
     * Case-insensitive and returning Optional instead of throwing exceptions.
     * @param text
     * @return
     */
    public static Optional<ColumnSeparator> fromString(String text) {
      for (ColumnSeparator curr : values()) {
        if (text.equalsIgnoreCase(curr.toString())) {
          return Optional.of(curr);
        }
      }
      return Optional.empty();
    }

    public Character getSeparatorChar() {
      return separatorChar.charAt(0);
    }
  }

  private final SequenceWriter sw;

  /**
   * Returns a {@link TabularOutput} instance configured for a specific type without using column aliases.
   * @param tabularOutputArgs headers should match the properties available in the specific type.
   * @param typeRef the type
   * @param writer won't be closed. Responsibility of the caller.
   * @return
   */
  private static <T> TabularOutput<T> createTabularFileNoAlias(TabularOutputArgs tabularOutputArgs, TypeReference<T> typeRef,
                                                               Writer writer) throws IOException {
    CsvSchema.Builder builder = CsvSchema.builder()
      .addColumns(tabularOutputArgs.getHeaders(), CsvSchema.ColumnType.STRING);

    CsvSchema csvSchema = builder.build().withHeader();

    if(tabularOutputArgs.getColumnSeparator() != null) {
      csvSchema = csvSchema.withColumnSeparator(tabularOutputArgs.getColumnSeparator().getSeparatorChar());
    }

    CsvMapper csvMapper = new CsvMapper();
    csvMapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
    return new TabularOutput<>(csvMapper.writerFor(typeRef)
      .with(csvSchema).writeValues(writer));
  }

  /**
   * Returns a {@link TabularOutput} instance configured for a specific type.
   * @param tabularOutputArgs headers should match the properties available in the type.
   *                      Aliases for headers matched by the order in the list. If an entry is empty string,
   *                      it will be replaced by the header value
   * @param typeRef the type
   * @param writer won't be closed. Responsibility of the caller.
   * @return
   */
  public static <T> TabularOutput<T> create(TabularOutputArgs tabularOutputArgs,
                                            TypeReference<T> typeRef, Writer writer) throws IOException {

    if (CollectionUtils.isEmpty(tabularOutputArgs.getReceivedHeadersAliases())) {
      return createTabularFileNoAlias(tabularOutputArgs, typeRef, writer);
    }

    if (tabularOutputArgs.getHeaders() == null ||
      tabularOutputArgs.getHeaders().size() != tabularOutputArgs.getReceivedHeadersAliases().size()) {
      throw new IllegalArgumentException(
        "headersAliases should match headers size and not be null");
    }

    //Replace empty aliases by header names. Copy the list since we will change it.
    List<String> headersAliases = new ArrayList<>(tabularOutputArgs.getReceivedHeadersAliases());
    for (int i = 0; i < headersAliases.size(); i++) {
      if (StringUtils.isEmpty(headersAliases.get(i))) {
        headersAliases.set(i, tabularOutputArgs.getHeaders().get(i));
      }
    }

    // Write all the header aliases first
    CsvSchema.Builder builder = CsvSchema.builder()
      .addColumns(headersAliases, CsvSchema.ColumnType.STRING);
    CsvSchema csvSchema = builder.build().withHeader();
    if(tabularOutputArgs.getColumnSeparator() != null) {
      csvSchema = csvSchema.withColumnSeparator(tabularOutputArgs.getColumnSeparator().getSeparatorChar());
    }

    CsvMapper csvMapper = new CsvMapper();
    SequenceWriter ow = csvMapper.writer().with(csvSchema).writeValues(writer);
    ow.write(null);
    ow.flush();

    //Use the real headers but configure the builder to not write them (since we have the aliases)
    builder = CsvSchema.builder()
      .addColumns(tabularOutputArgs.getHeaders(), CsvSchema.ColumnType.STRING);
    csvSchema = builder.build().withoutHeader();
    csvMapper = new CsvMapper();
    csvMapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
    return new TabularOutput<>(csvMapper.writerFor(typeRef)
      .with(csvSchema).writeValues(writer));
  }

  private TabularOutput(SequenceWriter sw) {
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

  @Builder
  @Getter
  public static class TabularOutputArgs {
    private final List<String> headers;
    private final List<String> receivedHeadersAliases;
    private final ColumnSeparator columnSeparator;
  }
}
