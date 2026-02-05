package ca.gc.aafc.dina.export.api.output;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 */
public final class TabularOutput<I, T> implements DataOutput<I, T> {

  public static final String OPTION_COLUMN_SEPARATOR = "columnSeparator";
  public static final String OPTION_ENABLE_ID_TRACKING = "enableIdTracking";
  public static final String OPTION_ENABLE_PACKAGING = "enablePackaging";

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
  private final Set<I> trackedIds;
  private final boolean idTrackingEnabled;

  /**
   * Returns a {@link TabularOutput} instance configured for a specific type without using column aliases.
   * @param tabularOutputArgs headers should match the properties available in the specific type.
   * @param typeRef the type
   * @param writer won't be closed. Responsibility of the caller.
   * @return
   */
  private static <I, T> TabularOutput<I, T> createTabularFileNoAlias(TabularOutputArgs tabularOutputArgs, TypeReference<T> typeRef,
                                                               Writer writer) throws IOException {

    CsvSchema csvSchema = buildCsvSchema(tabularOutputArgs.getHeaders(), true, tabularOutputArgs);
    CsvMapper csvMapper = new CsvMapper();
    csvMapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
    return new TabularOutput<>(csvMapper.writerFor(typeRef)
      .with(csvSchema).writeValues(writer), tabularOutputArgs.isEnableIdTracking());
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
  public static <I, T> TabularOutput<I, T> create(TabularOutputArgs tabularOutputArgs,
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
    CsvSchema csvHeaderSchema = buildCsvSchema(headersAliases, true, tabularOutputArgs);
    CsvMapper csvMapper = new CsvMapper();
    SequenceWriter ow = csvMapper.writer().with(csvHeaderSchema).writeValues(writer);
    ow.write(null);
    ow.flush();

    //Use the real headers but configure the builder to not write them (since we have the aliases)
    CsvSchema csvSchema = buildCsvSchema(tabularOutputArgs.getHeaders(), false, tabularOutputArgs);
    csvMapper = new CsvMapper();
    csvMapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
    return new TabularOutput<>(csvMapper.writerFor(typeRef)
      .with(csvSchema).writeValues(writer), tabularOutputArgs.isEnableIdTracking());
  }

  /**
   * Internal function to build the {@link CsvSchema} based on specific settings
   * @param headers list of headers to use for the schema
   * @param useHeader should the header be used (for output)
   * @param tabularOutputArgs used to find the separator
   * @return
   */
  private static CsvSchema buildCsvSchema(List<String> headers, boolean useHeader, TabularOutputArgs tabularOutputArgs) {
    CsvSchema.Builder builder = CsvSchema.builder()
      .addColumns(headers, CsvSchema.ColumnType.STRING);

    builder.setUseHeader(useHeader);

    if (tabularOutputArgs.getColumnSeparator() != null) {
      builder.setColumnSeparator(tabularOutputArgs.getColumnSeparator().getSeparatorChar());
    }
    
    return builder.build();
  }

  private TabularOutput(SequenceWriter sw, boolean idTrackingEnabled) {
    this.sw = sw;
    this.idTrackingEnabled = idTrackingEnabled;
    this.trackedIds = idTrackingEnabled ? new HashSet<>() : null;
  }

  /**
   * Adds a record to the CSV output with ID tracking for deduplication.
   * @param id the unique identifier for the record
   * @param record the record to write
   * @throws IOException if writing fails
   */
  @Override
  public void addRecord(I id, T record) throws IOException {
    if (idTrackingEnabled) {
      if (id == null) {
        throw new IllegalArgumentException("ID cannot be null when ID tracking is enabled");
      }
      
      // Skip if already tracked
      if (trackedIds.contains(id)) {
        return;
      }
      
      // Add to tracked IDs
      trackedIds.add(id);
    }
    
    // Write the record
    sw.write(record);
  }

  /**
   * Adds a record to the CSV output.
   *
   * @param type will be ignored for that output
   * @param record the record to write
   * @throws IOException if writing fails
   */
  @Override
  public void addRecord(String type, I id, T record) throws IOException {
    addRecord(id, record);
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
    /**
     * Enable in-memory ID tracking to avoid duplicate records.
     * When enabled, use addRecord(I id, T record) to track and skip duplicates.
     */
    private final boolean enableIdTracking;
  }
}
