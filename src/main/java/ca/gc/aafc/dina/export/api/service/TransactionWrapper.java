package ca.gc.aafc.dina.export.api.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Helper class that allows to wrap a function inside a transaction.
 */
@Component
public class TransactionWrapper {

  /**
   * Runs the provided supplier inside a read-only transaction.
   * @param supplier supplier to execute.
   * @return return value of the supplier
   */
  @Transactional(
    readOnly = true
  )
  public <T> T runInsideReadTransaction(Supplier<T> supplier) {
    return supplier.get();
  }

}
