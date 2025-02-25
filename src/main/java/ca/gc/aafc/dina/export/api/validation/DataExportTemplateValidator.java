package ca.gc.aafc.dina.export.api.validation;

import org.apache.commons.lang3.BooleanUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import ca.gc.aafc.dina.export.api.entity.DataExportTemplate;
import ca.gc.aafc.dina.validation.DinaBaseValidator;

@Component
public class DataExportTemplateValidator extends DinaBaseValidator<DataExportTemplate> {

  static final String INVALID_PUBLICLY_AVAILABLE_KEY = "dataExportTemplate.publiclyReleasableAndRestrictToCreatedBy.invalid";

  public DataExportTemplateValidator(MessageSource messageSource) {
    super(DataExportTemplate.class, messageSource);
  }

  @Override
  public void validateTarget(DataExportTemplate target, Errors errors) {

    if (BooleanUtils.isTrue(target.getPubliclyReleasable()) &&
      BooleanUtils.isTrue(target.getRestrictToCreatedBy())) {
      String errorMessage = getMessage(INVALID_PUBLICLY_AVAILABLE_KEY);
      errors.reject(INVALID_PUBLICLY_AVAILABLE_KEY, errorMessage);
    }
  }
}
