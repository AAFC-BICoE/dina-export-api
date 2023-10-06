package ca.gc.aafc.dina.export.api.service;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import lombok.NonNull;

import org.springframework.stereotype.Service;
import org.springframework.validation.SmartValidator;

import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.generator.DataExportGenerator;
import ca.gc.aafc.dina.jpa.BaseDAO;
import ca.gc.aafc.dina.service.DefaultDinaService;

/**
 * Called by the repository. Responsible for main database operations and to call the generator.
 */
@Service
public class DataExportService extends DefaultDinaService<DataExport> {

  private final DataExportGenerator dataExportGenerator;

  private final Consumer<Future<UUID>> asyncConsumer;

  /**
   *
   * @param baseDAO
   * @param validator
   * @param dataExportGenerator
   * @param asyncConsumer optional consumer to get the Future created for the async export
   */
  public DataExportService(@NonNull BaseDAO baseDAO,
                           @NonNull SmartValidator validator,
                           DataExportGenerator dataExportGenerator,
                           Optional<Consumer<Future<UUID>>> asyncConsumer) {
    super(baseDAO, validator);
    this.dataExportGenerator = dataExportGenerator;
    this.asyncConsumer = asyncConsumer.orElse(null);
  }

  @Override
  public void preCreate(DataExport dinaExport) {
    dinaExport.setUuid(UUID.randomUUID());
    dinaExport.setStatus(DataExport.ExportStatus.NEW);
  }

  @Override
  public void postCreate(DataExport dinaExport) {
    try {
      if(asyncConsumer == null) {
        dataExportGenerator.export(dinaExport);
      } else {
        asyncConsumer.accept(dataExportGenerator.export(dinaExport));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
