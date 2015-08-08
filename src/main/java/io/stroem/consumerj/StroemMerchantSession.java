package io.stroem.consumerj;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import io.stroem.api.Messages;
import io.stroem.consumerj.issuer.*;
import io.stroem.consumerj.merchant.*;
import io.stroem.javaapi.JavaToScalaBridge;
import io.stroem.proto.StroemProtos;

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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Date;
import java.util.concurrent.Callable;

/**
 * <p>Represents a Stroem payment protocol session to the merchant server. The two most important methods are:
 * </p>
 *
 * <ol>
 *     <li>createFromStroemUri() - Will connect to the merchant server and fetch an offer back.</li>
 *     <li>sendPromissoryNoteToMerchant() - Will send the Note to the merchant and get a receipt back.</li>
 * </ol>
 *
 * <p>This class corresponds to the {@link org.bitcoinj.protocols.payments.PaymentSession} class for
 * BIP70 payment protocol in bitcoinj, and the reason we don't extend
 * {@link org.bitcoinj.protocols.payments.PaymentSession} directly is that we don't have a corresponding getPayment()
 * (Stroem will not send a PAYMENT to the merchant, but a promissory note).
 * </p>
 *
 * <p>So, instead we extend {@link PaymentProtocolSessionCore}, representing the fields that
 * are common for both these classes.
 * (Hopefully, {@link org.bitcoinj.protocols.payments.PaymentSession} will use the same base class later on)
 * </p>
 *
 */
public class StroemMerchantSession extends PaymentProtocolSessionCore {

  private static final Logger log = LoggerFactory.getLogger(StroemMerchantSession.class);


  public static final String ISSUER_NOT_ACCEPTED_CODE = "unknown issuer:";


  public static final String MEMO_STROEM_SIGNAL = "STROEM";
  private boolean stroem = false;

  private static ListeningExecutorService executor = Threading.THREAD_POOL;
  private NetworkParameters params;
  private final TrustStoreLoader trustStoreLoader; // Not used as of now

  // Stroem specific fields
  private StroemProtos.MerchantPaymentDetails merchantPaymentDetails;
  private byte[] stroemMessageData;
  private URI merchantUri;
  private String issuerName;


  /**
   * Stores the calculated PKI verification data, or null if none is available.
   * Only valid after the session is created with the verifyPki parameter set to true.
   */
  @Nullable public final PaymentProtocol.PkiVerificationData pkiVerificationData;

  /**
   * Returns a future that will be notified with a StroemMerchantSession object after it is fetched using the provided uri.
   * uri is a BIP-72-style Stroem URI object that specifies where the {@link Protos.PaymentRequest} object may
   * be fetched, usually in the r= parameter.
   *
   * An exception is thrown by the future if the signature cannot be verified.
   *
   * Note: After the future has completed you must call isStroem() to verify that this really is a stroem message.
   *       It is perfectly valid for the merchant to reject the issuer, and respond with a normal BIP70 PaymentDetail object.
   *
   * Note: PKI method cannot be specified yet.
   */
  public static ListenableFuture<StroemMerchantOfferResult> createFromStroemUri(final StroemUri stroemUri) {
      return fetchPaymentRequest(stroemUri, stroemUri.getIssuerName());
  }

  private static ListenableFuture<StroemMerchantOfferResult> fetchPaymentRequest(final StroemUri stroemUri, final String issuerName)  {
    return executor.submit(new Callable<StroemMerchantOfferResult>() {
      @Override
      public StroemMerchantOfferResult call() throws Exception {
        return doGetOffer(stroemUri, issuerName);
      }
    });
  }

