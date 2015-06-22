package io.stroem.clientj.domain;


import org.bitcoinj.uri.BitcoinURI;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

/**
 * <p>StroemUriUtilTest to provide the following :</p>
 * <ul>
 * <li> </li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemUriUtilTest {


  @Test
  public void testOkUri() throws Exception {
    String in = "http://strawpay.com/x=1131&y=1235";
    String out = "strawpay.com";

    URI uri = new URI(in);
    String ret = StroemUriUtil.getBaseDomainNameFromUri(uri);

    assertEquals(ret, out);
  }

  @Test
  public void testLongOkUri() throws Exception {
    String in = "http://xxx.aoeu.strawpay.com/x=1131&y=1235";
    String out = "strawpay.com";

    URI uri = new URI(in);
    String ret = StroemUriUtil.getBaseDomainNameFromUri(uri);

    assertEquals(ret, out);
  }
}
