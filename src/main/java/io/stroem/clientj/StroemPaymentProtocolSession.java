package io.stroem.clientj;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.InvalidProtocolBufferException;
import org.bitcoin.protocols.payments.Protos;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TrustStoreLoader;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.protocols.payments.PaymentProtocolException;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
//TODO:Olle This is not ready!
public class StroemPaymentProtocolSession {

  private static final Logger log = LoggerFactory.getLogger(StroemPaymentProtocolSession.class);

  private static ListeningExecutorService executor = Threading.THREAD_POOL;
  private NetworkParameters params;
  private final TrustStoreLoader trustStoreLoader;
  private Protos.PaymentRequest paymentRequest;
  private Protos.PaymentDetails paymentDetails;
  private Coin totalValue = Coin.ZERO;

  /**
   * Stores the calculated PKI verification data, or null if none is available.
   * Only valid after the session is created with the verifyPki parameter set to true.
   */
  @Nullable public final PaymentProtocol.PkiVerificationData pkiVerificationData;

  /**
   * Returns a future that will be notified with a StroemPaymentProtocolSession object after it is fetched using the provided uri.
   * uri is a BIP-72-style BitcoinURI object that specifies where the {@link Protos.PaymentRequest} object may
   * be fetched in the r= parameter.
   *
   * If verifyPki is specified and the payment request object specifies a PKI method, then the system trust store will
   * be used to verify the signature provided by the payment request. An exception is thrown by the future if the
   * signature cannot be verified.
   */
  public static ListenableFuture<StroemPaymentProtocolSession> createFromBitcoinUri(final BitcoinURI uri)
      throws PaymentProtocolException {
    String url = uri.getPaymentRequestUrl();
    if (url == null)
      throw new PaymentProtocolException.InvalidPaymentRequestURL("No payment request URL (r= parameter) in BitcoinURI " + uri);
    try {
      return fetchPaymentRequest(new URI(url));
    } catch (URISyntaxException e) {
      throw new PaymentProtocolException.InvalidPaymentRequestURL(e);
    }
  }

  /**
   * Returns a future that will be notified with a StroemPaymentProtocolSession object after it is fetched using the provided url.
   * url is an address where the {@link Protos.PaymentRequest} object may be fetched.
   * If the payment request object specifies a PKI method, then the system trust store will
   * be used to verify the signature provided by the payment request. An exception is thrown by the future if the
   * signature cannot be verified.
   */
  public static ListenableFuture<StroemPaymentProtocolSession> createFromUrl(final String url)
      throws PaymentProtocolException {
    if (url == null)
      throw new PaymentProtocolException.InvalidPaymentRequestURL("null paymentRequestUrl");
    try {
      return fetchPaymentRequest(new URI(url));
    } catch(URISyntaxException e) {
      throw new PaymentProtocolException.InvalidPaymentRequestURL(e);
    }
  }

