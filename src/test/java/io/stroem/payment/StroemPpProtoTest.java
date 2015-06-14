
import com.google.protobuf.ByteString;
import org.junit.Test;
import io.stroem.paymentprotocol.StroemPpProtos;
import static org.junit.Assert.*;

/**
 * <p>StroemPpProtosTest to provide the following :</p>
 * <ul>
 * <li> </li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemPpProtoTest {

  private String BYTE_AS_HEX = " 0A 04 74 65 73 74 18 CD BF EB AB 05 20 C1 C3 EB AB 05 C2 3E AE 01 08 0B 62 A9 01 0A 36 0A 11 74 65 73 74 2E 73 74 72 61 77 70 61 79 2E 63 6F 6D 12 21 03 07 AB AC 2A 47 D4 0C D9 80 BC 7B 06 CB 45 59 22 E4 12 A6 77 B9 9E 02 95 B7 64 5F 9C 4C A2 34 4F 12 2B 0A 06 73 6D 65 72 63 68 12 21 03 53 57 00 68 F2 D1 E7 CA AD 28 DC 83 19 3E 38 A8 1B D1 B1 73 00 2F 43 12 6C E7 CD 3A EF F7 3D DB 18 E0 C6 5B 22 09 6B 6F 70 70 20 6F 6C 6C 65 2A 03 42 54 43 32 2B 0A 06 73 6D 65 72 63 68 12 21 03 53 57 00 68 F2 D1 E7 CA AD 28 DC 83 19 3E 38 A8 1B D1 B1 73 00 2F 43 12 6C E7 CD 3A EF F7 3D DB 38 F4 03";

  private byte[] convertFromHex2Byte(String hex) {
    byte[] ret;

    System.out.println("hex len = " + hex.length());
    int len = hex.length() / 3;
    System.out.println("Len = " + len);
    ret = new byte[len];
    for(int i = 0; i < len; i++) {
      int pos = i*3;
      String s = hex.substring(pos, pos + 3 );
      byte b = (byte) ((Character.digit(s.charAt(1), 16) << 4)
          + Character.digit(s.charAt(2), 16));
      ret[i] = b;
    }

    return ret;
  }

  @Test
  public void testConversions() throws Exception {
    byte[] data = convertFromHex2Byte(BYTE_AS_HEX);
    StroemPpProtos.PaymentDetails paymentDetails = StroemPpProtos.PaymentDetails.parseFrom(data);
    System.out.println("Data understood");
    assertNotNull(paymentDetails);
    long l = paymentDetails.getExpires();
    System.out.println("expires = " + l);
    assertTrue(l > 0);
    ByteString stroemDataBytes = paymentDetails.getStroemMessage();
    byte[] stroemData = stroemDataBytes.toByteArray();
    System.out.println("stroem data length: " + stroemData.length);
    assertTrue(stroemData.length > 0);
  }

}