package io.stroem.clientj.domain;

import org.bitcoinj.uri.BitcoinURI;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * <p>StroemUriTest to provide the following :</p>
 * <ul>
 * <li> </li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemUriTest {

  @Test
  public void testOkUri() throws Exception {
     BitcoinURI bitcoinURI = new BitcoinURI(
         "bitcoin:1AhN6rPdrMuKBGFDKR1k9A8SCLYaNgXhty?r.stroem=true&r=" +
             "http%3A%2F%2Flocalhost%3A9000%2Fapi%2Fpaymentrequest%2F1636914932393220992"
     );

     StroemUri stroemUri = new StroemUri(bitcoinURI);
    assertTrue(stroemUri.isStroemPayment());

    stroemUri.addIssuerDomainName("olle.com");
    String uriStr = stroemUri.getStroemParamUriAsString();

    assertEquals("http://localhost:9000/api/paymentrequest/1636914932393220992?stroem.issuer=olle.com", uriStr);
  }

  @Test
  public void testUriWithOtherParam() throws Exception {
     BitcoinURI bitcoinURI = new BitcoinURI(
         "bitcoin:1AhN6rPdrMuKBGFDKR1k9A8SCLYaNgXhty?r.stroem=true&r=" +
             "http%3A%2F%2Flocalhost%3A9000%2Fapi%2Fpaymentrequest%2F1636914932393220992%3Fa=1111"
     );

     StroemUri stroemUri = new StroemUri(bitcoinURI);
    assertTrue(stroemUri.isStroemPayment());

    stroemUri.addIssuerDomainName("olle.com");
    String uriStr = stroemUri.getStroemParamUriAsString();

    assertEquals("http://localhost:9000/api/paymentrequest/1636914932393220992?a=1111&stroem.issuer=olle.com", uriStr);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAlreadyProvidedIssuerInBitcoinUrl() throws Exception {
     BitcoinURI bitcoinURI = new BitcoinURI(
         "bitcoin:1AhN6rPdrMuKBGFDKR1k9A8SCLYaNgXhty?r.stroem=true&r=" +
             "http%3A%2F%2Flocalhost%3A9000%2Fapi%2Fpaymentrequest%2F1636914932393220992%3Fstroem.issuer=olle.com"
     );

     StroemUri stroemUri = new StroemUri(bitcoinURI);
    assertTrue(stroemUri.isStroemPayment());

    stroemUri.addIssuerDomainName("olle.com");
    String uriStr = stroemUri.getStroemParamUriAsString();
    fail("Should not get here");

  }
}
