package ca.gc.aafc.dina.export.api.async;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AsyncConsumer<T>  implements Consumer<T> {

  private final List<T> accepted = new ArrayList<>();

  @Override
  public void accept(T o) {
    accepted.add(o);
  }

  public List<T> getAccepted() {
    return accepted;
  }
}