  private static StroemMerchantOfferResult doGetOffer(StroemUri stroemUri, String issuerName) {

    String uriStr = null;
    try {
      uriStr = stroemUri.getPaymentRequestUrl();
    } catch (IllegalArgumentException e) {
      log.warn(e.getMessage());
      return new StroemMerchantOfferResult(StroemMerchantOfferResult.StatusCode.INVALID_STROEM_URI, e.getMessage());
    }

    log.debug("Final Stroem Uri: " + uriStr);
    URI uri = null;
    try {
      uri = new URI(uriStr);
    } catch (URISyntaxException e) {
      log.warn(e.getReason(), e);
      return new StroemMerchantOfferResult(StroemMerchantOfferResult.StatusCode.INVALID_URI, e.getReason());
    }

    HttpURLConnection connection = null;
    try {
      connection = HttpConfigurator.buildHttpURLConnectionForGettingOffer(uri);
    } catch (IOException e) {
      log.error("ERROR: " + e.getMessage(), e);
      HttpConfigurator.closeAll(connection);
      return new StroemMerchantOfferResult(StroemMerchantOfferResult.StatusCode.ERROR, e.getMessage());
    }

    int responseCode;
    try {
      connection.connect();
      responseCode = connection.getResponseCode();
    } catch (IOException e) {
      log.warn("Error: Merchant server does not respond on the URI: " + e.getMessage(), e);
      HttpConfigurator.closeAll(connection);
      return new StroemMerchantOfferResult(StroemMerchantOfferResult.StatusCode.MERCHANT_DOWN, e.getMessage());
    }

    if (responseCode == HttpURLConnection.HTTP_OK) {
      InputStream is = null;
      try {
        Protos.PaymentRequest paymentRequest = Protos.PaymentRequest.parseFrom(connection.getInputStream());
        log.debug("Merchant responded with a (Stroem) payment request ");
        return new StroemMerchantOfferResult(new StroemMerchantSession(paymentRequest, uri, issuerName));
      } catch (PaymentProtocolException e) {
        log.warn("Wrong Stroem verison: " + e.getMessage(), e);
        return new StroemMerchantOfferResult(StroemMerchantOfferResult.StatusCode.ERROR, e.getMessage());
      } catch (InvalidProtocolBufferException e) {
        log.warn("Error: protobuf incorrect formatted: " + e.getMessage(), e);
        return new StroemMerchantOfferResult(StroemMerchantOfferResult.StatusCode.ERROR, e.getMessage());
      } catch (IOException e) {
        log.error("ERROR: " + e.getMessage(), e);
        return new StroemMerchantOfferResult(StroemMerchantOfferResult.StatusCode.ERROR, e.getMessage());
      } finally {
        HttpConfigurator.closeAll(is, connection);
      }
    } else {
      String str = connection.getErrorStream().toString();
      if (str != null && str.startsWith(ISSUER_NOT_ACCEPTED_CODE)) {
        String msg = "Merchant doesn't accept the given issuer: " + issuerName;
        log.info(msg);
        HttpConfigurator.closeAll(connection);
        return new StroemMerchantOfferResult(StroemMerchantOfferResult.StatusCode.WRONG_ISSUER, msg);
      } else {
        String responseMessage = "Unable to read server error message";
        try {
          responseMessage = connection.getResponseMessage();
          log.info("Merchant server respond with http error {}: {}", responseCode, responseMessage);
        } catch (IOException e) {
          log.error("Could not parse response =" + e.getMessage(), e);
        }
        HttpConfigurator.closeAll(connection);
        return new StroemMerchantOfferResult(StroemMerchantOfferResult.StatusCode.MERCHANT_RESPONDS_WITH_ERROR_CODE, responseCode + " - " + responseMessage);
      }
    }
  }

  /**
   * Creates a StroemMerchantSession from the provided {@link Protos.PaymentRequest}.
   *
   * Note: You must call isStroem() on the instance to verify that this really is a stroem message.
   *
   * @throws PaymentProtocolException - Thrown when something is wrong with the message content
   */
  public StroemMerchantSession(Protos.PaymentRequest request, URI merchantUri, String issuerName) throws PaymentProtocolException {
    super(request);
    this.trustStoreLoader = new TrustStoreLoader.DefaultTrustStoreLoader();
    this.merchantUri = merchantUri;
    this.issuerName = issuerName;
    parsePaymentRequest(request);
    pkiVerificationData = null; // TODO:Olle Do we need verification? // PaymentProtocol.verifyPaymentRequestPki(request, this.trustStoreLoader.getKeyStore());
  }


  /**
   * Sends the negotiated note to the merchant.
   *
   * @param stroemMessage to send to the merchant
   */
  public ListenableFuture<StroemMerchantReceiptResult> sendPromissoryNoteToMerchant(final StroemProtos.StroemMessage stroemMessage) {
    return executor.submit(new Callable<StroemMerchantReceiptResult>() {
      @Override
      public StroemMerchantReceiptResult call() throws Exception {
        return doSend(stroemMessage);
      }
    });
  }

