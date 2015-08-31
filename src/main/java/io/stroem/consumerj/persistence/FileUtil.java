package io.stroem.consumerj.persistence;

import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;

/**
 * <p>FileUtil to provide the following :</p>
 * <ul>
 * <li> </li>
 * </ul>
 *
 * @since 0.0.1
 */
public class FileUtil {

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(FileUtil.class);

  private static SecureRandom secureRandom = new SecureRandom();

  /**
   * <p>Atomically create a temporary directory that will be removed when the JVM exits</p>
   *
   * @return A random temporary directory
   * @throws java.io.IOException If something goes wrong
   */
  public static File createTemporaryDirectory() throws IOException {

    // Use JDK7 NIO Files for a more secure operation than Guava
    File topLevelTemporaryDirectory = Files.createTempDirectory("tmp_wallet").toFile();

    topLevelTemporaryDirectory.deleteOnExit();

    // Add a random number to the topLevelTemporaryDirectory
    String temporaryDirectoryName = topLevelTemporaryDirectory.getAbsolutePath() + File.separator + secureRandom.nextInt(Integer.MAX_VALUE);
    log.trace("Temporary directory name:\n'{}'", temporaryDirectoryName);
    File temporaryDirectory = new File(temporaryDirectoryName);
    temporaryDirectory.deleteOnExit();

    if (temporaryDirectory.mkdir() && temporaryDirectory.exists() && temporaryDirectory.canWrite() && temporaryDirectory.canRead()) {
      log.debug("Created temporary directory:\n'{}'", temporaryDirectory.getAbsolutePath());
      return temporaryDirectory;
    }

    // Must have failed to be here
    throw new IOException("Did not create '" + temporaryDirectory.getAbsolutePath() + "' with RW permissions");
  }
}
