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
  public void test_isStroemUri() throws Exception {
    StroemUri stroemUri = new StroemUri(null,
        "bitcoin:mqEwuEiuVw6CGaxh9Yghuo7jRWYxU8NpS7?amount=0.01500000&r.stroem=" +
            "http%3A%2F%2Flocalhost%3A9000%2Fapi%2Fpaymentrequest%2F7237482603553792996",
            null
    );

    assertTrue(stroemUri.isStroemPayment());

    stroemUri.addIssuerName("a");
    String destUri = stroemUri.getPaymentRequestUrl();

    System.out.println("Dest URI: " + destUri);
    assertEquals("http://localhost:9000/api/paymentrequest/7237482603553792996?stroem.issuer=a", destUri);

  }

  @Test
  public void test_addIssuerNameOkUri() throws Exception {
    StroemUri stroemUri = new StroemUri(null,
        "bitcoin:1AhN6rPdrMuKBGFDKR1k9A8SCLYaNgXhty?r.stroem=true&r=" +
            "http%3A%2F%2Flocalhost%3A9000%2Fapi%2Fpaymentrequest%2F1636914932393220992"
            ,null
    );

    assertTrue(stroemUri.isStroemPayment());

    stroemUri.addIssuerName("olle.com");
    String uriStr = stroemUri.getPaymentRequestUrl();

    assertEquals("http://localhost:9000/api/paymentrequest/1636914932393220992?stroem.issuer=olle.com", uriStr);
  }

  @Test
  public void test_addIssuerNameUriWithOtherParam() throws Exception {
    StroemUri stroemUri = new StroemUri(null,
        "bitcoin:1AhN6rPdrMuKBGFDKR1k9A8SCLYaNgXhty?r.stroem=true&r=" +
            "http%3A%2F%2Flocalhost%3A9000%2Fapi%2Fpaymentrequest%2F1636914932393220992%3Fa=1111"
            , null
    );

    assertTrue(stroemUri.isStroemPayment());

    stroemUri.addIssuerName("olle.com");
    String uriStr = stroemUri.getPaymentRequestUrl();

    assertEquals("http://localhost:9000/api/paymentrequest/1636914932393220992?a=1111&stroem.issuer=olle.com", uriStr);
  }

  @Test
  public void test_addIssuerNameUri_2() throws Exception {
    StroemUri stroemUri = new StroemUri(null,
        "bitcoin:mqEwuEiuVw6CGaxh9Yghuo7jRWYxU8NpS7?amount=0.01500000&r.stroem=" +
            "http%3A%2F%2Flocalhost%3A9000%2Fapi%2Fpaymentrequest%2F7237482603553792996"
            , null
    );

    assertTrue(stroemUri.isStroemPayment());

    stroemUri.addIssuerName("olle.com");
    String uriStr = stroemUri.getPaymentRequestUrl();

    System.out.println("Dest URI: " + uriStr);
    assertEquals("http://localhost:9000/api/paymentrequest/7237482603553792996?stroem.issuer=olle.com", uriStr);
  }



  @Test(expected = IllegalArgumentException.class)
  public void test_addIssuerNameAlreadyProvidedIssuerInBitcoinUrl() throws Exception {
    StroemUri stroemUri = new StroemUri(null,
        "bitcoin:1AhN6rPdrMuKBGFDKR1k9A8SCLYaNgXhty?r.stroem=true&r=" +
            "http%3A%2F%2Flocalhost%3A9000%2Fapi%2Fpaymentrequest%2F1636914932393220992%3Fstroem.issuer=olle.com"
            ,null
    );

    assertTrue(stroemUri.isStroemPayment());

    stroemUri.addIssuerName("olle.com");
    String uriStr = stroemUri.getPaymentRequestUrl();
    fail("Should not get here");

  }
}