  private static ListenableFuture<StroemPaymentProtocolSession> fetchPaymentRequest(final URI uri) {
    return executor.submit(new Callable<StroemPaymentProtocolSession>() {
      @Override
      public StroemPaymentProtocolSession call() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
        connection.setRequestProperty("Accept", PaymentProtocol.MIMETYPE_PAYMENTREQUEST);
        connection.setUseCaches(false);
        Protos.PaymentRequest paymentRequest = Protos.PaymentRequest.parseFrom(connection.getInputStream());
        return new StroemPaymentProtocolSession(paymentRequest);
      }
    });
  }

  /**
   * Creates a StroemPaymentProtocolSession from the provided {@link Protos.PaymentRequest}.
   * Verifies PKI by default.
   */
  public StroemPaymentProtocolSession(Protos.PaymentRequest request) throws PaymentProtocolException {
    this.trustStoreLoader = new TrustStoreLoader.DefaultTrustStoreLoader();
    parsePaymentRequest(request);
    pkiVerificationData = null;
  }

  /**
   * Returns the outputs of the payment request.
   */
  public List<PaymentProtocol.Output> getOutputs() {
    List<PaymentProtocol.Output> outputs = new ArrayList<PaymentProtocol.Output>(paymentDetails.getOutputsCount());
    for (Protos.Output output : paymentDetails.getOutputsList()) {
      Coin amount = output.hasAmount() ? Coin.valueOf(output.getAmount()) : null;
      outputs.add(new PaymentProtocol.Output(amount, output.getScript().toByteArray()));
    }
    return outputs;
  }

  /**
   * Returns the memo included by the merchant in the payment request, or null if not found.
   */
  @Nullable public String getMemo() {
    if (paymentDetails.hasMemo())
      return paymentDetails.getMemo();
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
   */
  public Date getDate() {
    return new Date(paymentDetails.getTime() * 1000);
  }

  /**
   * Returns the expires time of the payment request, or null if none.
   */
  @Nullable public Date getExpires() {
    if (paymentDetails.hasExpires())
      return new Date(paymentDetails.getExpires() * 1000);
    else
      return null;
  }

  /**
   * This should always be called before attempting to call sendPayment.
   */
  public boolean isExpired() {
    return paymentDetails.hasExpires() && System.currentTimeMillis() / 1000L > paymentDetails.getExpires();
  }

  /**
   * Returns the payment url where the Payment message should be sent.
   * Returns null if no payment url was provided in the PaymentRequest.
   */
  public @Nullable String getPaymentUrl() {
    if (paymentDetails.hasPaymentUrl())
      return paymentDetails.getPaymentUrl();
    return null;
  }

  /**
   * Returns the merchant data included by the merchant in the payment request, or null if none.
   */
  @Nullable public byte[] getMerchantData() {
    if (paymentDetails.hasMerchantData())
      return paymentDetails.getMerchantData().toByteArray();
    else
      return null;
  }

  /**
   * Returns a {@link org.bitcoinj.core.Wallet.SendRequest} suitable for broadcasting to the network.
   */
  public Wallet.SendRequest getSendRequest() {
    Transaction tx = new Transaction(params);
    for (Protos.Output output : paymentDetails.getOutputsList())
      tx.addOutput(new TransactionOutput(params, tx, Coin.valueOf(output.getAmount()), output.getScript().toByteArray()));
    return Wallet.SendRequest.forTx(tx).fromPaymentDetails(paymentDetails);
  }

  /**
   * Generates a Payment message and sends the payment to the merchant who sent the PaymentRequest.
   * Provide transactions built by the wallet.
   * NOTE: This does not broadcast the transactions to the bitcoin network, it merely sends a Payment message to the
   * merchant confirming the payment.
   * Returns an object wrapping PaymentACK once received.
   * If the PaymentRequest did not specify a payment_url, returns null and does nothing.
   * @param txns list of transactions to be included with the Payment message.
   * @param refundAddr will be used by the merchant to send money back if there was a problem.
   * @param memo is a message to include in the payment message sent to the merchant.
   */
  public @Nullable ListenableFuture<PaymentProtocol.Ack> sendPayment(List<Transaction> txns, @Nullable Address refundAddr, @Nullable String memo)
      throws PaymentProtocolException, VerificationException, IOException {
    Protos.Payment payment = getPayment(txns, refundAddr, memo);
    if (payment == null)
      return null;
    if (isExpired())
      throw new PaymentProtocolException.Expired("PaymentRequest is expired");
    URL url;
    try {
      url = new URL(paymentDetails.getPaymentUrl());
    } catch (MalformedURLException e) {
      throw new PaymentProtocolException.InvalidPaymentURL(e);
    }
    return sendPayment(url, payment);
  }

  /**
   * Generates a Payment message based on the information in the PaymentRequest.
   * Provide transactions built by the wallet.
   * If the PaymentRequest did not specify a payment_url, returns null.
   * @param txns list of transactions to be included with the Payment message.
   * @param refundAddr will be used by the merchant to send money back if there was a problem.
   * @param memo is a message to include in the payment message sent to the merchant.
   */
  public @Nullable Protos.Payment getPayment(List<Transaction> txns, @Nullable Address refundAddr, @Nullable String memo)
      throws IOException, PaymentProtocolException.InvalidNetwork {
    if (paymentDetails.hasPaymentUrl()) {
      for (Transaction tx : txns)
        if (!tx.getParams().equals(params))
          throw new PaymentProtocolException.InvalidNetwork(params.getPaymentProtocolId());
      return PaymentProtocol.createPaymentMessage(txns, totalValue, refundAddr, memo, getMerchantData());
    } else {
      return null;
    }
  }

  @VisibleForTesting
  protected ListenableFuture<PaymentProtocol.Ack> sendPayment(final URL url, final Protos.Payment payment) {
    return executor.submit(new Callable<PaymentProtocol.Ack>() {
      @Override
      public PaymentProtocol.Ack call() throws Exception {

        HttpURLConnection connectionToUse;

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connectionToUse = connection;
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", PaymentProtocol.MIMETYPE_PAYMENT);
        connection.setRequestProperty("Accept", PaymentProtocol.MIMETYPE_PAYMENTACK);
        connection.setRequestProperty("Content-Length", Integer.toString(payment.getSerializedSize()));
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(true);

        // ALICE - Allow sending to localhost even if the SSL cert is incompatible - for testing.
        if (connection instanceof HttpsURLConnection && "localhost".equals(url.getHost())) {
          log.debug("Allowing all SSL connections to 'localhost'");
          HttpsURLConnection conn1 = (HttpsURLConnection) url.openConnection();
          conn1.setHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
              return true;
            }
          });
          connectionToUse = conn1;

          conn1.setRequestMethod("POST");
          conn1.setRequestProperty("Content-Type", PaymentProtocol.MIMETYPE_PAYMENT);
          conn1.setRequestProperty("Accept", PaymentProtocol.MIMETYPE_PAYMENTACK);
          conn1.setRequestProperty("Content-Length", Integer.toString(payment.getSerializedSize()));
          conn1.setUseCaches(false);
          conn1.setDoInput(true);
          conn1.setDoOutput(true);
        }

        // Send request.
        DataOutputStream outStream = new DataOutputStream(connectionToUse.getOutputStream());
        payment.writeTo(outStream);
        outStream.flush();
        outStream.close();

        // Get response.
        Protos.PaymentACK paymentAck = Protos.PaymentACK.parseFrom(connectionToUse.getInputStream());
        return PaymentProtocol.parsePaymentAck(paymentAck);
      }
    });
  }

  private void parsePaymentRequest(Protos.PaymentRequest request) throws PaymentProtocolException {
    try {
      if (request == null)
        throw new PaymentProtocolException("request cannot be null");
      if (request.getPaymentDetailsVersion() != 1)
        throw new PaymentProtocolException.InvalidVersion("Version 1 required. Received version " + request.getPaymentDetailsVersion());
      paymentRequest = request;
      if (!request.hasSerializedPaymentDetails())
        throw new PaymentProtocolException("No PaymentDetails");
      paymentDetails = Protos.PaymentDetails.newBuilder().mergeFrom(request.getSerializedPaymentDetails()).build();
      if (paymentDetails == null)
        throw new PaymentProtocolException("Invalid PaymentDetails");
      if (!paymentDetails.hasNetwork())
        params = MainNetParams.get();
      else
        params = NetworkParameters.fromPmtProtocolID(paymentDetails.getNetwork());
      if (params == null)
        throw new PaymentProtocolException.InvalidNetwork("Invalid network " + paymentDetails.getNetwork());
      if (paymentDetails.getOutputsCount() < 1)
        throw new PaymentProtocolException.InvalidOutputs("No outputs");
      for (Protos.Output output : paymentDetails.getOutputsList()) {
        if (output.hasAmount())
          totalValue = totalValue.add(Coin.valueOf(output.getAmount()));
      }
      // This won't ever happen in practice. It would only happen if the user provided outputs
      // that are obviously invalid. Still, we don't want to silently overflow.
      if (totalValue.compareTo(NetworkParameters.MAX_MONEY) > 0)
        throw new PaymentProtocolException.InvalidOutputs("The outputs are way too big.");
    } catch (InvalidProtocolBufferException e) {
      throw new PaymentProtocolException(e);
    }
  }

  /** Returns the value of pkiVerificationData or null if it wasn't verified at construction time. */
  @Nullable public PaymentProtocol.PkiVerificationData verifyPki() {
    return pkiVerificationData;
  }

  /** Gets the params as read from the PaymentRequest.network field: main is the default if missing. */
  public NetworkParameters getNetworkParameters() {
    return params;
  }

  /** Returns the protobuf that this object was instantiated with. */
  public Protos.PaymentRequest getPaymentRequest() {
    return paymentRequest;
  }

  /** Returns the protobuf that describes the payment to be made. */
  public Protos.PaymentDetails getPaymentDetails() {
    return paymentDetails;
  }
}