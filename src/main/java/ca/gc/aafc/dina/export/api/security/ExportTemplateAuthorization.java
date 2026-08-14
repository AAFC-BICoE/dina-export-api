package ca.gc.aafc.dina.export.api.security;

import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.security.auth.PermissionAuthorizationService;

@Service
public class ExportTemplateAuthorization extends PermissionAuthorizationService {

  public ExportTemplateAuthorization() {
  }

  @PreAuthorize("hasObjectOwnership(@currentUser, #entity)")
  public void authorizeCreate(Object entity) {
  }

  @PreAuthorize("isObjectPubliclyReleasable(#entity) || hasObjectOwnership(@currentUser, #entity) || " +
    "(!#entity.getRestrictToCreatedBy() && hasMinimumGroupAndRolePermissions(@currentUser, 'READ_ONLY', #entity))")
  public void authorizeRead(Object entity) {
  }

  @PreAuthorize("hasObjectOwnership(@currentUser, #entity)")
  public void authorizeUpdate(Object entity) {
  }

  @PreAuthorize("hasObjectOwnership(@currentUser, #entity)")
  public void authorizeDelete(Object entity) {
  }

  public Set<String> evaluatedAttributes() {
    return Set.of("createdBy", "group", "restrictToCreatedBy", "publiclyReleasable");
  }

  public String getName() {
    return ExportTemplateAuthorization.class.getSimpleName();
  }
}
