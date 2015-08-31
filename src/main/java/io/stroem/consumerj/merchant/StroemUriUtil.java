package io.stroem.consumerj.merchant;

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

    public static final String NO_DOMAIN_NAME_IN_URI = "no.domain.name";


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
            if (host.equalsIgnoreCase("localhost")) {
                return host; // We want to allow this for testing
            } else {
                throw new IllegalStateException("URI is not valid (missing dot): " + uri.toString());
            }
        }

        // Exit if this is a IP number, like "172.27.773.0"
        String topDomain = host.substring(lastDotPos + 1);
        if (Character.isDigit(topDomain.charAt(0)) ) {
            return NO_DOMAIN_NAME_IN_URI;
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
