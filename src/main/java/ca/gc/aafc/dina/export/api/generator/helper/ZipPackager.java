package ca.gc.aafc.dina.export.api.generator.helper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.extern.log4j.Log4j2;

/**
 * Utility for packaging files into a ZIP archive and cleaning up temporary directories.
 */
@Log4j2
public final class ZipPackager {

  private ZipPackager() {
    // utility class
  }

  /**
   * Creates a ZIP archive containing all regular files in the given directory.
   *
   * @param sourceDir the directory whose files will be included
   * @param zipFilePath the output ZIP file path
   * @throws IOException if reading sources or writing the ZIP fails
   */
  public static void createZipPackage(Path sourceDir, Path zipFilePath) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
         ZipOutputStream zipOut = new ZipOutputStream(fos)) {

      List<Path> files;
      try (Stream<Path> paths = Files.walk(sourceDir)) {
        files = paths.filter(Files::isRegularFile).toList();
      }

      for (Path file : files) {
        String entryName = sourceDir.relativize(file).toString();
        zipOut.putNextEntry(new ZipEntry(entryName));
        try (InputStream is = Files.newInputStream(file)) {
          is.transferTo(zipOut);
        }
        zipOut.closeEntry();
      }

      zipOut.finish();
    }
  }

  /**
   * Recursively deletes a directory and all its contents.
   * Files are deleted before their parent directories.
   *
   * @param directory the directory to delete
   * @throws IOException if listing the directory fails
   */
  public static void deleteDirectoryRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()) 
        .forEach(path -> {
          try {
            Files.delete(path);
          } catch (IOException e) {
            log.warn("Failed to delete: {}", path, e);
          }
        });
    }
  }
}
