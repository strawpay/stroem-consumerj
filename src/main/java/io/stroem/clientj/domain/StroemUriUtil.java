package io.stroem.clientj.domain;

import java.net.URI;

/**
 * <p>StroemUriUtil to provide the following :</p>
 * <ul>
 * <li> </li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemUriUtil {


  /**
   * @param uri will be used to find the base domain name. Example: "http;//aoeu.strawpay.com/x=1131&y=1235"
   * @return the top-level and second-level domain name. Example: "strawpay.com"
   */
  public static String getBaseDomainNameFromUri(URI uri) {
    if (uri == null) {
      throw new IllegalArgumentException("Cannot perform operation if URI is null");
    }
    String host = uri.getHost();
    // Extract last part, like "strawpay.com"
    int lastDotPos = host.lastIndexOf(".");
    if (lastDotPos == -1) {
      throw new IllegalStateException("URI is not valid (missing dot): " + uri.toString());
    }
    // look for more dots
    String noTopDomain = host.substring(0, lastDotPos);
    // Check for more dots
    int secondLastDotPos = noTopDomain.lastIndexOf(".");
    if (secondLastDotPos == -1) {
      // No more dots, just return
      return host;
    } else {
      // Remove all third-level (and higher) domain names
      String realDomain = host.substring(secondLastDotPos + 1);
      return realDomain;
    }
  }
}
