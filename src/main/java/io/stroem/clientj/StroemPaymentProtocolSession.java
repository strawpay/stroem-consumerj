package io.stroem.clientj;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.stroem.api.Messages;
import io.stroem.clientj.domain.*;
import io.stroem.javaapi.JavaToScalaBridge;
import io.stroem.proto.StroemProtos;
import io.stroem.paymentprotocol.StroemPpProtos;
import org.bitcoin.protocols.payments.Protos;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TrustStoreLoader;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.protocols.payments.PaymentProtocolException;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.*;
import java.util.Date;
import java.util.concurrent.Callable;

/**
 * <p>StroemPaymentProtocolSession to provide the following :</p>
 * <ul>
 * <li>A payment protocol session to the merchant server.</li>
 * <li>.</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemPaymentProtocolSession {

  private static final Logger log = LoggerFactory.getLogger(StroemPaymentProtocolSession.class);

  private static ListeningExecutorService executor = Threading.THREAD_POOL;
  private NetworkParameters params;
  private final TrustStoreLoader trustStoreLoader; // Not used as of now
  private StroemPpProtos.PaymentRequest paymentRequest;
  private StroemProtos.MerchantPaymentDetails merchantPaymentDetails;
  private Coin totalValue;
  private Date creationDate;
  private Date expiryDate;
  private byte[] stroemData;
  private URI merchantUri;
  private String issuerName;

  /**
   * Stores the calculated PKI verification data, or null if none is available.
   * Only valid after the session is created with the verifyPki parameter set to true.
   */
  @Nullable public final PaymentProtocol.PkiVerificationData pkiVerificationData;

  /**
   * Returns a future that will be notified with a StroemPaymentProtocolSession object after it is fetched using the provided uri.
   * uri is a BIP-72-style Stroem URI object that specifies where the {@link StroemPpProtos.PaymentRequest} object may
   * be fetched, usually in the r= parameter.
   *
   *  An exception is thrown by the future if the signature cannot be verified.
   *
   * Will add the issuer name as a parameter on the URI before calling.
   *
   * Note: PKI method cannot be specified yet.
   */
  public static ListenableFuture<StroemPaymentProtocolSession> createFromStroemUri(final StroemUri uri, final String issuerName)
      throws PaymentProtocolException {
    uri.addIssuerName(issuerName);
    String url = uri.getStroemParamUriAsString();
    if (url == null)
      throw new PaymentProtocolException.InvalidPaymentRequestURL("No payment request URL (r= parameter) in BitcoinURI " + uri);
    try {
      return fetchPaymentRequest(new URI(url), issuerName);
    } catch (URISyntaxException e) {
      throw new PaymentProtocolException.InvalidPaymentRequestURL(e);
    }
  }

  private static ListenableFuture<StroemPaymentProtocolSession> fetchPaymentRequest(final URI uri, final String issuerName) {
    return executor.submit(new Callable<StroemPaymentProtocolSession>() {
      @Override
      public StroemPaymentProtocolSession call() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
        connection.setRequestProperty("Accept", PaymentProtocol.MIMETYPE_PAYMENTREQUEST);
        connection.setUseCaches(false);
        StroemPpProtos.PaymentRequest paymentRequest = StroemPpProtos.PaymentRequest.parseFrom(connection.getInputStream());
        log.debug("Merchant responded with a (Stroem) payment request ");
        return new StroemPaymentProtocolSession(paymentRequest, uri, issuerName);
      }
    });
  }

  /**
   * Creates a StroemPaymentProtocolSession from the provided {@link StroemPpProtos.PaymentRequest}.
   */
  public StroemPaymentProtocolSession(StroemPpProtos.PaymentRequest request, URI merchantUri, String issuerName) throws PaymentProtocolException {
    this.trustStoreLoader = new TrustStoreLoader.DefaultTrustStoreLoader();
    this.merchantUri = merchantUri;
    this.issuerName = issuerName;
    parsePaymentRequest(request);
    pkiVerificationData = null; // TODO:Olle Do we need verification? // PaymentProtocol.verifyPaymentRequestPki(request, this.trustStoreLoader.getKeyStore());
  }


  /**
   * Returns the memo included by the merchant in the payment request, or null if not found.
   */
  @Nullable public String getMemo() {
    if (merchantPaymentDetails.hasDisplayText())
      return merchantPaymentDetails.getDisplayText();
    else
      return null;
  }

  /**
   * Returns the total amount of bitcoins requested.
   */
  public Coin getValue() {
    return totalValue;
  }

  /**
   * Returns the date that the payment request was generated.
   * Use new instance since Date is unreliable.
   */
  public Date getDate() {
    return new Date(creationDate.getTime());
  }

  /**
   * Returns the expires time of the payment request, or null if none.
   */
  @Nullable public Date getExpires() {
    return expiryDate;
  }

  /**
   * This should always be called before attempting to call pay
   */
  public boolean isExpired() {
    Date expires = getExpires();
    if (expires != null) {
      return System.currentTimeMillis() > expires.getTime();
    } else {
      return false;
    }
  }

  /**
   * Sends the negotiated note to the merchant.
   * Will use the same URI as we used to get the payment request.
   *
   * @param stroemMessage to send to the merchant
   */
  public ListenableFuture<StroemPaymentReceipt> sendPromissoryNoteToMerchant(StroemProtos.StroemMessage stroemMessage) {
    return executor.submit(new Callable<StroemPaymentReceipt>() {
      @Override
      public StroemPaymentReceipt call() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) merchantUri.toURL().openConnection();
        connection.setRequestProperty("Accept", PaymentProtocol.MIMETYPE_PAYMENT);
        connection.setUseCaches(false);
        // Get the stroem message first
        StroemProtos.StroemMessage stroemMessageReply = StroemProtos.StroemMessage.parseFrom(connection.getInputStream());
        // Convert it to correct type
        io.stroem.api.PaymentReceipt paymentReceipt = Messages.parsePaymentReceipt(stroemMessageReply).get();
        // Cast it to Java
        return new StroemPaymentReceipt(
            new StroemPaymentHash(paymentReceipt.paymentHash().value()),
            new Date(paymentReceipt.signedAt().getMillis()),
            paymentReceipt.signature().toCanonicalised());
      }
    });

  }

  private void parsePaymentRequest(StroemPpProtos.PaymentRequest request) throws PaymentProtocolException {
    try {
      if (request == null)
        throw new PaymentProtocolException("request cannot be null");
      if (request.getPaymentDetailsVersion() != 1)
        throw new PaymentProtocolException.InvalidVersion("Version 1 required. Received version " + request.getPaymentDetailsVersion());

      log.debug("Begin parsing paymentRequest");
      // Get the merchant details from the request
      paymentRequest = request;
      if (!request.hasSerializedPaymentDetails())
        throw new PaymentProtocolException("No PaymentDetails");

      ByteString paymentDetailsBytes = request.getSerializedPaymentDetails();
      StroemPpProtos.PaymentDetails paymentDetails = StroemPpProtos.PaymentDetails.parseFrom(paymentDetailsBytes);

      // Get expires (getting it from the main PaymentDetails object)
      if (paymentDetails.hasExpires())
        expiryDate = new Date(paymentDetails.getExpires() * 1000L);
      else
        expiryDate = null;

      ByteString stroemDataBytes = null;
      if(paymentDetails.hasStroemMessage()) {
        stroemDataBytes = paymentDetails.getStroemMessage();
      } else {
        throw new RuntimeException("At this point the payment details must have stroem message");
      }
      stroemData = stroemDataBytes.toByteArray();

      creationDate = new Date(paymentDetails.getTime() * 1000);
      merchantPaymentDetails = getMerchantPaymentDetailsFromBytes(stroemDataBytes);
      if (merchantPaymentDetails == null)
        throw new PaymentProtocolException("Invalid PaymentDetails");

      // Get the params from the currency field
      params = getParamsFromCurrency(merchantPaymentDetails);
      if (params == null)
        throw new PaymentProtocolException.InvalidNetwork("Invalid currency " + merchantPaymentDetails.getCurrency());

      totalValue = getTotalValue();
      log.debug("End parsing of payment request");
    } catch (InvalidProtocolBufferException e) {
      throw new PaymentProtocolException(e);
    } catch (IOException  e) {
      throw new PaymentProtocolException(e);
    }
  }

  /**
   * Discussion: The name of the exception thrown here is not 100% correct,
   * but reuse will hold down complexity.
   *
   * @return The amout to pay
   * @throws PaymentProtocolException.InvalidOutputs
   */
  private Coin getTotalValue() throws PaymentProtocolException.InvalidOutputs {
    if (!merchantPaymentDetails.hasAmount()) {
      new StroemPaymentProtocolException("There must be an amount to pay");
    }

    Coin value = Coin.valueOf(merchantPaymentDetails.getAmount());

    // This won't ever happen in practice. It would only happen if the user provided outputs
    // that are obviously invalid. Still, we don't want to silently overflow.
    if (value.compareTo(NetworkParameters.MAX_MONEY) > 0)
      throw new PaymentProtocolException.InvalidOutputs("The outputs are way too big.");

    return value;
  }

  /**
   * For Stroem, network is stored in currency (testnet is seen as another currency/altcoin)
   *
   * @param merchantPaymentDetails
   * @return The network parameters calculated from the currency parameter
   */
  private NetworkParameters getParamsFromCurrency(StroemProtos.MerchantPaymentDetails merchantPaymentDetails) {
    if (!merchantPaymentDetails.hasCurrency()) {
      return MainNetParams.get();
    } else {
      String chosenNetwork = merchantPaymentDetails.getCurrency();
      if (lookForMainNetworkName(chosenNetwork)) {
        return MainNetParams.get();
      } else {
        return NetworkParameters.fromPmtProtocolID(merchantPaymentDetails.getCurrency());
      }
    }
  }

  // We are non strict when reading the currency variable (many names will find the main Bitcoin network)
  private boolean lookForMainNetworkName(String choosenNetwork) {
    return "BTC".equals(choosenNetwork) || "XBT".equals(choosenNetwork) || "main".equals(choosenNetwork);
  }

  /**
   * Will extract the MerchantPaymentDetails object from a binary string in the stroemData field of
   * the PaymentDetails object
   *
   * Note: Hopefully our "stroemData" field will be accepted as an extension of the payment protocol later on.
   *
   * @param stroemDataBytes
   * @return The Stroem specific MerchantPaymentDetails object
   * @throws IOException
   * @throws PaymentProtocolException
   */
  private StroemProtos.MerchantPaymentDetails getMerchantPaymentDetailsFromBytes(ByteString stroemDataBytes) throws IOException, PaymentProtocolException {
    return JavaToScalaBridge.buildMerchantPaymentDetial(stroemDataBytes.toByteArray());
  }

  /** Returns the value of pkiVerificationData or null if it wasn't verified at construction time. */
  @Nullable public PaymentProtocol.PkiVerificationData verifyPki() {
    return pkiVerificationData;
  }

  /** Gets the params as read from the PaymentRequest.network field: main is the default if missing. */
  public NetworkParameters getNetworkParameters() {
    return params;
  }

  /**
   * Returns the protobuf that this object was instantiated with.
   *
   *  @return Note: this is NOT the same as Protos.PaymentRequest object
   */
  public StroemPpProtos.PaymentRequest getPaymentRequest() {
    return paymentRequest;
  }

  /**
   * Will transform the PaymentRequest so it will have the same type as the standard PaymentRequest
   *
   * @return The transformed Protos.PaymentRequest object
   */
  public Protos.PaymentRequest getPaymentRequestNormal() throws InvalidProtocolBufferException {
    byte[] bytes = paymentRequest.toByteArray();
    ByteString byteString = ByteString.copyFrom(bytes);
    return Protos.PaymentRequest.parseFrom(byteString);
  }

  /** Returns the protobuf that describes the payment to be made. */
  public StroemProtos.MerchantPaymentDetails getMerchantPaymentDetails() {
    return merchantPaymentDetails;
  }

  public URI getMerchantUri() {
    return merchantUri;
  }

  /**
   * @return The top-level domain and second-level domain, ex; "strawpay.com"
   */
  public String getMerchantBaseDomainName() {
    return StroemUriUtil.getBaseDomainNameFromUri(merchantUri);
  }

  public String getIssuerName() {
    return issuerName;
  }
}