  private StroemMerchantReceiptResult doSend(StroemProtos.StroemMessage stroemMessage) {

    log.debug("Merchant payment Url: " + getPaymentUrl());
    URI uri = null;
    try {
      uri = new URI(getPaymentUrl());
    } catch (URISyntaxException e) {
      log.warn(e.getReason(), e);
      return new StroemMerchantReceiptResult(StroemMerchantReceiptResult.StatusCode.INVALID_URI, e.getReason());
    }

    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) HttpConfigurator.buildHttpURLConnectionForPostingTheNote(uri, stroemMessage);
    } catch (IOException e) {
      log.error("Bad http configuration =" + e.getMessage(), e);
      return new StroemMerchantReceiptResult(StroemMerchantReceiptResult.StatusCode.ERROR, e.getMessage());
    }

    OutputStream os = null;
    int responseCode;
    try {
      connection.connect();
      os = connection.getOutputStream();
      stroemMessage.writeTo(os);
      os.flush();
      responseCode = connection.getResponseCode();
    } catch (IOException e) {
      log.warn("Error: Merchant server does not respond on the URI: " + e.getMessage(), e);
      HttpConfigurator.closeAll(os, null, connection);
      return new StroemMerchantReceiptResult(StroemMerchantReceiptResult.StatusCode.MERCHANT_DOWN, e.getMessage());
    }

    InputStream is = null;
    if (responseCode == HttpURLConnection.HTTP_OK) {
      try {
        is = connection.getInputStream();
        final StroemProtos.StroemMessage stroemMessageReply = StroemProtos.StroemMessage.parseFrom(is);

        // Convert it to correct type
        io.stroem.api.PaymentReceipt paymentReceipt = Messages.parsePaymentReceipt(stroemMessageReply).get();
        // Cast it to Java
        StroemPaymentReceipt stroemPaymentReceipt = new StroemPaymentReceipt(
                new StroemPaymentHash(paymentReceipt.paymentHash().value()),
                new Date(paymentReceipt.signedAt().getMillis()),
                paymentReceipt.signature().toCanonicalised());

        return new StroemMerchantReceiptResult(stroemPaymentReceipt);
      } catch (IOException e) {
        log.error("Could not parse response =" + e.getMessage(), e);
        return new StroemMerchantReceiptResult(StroemMerchantReceiptResult.StatusCode.ERROR, e.getMessage());
      } finally {
        HttpConfigurator.closeAll(os, is, connection);
      }

    } else {
      String responseMessage = "Unable to read server error message";
      try {
        responseMessage = connection.getResponseMessage();
        log.info("got http error {}: {}", responseCode, responseMessage);
      } catch (IOException e) {
        log.error("Could not parse response =" + e.getMessage(), e);
      }
      HttpConfigurator.closeAll(os, null, connection);
      return new StroemMerchantReceiptResult(StroemMerchantReceiptResult.StatusCode.MERCHANT_RESPONDS_WITH_ERROR_CODE, responseCode + " - " + responseMessage);
    }
  }



  private void parsePaymentRequest(Protos.PaymentRequest request) throws PaymentProtocolException {

    Protos.PaymentDetails paymentDetails;

    try {
      if (request == null)
        throw new PaymentProtocolException("request cannot be null");
      if (request.getPaymentDetailsVersion() != 1)
        throw new PaymentProtocolException.InvalidVersion("Version 1 required. Received version " + request.getPaymentDetailsVersion());

      log.debug("Begin parsing paymentRequest");
      // Get the merchant details from the request
      if (!request.hasSerializedPaymentDetails())
        throw new PaymentProtocolException("No PaymentDetails");

      ByteString paymentDetailsBytes = request.getSerializedPaymentDetails();
      paymentDetails = Protos.PaymentDetails.parseFrom(paymentDetailsBytes);

      // Check the memo field
      if (paymentDetails.hasMemo() && MEMO_STROEM_SIGNAL.equals(paymentDetails.getMemo())) {
        log.debug("this is a stroem message");
        stroem = true;
        parseStroemMessage(paymentDetails);
      } else {
        stroem = false;
        log.debug("This was not a stroem message (since its MEMO field didn't say so)");
      }
    } catch (InvalidProtocolBufferException e) {
      throw new PaymentProtocolException(e);
    } catch (IOException e) {
      throw new PaymentProtocolException(e);
    }
  }

  private void parseStroemMessage(Protos.PaymentDetails paymentDetails) throws PaymentProtocolException, IOException {

    Coin totalValue;
    Date creationDate;
    Date expiryDate;
    String memo;
    String paymentUrl;
    byte[] merchantData;

    ByteString stroemMessageDataBytes = null;
    if (paymentDetails.hasMerchantData()) {
      stroemMessageDataBytes = paymentDetails.getMerchantData();
    } else {
      throw new RuntimeException("At this point the payment details must have stroem message in merchant data");
    }
    stroemMessageData = stroemMessageDataBytes.toByteArray();
    merchantData = stroemMessageData;

    merchantPaymentDetails = getMerchantPaymentDetailsFromBytes(stroemMessageDataBytes);
    if (merchantPaymentDetails == null)
      throw new PaymentProtocolException("Invalid PaymentDetails");

    // Get the params from the currency field
    params = getParamsFromCurrency(merchantPaymentDetails);
    if (params == null)
      throw new PaymentProtocolException.InvalidNetwork("Invalid currency " + merchantPaymentDetails.getCurrency());

    // ===================================
    log.debug("Get common values from PaymentDetails");
    creationDate = new Date(paymentDetails.getTime() * 1000);

    // Get expires (getting it from the main PaymentDetails object)
    if (paymentDetails.hasExpires())
      expiryDate = new Date(paymentDetails.getExpires() * 1000L);
    else
      expiryDate = null;

    // ===================================
    log.debug("Get common values from MerchantPaymentDetails");

    if (merchantPaymentDetails.hasDisplayText())
      memo = merchantPaymentDetails.getDisplayText();
    else
      memo = null;

    if (paymentDetails.hasPaymentUrl())
      paymentUrl = paymentDetails.getPaymentUrl();
    else
      paymentUrl = null;

    // Total amount
    if (!merchantPaymentDetails.hasAmount()) {
      new StroemPaymentProtocolException("There must be an amount to pay");
    }

    totalValue = Coin.valueOf(merchantPaymentDetails.getAmount());

    // This won't ever happen in practice. It would only happen if the user provided outputs
    // that are obviously invalid. Still, we don't want to silently overflow.
    if (totalValue.compareTo(NetworkParameters.MAX_MONEY) > 0)
      throw new PaymentProtocolException.InvalidOutputs("The outputs are way too big.");


    // ===================================
    // Intitialize the common values
    init(totalValue,
            creationDate,
            expiryDate,
            memo,
            paymentUrl,
            paymentDetails,
            merchantData);

    log.debug("End parsing of payment request");

  }

  /**
   * @return True if this indeed is a stroem session. Should be checked on every new instance!
   */
  public boolean isStroem() {
    return stroem;
  }

  /**
   * Returns the value of pkiVerificationData or null if it wasn't verified at construction time.
   */
  @Nullable public PaymentProtocol.PkiVerificationData verifyPki() {
    return pkiVerificationData;
  }

  /**
   * Gets the params as read from the PaymentRequest.network field: main is the default if missing.
   */
  public NetworkParameters getNetworkParameters() {
    return params;
  }

  // ========= Stroem specific methods ===========
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
   * Will extract the MerchantPaymentDetails object from a binary string in the stroemMessageData field of
   * the PaymentDetails object
   *
   * Note: Hopefully our "stroemMessageData" field will be accepted as an extension of the payment protocol later on.
   *
   * @param stroemDataBytes
   * @return The Stroem specific MerchantPaymentDetails object
   * @throws IOException
   * @throws PaymentProtocolException
   */
  private StroemProtos.MerchantPaymentDetails getMerchantPaymentDetailsFromBytes(ByteString stroemDataBytes) throws IOException, PaymentProtocolException {
    return JavaToScalaBridge.buildMerchantPaymentDetial(stroemDataBytes.toByteArray());
  }

  /**
   * @return MerchantPaymentDetails = the protobuf object for stroem specific data
   */
  public StroemProtos.MerchantPaymentDetails getMerchantPaymentDetails() {
    return merchantPaymentDetails;
  }

  /**
   * @return the URI we used for the inital contact with the merchant
   */
  public URI getMerchantUri() {
    return merchantUri;
  }

  /**
   * @return the top-level domain and second-level domain, ex; "strawpay.com"
   */
  public String getMerchantBaseDomainName() {
    return StroemUriUtil.getBaseDomainNameFromUri(merchantUri);
  }

  /**
   * @return the name of the issuer the consumer must use for this purchase
   */
  public String getIssuerName() {
    return issuerName;
  }

}