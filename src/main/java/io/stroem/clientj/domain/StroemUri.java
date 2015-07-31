package io.stroem.clientj.domain;


import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;

import javax.annotation.Nullable;

/**
 * <p>StroemUrl to provide the following :</p>
 * <ul>
 *   <li>A URL that could potentially be a normal BIP70 uri and a Stroem payment protocol uri. </li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemUri extends BitcoinURI {

  public static String BIP70_PARAM = "r";
  public static String STROEM_PARAM = "r.stroem";
  public static String STROEM_PARAM_TRUE_VALUE = "true";
  public static String STROEM_ISSUER = "stroem.issuer";

  private String issuerName; // Needed so that the merchant can verify if the issuer is supported.

  /**
   * Constructs a new object by trying to parse the input as a valid Bitcoin URI.
   *
   * @param params The network parameters that determine which network the URI is from, or null if you don't have
   *               any expectation about what network the URI is for and wish to check yourself.
   * @param input The raw URI data to be parsed (see class comments for accepted formats)
   * @param issuerName The name of the issuer the wallet wants to use for the payment, must be sent to
   *                   the merchant so the merchant can validate if the issuer is acceptable.
   *                   If issuer is not set here in the constructor, addIssuerName() must be called before
   *                   getStroemParamUriAsString() is called.
   *
   * @throws BitcoinURIParseException If the input fails Bitcoin URI syntax and semantic checks.
   */
  public StroemUri(@Nullable NetworkParameters params, String input, @Nullable String issuerName) throws BitcoinURIParseException {
    super(params, input);
    if (issuerName != null) {
      this.issuerName = issuerName;
    }
  }

  /**
   * @return True if this is a stroem payment request
   */
  public boolean isStroemPayment() {
    Object stroemParamObj = getParameterByName(STROEM_PARAM);
    return stroemParamObj != null;
  }

  public String getIssuerName() {
    return issuerName;
  }

  public void addIssuerName(String issuerName) {
    this.issuerName = issuerName;
  }


  /**
   * Fetches the stroem param Uri. This is a bit tricky, since the URI can reside in the
   * "r" parameter or the "r.stroem" parameter.
   *
   * Note that the StroemUri should have an issuerName at this stage.
   *
   * Throws IllegalStateException if value not found.
   *
   * @return The value of the stroem parameter as a String
   */
  @Override
  public String getPaymentRequestUrl() {
    if (isStroemPayment()) {
      return getStroemPaymenRequestUrl();
    } else {
      return super.getPaymentRequestUrl();
    }
  }

  private String getStroemPaymenRequestUrl() {
    String retUri;
    Object stroemParamObj = getParameterByName(STROEM_PARAM);
    String stroemParamValue = (String) stroemParamObj;
    if (stroemParamValue.length() < 1) {
      throw new IllegalArgumentException("The " + STROEM_PARAM + " parameter must be set in theURI.");
    }

    if (issuerName == null || issuerName.isEmpty()) {
      throw new IllegalArgumentException("Every StroemURI must have an issuer name");
    }

    if (STROEM_PARAM_TRUE_VALUE.equalsIgnoreCase(stroemParamValue)) {
      // The URI must be stored in the other parameter
      Object bip70ParamObj = getParameterByName(BIP70_PARAM);
      if (bip70ParamObj == null) {
        throw new IllegalStateException("This is a stroem URI so there must be a " + BIP70_PARAM + " parameter. ");
      } else {
        // Add the issuer domain name before returning
        retUri = addIssuer((String) bip70ParamObj);
      }
    } else {
      // Then this must be the URI, and we have a pure Stroem URL
      retUri = addIssuer(stroemParamValue);

    }
    return retUri;
  }

  /**
   *
   * @param baseUri
   * @return A string with issuer name added
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
    return baseUri + glueChar + STROEM_ISSUER + "=" + issuerName;
  }
}
