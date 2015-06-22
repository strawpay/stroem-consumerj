package io.stroem.clientj.domain;


import org.bitcoinj.uri.BitcoinURI;

import java.util.Objects;

/**
 * <p>StroemUrl to provide the following :</p>
 * <ul>
 *   <li>A URL that could potentially be a normal BIP70 uri and a Stroem payment protocol uri. </li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemUri {

  public static String BIP70_PARAM = "r";
  public static String STROEM_PARAM = "r.stroem";
  public static String STROEM_PARAM_TRUE_VALUE = "true";
  public static String STROEM_ISSUER = "stroem.issuer";

  private BitcoinURI bitcoinURI;
  private String issuerDomainName;


  public StroemUri(BitcoinURI bitcoinURI) {
    this.bitcoinURI = bitcoinURI;
  }

  /**
   * @return True if this is a stroem payment request
   */
  public boolean isStroemPayment() {
    Object stroemParamObj = bitcoinURI.getParameterByName(STROEM_PARAM);
    return stroemParamObj != null;
  }

  public void addIssuerDomainName(String domainName) {
    issuerDomainName = domainName;
  }


  /**
   * Fetches the stroem param Uri. This is a bit tricky, since the URI can reside in the
   * "r" parameter or the "r.stroem" parameter.
   *
   * Throws IllegalStateException if value not found.
   *
   * @return The value of the stroem parameter as a String
   */
  public String getStroemParamUriAsString() {
    if (!isStroemPayment()) {
      throw new IllegalStateException("There is no stroem parameter in this URI");
    }

    Object stroemParamObj = bitcoinURI.getParameterByName(STROEM_PARAM);
    String stroemParamValue = (String) stroemParamObj;
    if (STROEM_PARAM_TRUE_VALUE.equalsIgnoreCase(stroemParamValue)) {
      // The URI must be stored in the other parameter
      Object bip70ParamObj = bitcoinURI.getParameterByName(BIP70_PARAM);
      if (bip70ParamObj == null) {
        throw new IllegalStateException("This is a stroem URI so there must be a " + BIP70_PARAM + " parameter. ");
      } else {
        // Add the issuer domain name before returning
        stroemParamValue = addIssuer((String) bip70ParamObj);
      }
    }
    return stroemParamValue;
  }

  /**
   *
   * @param baseUri
   * @return A string with
   */
  private String addIssuer(String baseUri) {
    if (baseUri.indexOf(STROEM_ISSUER) > -1) {
      throw new IllegalArgumentException("The stroem URI should not include a " + STROEM_ISSUER + " yet:  " + baseUri);
    }

    int questionMarkIndex = baseUri.indexOf("?");
    String glueChar = "?";
    if (questionMarkIndex > -1) {
      // Add the issuer with ampersand instead
      glueChar = "&";
    }
    return baseUri + glueChar + STROEM_ISSUER + "=" + issuerDomainName;
  }
}
