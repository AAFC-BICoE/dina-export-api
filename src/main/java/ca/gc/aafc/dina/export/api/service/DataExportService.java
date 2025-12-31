package ca.gc.aafc.dina.export.api.service;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Service;
import org.springframework.validation.SmartValidator;

import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.generator.DataExportGenerator;
import ca.gc.aafc.dina.jpa.BaseDAO;
import ca.gc.aafc.dina.service.DefaultDinaService;

/**
 * Responsible for main database operations and to call the generator based on export type.
 */
@Log4j2
@Service
public class DataExportService extends DefaultDinaService<DataExport> {

  private final DataExportGenerator tabularDataExportGenerator;
  private final DataExportGenerator objectStoreExportGenerator;

  private final Consumer<Future<UUID>> asyncConsumer;

  /**
   *
   * @param baseDAO
   * @param validator
   * @param tabularDataExportGenerator
   * @param objectStoreExportGenerator
   * @param asyncConsumer optional consumer to get the Future created for the async export
   */
  public DataExportService(BaseDAO baseDAO,
                           SmartValidator validator,
                           DataExportGenerator tabularDataExportGenerator,
                           DataExportGenerator objectStoreExportGenerator,
                           Optional<Consumer<Future<UUID>>> asyncConsumer) {
    super(baseDAO, validator);
    this.tabularDataExportGenerator = tabularDataExportGenerator;
    this.objectStoreExportGenerator = objectStoreExportGenerator;
    this.asyncConsumer = asyncConsumer.orElse(null);
  }

  @Override
  public void preCreate(DataExport dinaExport) {

    if (dinaExport.getUuid() == null) {
      dinaExport.setUuid(UUID.randomUUID());
    }
    dinaExport.setStatus(DataExport.ExportStatus.NEW);
    dinaExport.setFilename(generatorByExportType(dinaExport).generateFilename(dinaExport));
  }

  @Override
  public void postCreate(DataExport dinaExport) {
    flush();

    DataExportGenerator exportGenerator = generatorByExportType(dinaExport);

    try {
      if (asyncConsumer == null) {
        exportGenerator.export(dinaExport)
          .exceptionally(ex -> {
            log.error("Async exception:", ex);
            return null;
          });
      } else {
        asyncConsumer.accept(exportGenerator.export(dinaExport));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public DataExport findOne(UUID uuid) {
    return findOne(uuid, DataExport.class);
  }

  @Override
  public void delete(DataExport dinaExport) {
    delete(dinaExport, true);
  }

  public void delete(DataExport dinaExport, boolean callExportGenerator) {
    if (callExportGenerator) {
      DataExportGenerator exportGenerator = generatorByExportType(dinaExport);
      try {
        exportGenerator.deleteExport(dinaExport);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    super.delete(dinaExport);
  }

  private DataExportGenerator generatorByExportType(DataExport export) {
    return switch (export.getExportType()) {
      case TABULAR_DATA -> tabularDataExportGenerator;
      case OBJECT_ARCHIVE -> objectStoreExportGenerator;
    };
  }
}
