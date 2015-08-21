package io.stroem.consumerj;

import io.stroem.api.Messages;
import io.stroem.consumerj.issuer.*;
import io.stroem.promissorynote.PaymentInstrument;
import org.bitcoinj.core.*;
import org.bitcoinj.net.NioClient;
import org.bitcoinj.net.ProtobufParser;
import org.bitcoinj.protocols.channels.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.stroem.proto.StroemProtos;
import io.stroem.proto.StroemProtos.StroemMessage;
import io.stroem.javaapi.JavaToScalaBridge;

import com.google.protobuf.ByteString;

import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import org.spongycastle.math.ec.ECPoint;
import org.spongycastle.crypto.params.KeyParameter;

import static com.google.common.base.Preconditions.checkState;

/**
 * Runs the stroem protocol over a raw TCP socket using NIO, standalone.
 * Used to connect to an Stroem Issuer to buy Promissory Notes over a payment channel.
 */
public class StroemIssuerConnection {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(StroemIssuerConnection.class);

  public static final int STROEM_PORT = 4399;
  public static final int CLIENT_STROEM_VERSION = 1;
  public static final String CURRENCY = "BTC";

  private PaymentChannelClient paymentChannelClient;

  private StroemMessageReceiver stroemMessageReceiver;

  // Used to write messages to the socket
  private final ProtobufParser<StroemMessage> wireParser;

  // Holds the status of an initialization of the payment channel
  private final SettableFuture<StroemIssuerConnectionResult> channelOpenFuture = SettableFuture.create();
  // Holds the status of a settlement of the payment channel
  private SettableFuture<Void> settlementFuture = SettableFuture.create();
  // A general future used to detect errors
  private SettableFuture<Void> currentFuture = SettableFuture.create();

  // Indicates if a channel was created (i.e. did not previously exist)
  private boolean freshChannel = false;
  // True when we have entered the settling process (the settling will terminate the connection)
  private boolean settling = false;

  // Some intermediate member values (coming from the constructor)
  private final Wallet wallet;
  private Sha256Hash serverIdHash;
  private ECKey myKey;
  private Coin maxValue;
  @Nullable private StroemPaymentChannelConfiguration channelConfiguration;
  @Nullable private KeyParameter userKeySetup;

  // Temporary (state) variables
  private StroemStep stroemStep = StroemStep.START;

  /**
   *  Tell it to terminate the payment relationship and thus broadcast the micropayment transactions. We will
   *  resume control in destroyConnection below.
   */
  public synchronized void settlePaymentChannel() {
    settling = true;
    currentFuture = settlementFuture = SettableFuture.create();
    if (paymentChannelClient == null) {
      // Have to connect first.
      throw new IllegalStateException("Cannot settle paymnet channel if channel doesn't have a PaymentChannelClient");
    }
    paymentChannelClient.settle();
  }

  /**
   * Call this method to get the issuer's Entity after the channel has been initiated.
   */
  @Nullable
  public StroemEntity getIssuerEntity() {
    return this.stroemMessageReceiver.getIssuerGivenEntity();
  }


  /**
   * Attempts to:
   * 1. open a new connection to an open a payment channel over the Stroem protocol (using the given serverId), or
   * 2. create a new Stroem payment channel.
   *
   * Blocking until the connection is open.
   *
   * Use this constructor if you are building a normal wallet (this is the recommended constructor)
   *
   * @param configuration Meta data for the channel.
   * @param socketTimeoutSeconds The connection timeout and read timeout during initialization. This should be large enough
   *                       to accommodate ECDSA signature operations and network latency.
   * @param wallet The wallet which will be paid from, and where completed transactions will be committed.
   *               Must already have a {@link org.bitcoinj.protocols.channels.StoredPaymentChannelClientStates} object in its extensions set.
   * @param myKey A freshly generated keypair used for the multisig contract and refund output.
   * @param userKeySetup Key derived from a user password, used to decrypt myKey, if it is encrypted, during setup.
   *
   */
  public StroemIssuerConnection(StroemPaymentChannelConfiguration configuration, int socketTimeoutSeconds, Wallet wallet,
                                ECKey myKey, @Nullable KeyParameter userKeySetup
  )  {
    this(configuration.getIssuerHost(), configuration.getStroemId(), socketTimeoutSeconds, configuration.getTimeoutSeconds(),
            wallet, myKey, userKeySetup, configuration.getMaxValue());
    this.channelConfiguration = configuration;
  }

  /**
   * Attempts to:
   * 1. open a new connection to an open a payment channel over the Stroem protocol (using the given serverId), or
   * 2. create a new Stroem payment channel.
   *
   * Blocking until the connection is open.
   *
   * Use this constructor if you are building a normal wallet
   *
   * @param stroemIdSimple The host where the issuer server is listening, also the ID of the payment channel.
   * @param socketTimeoutSeconds The connection timeout and read timeout during initialization. This should be large enough
   *                       to accommodate ECDSA signature operations and network latency.
   * @param paymentChannelTimeoutSeconds How long the payment channel should stay open. Server not care about this value.
   * @param wallet The wallet which will be paid from, and where completed transactions will be committed.
   *               Must already have a {@link org.bitcoinj.protocols.channels.StoredPaymentChannelClientStates} object in its extensions set.
   * @param myKey A freshly generated keypair used for the multisig contract and refund output.
   * @param userKeySetup Key derived from a user password, used to decrypt myKey, if it is encrypted, during setup.
   * @param maxValue The maximum value this channel is allowed to request
   *
   */
  public StroemIssuerConnection(StroemIdSimple stroemIdSimple, int socketTimeoutSeconds, long paymentChannelTimeoutSeconds, Wallet wallet,
                                ECKey myKey, @Nullable KeyParameter userKeySetup, Coin maxValue
  )  {
    this(stroemIdSimple.getIssuerUriHost(), stroemIdSimple, socketTimeoutSeconds, paymentChannelTimeoutSeconds, wallet, myKey, userKeySetup, maxValue);
  }

  /**
   * Attempts to:
   * 1. open a new connection to an open a payment channel over the Stroem protocol (using the given serverId), or
   * 2. create a new Stroem payment channel.
   *
   * Blocking until the connection is open.
   *
   * Use this constructor if you have need of the "serverId" param.
   *
   * @param issuerHost The host where the issuer server is listening.
   * @param serverId A unique ID which is used to attempt reopening of an existing channel.
   * @param socketTimeoutSeconds The connection timeout and read timeout during initialization. This should be large enough
   *                       to accommodate ECDSA signature operations and network latency.
   * @param paymentChannelTimeoutSeconds How long the payment channel should stay open. Server not care about this value.
   * @param wallet The wallet which will be paid from, and where completed transactions will be committed.
   *               Must already have a {@link org.bitcoinj.protocols.channels.StoredPaymentChannelClientStates} object in its extensions set.
   * @param myKey A freshly generated keypair used for the multisig contract and refund output.
   * @param userKeySetup Key derived from a user password, used to decrypt myKey, if it is encrypted, during setup.
   * @param maxValue The maximum value this channel is allowed to request
   *
   */
  public StroemIssuerConnection(String issuerHost, StroemId serverId, int socketTimeoutSeconds, long paymentChannelTimeoutSeconds, Wallet wallet,
                                ECKey myKey, @Nullable KeyParameter userKeySetup, Coin maxValue
  ) {

    // Initiate some members
    this.wallet = wallet;
    this.serverIdHash = serverId.getRealPaymentChannelServerId();
    this.myKey = myKey;
    this.maxValue = maxValue;
    this.userKeySetup = userKeySetup;

    log.debug("1. Start to init TCP over NIO");

    // Handles messages going out on the network (Java objects -> Stroem protobuf)
    StroemPaymentChannelClientConnection stroemPaymentChannelClientConnection = new StroemPaymentChannelClientConnection(this, channelOpenFuture, settlementFuture, currentFuture,
            paymentChannelTimeoutSeconds);

    PaymentChannelClient.ClientConnection clientConnection = stroemPaymentChannelClientConnection;
    log.debug("2. client connection built");

    paymentChannelClient = new PaymentChannelClient(wallet, myKey, maxValue, serverIdHash, paymentChannelTimeoutSeconds, userKeySetup, clientConnection);
    stroemPaymentChannelClientConnection.setPaymentChannelClient(paymentChannelClient);
    log.debug("3. payment client built");

    stroemMessageReceiver = new StroemMessageReceiver(paymentChannelClient);
    log.debug("4. stroem message receiver built");

    // This listener handles messages coming in from network (Stroem protobuf -> java objects)
    ProtobufParser.Listener<StroemMessage> stroemMessageListener = buildStroemMessageListener();
    log.debug("5. stroem message listener built");

    StroemMessage defaultInstance = StroemMessage.getDefaultInstance();
    log.debug("6. default instance built");

    wireParser = new ProtobufParser<StroemMessage>(stroemMessageListener, defaultInstance, Short.MAX_VALUE, socketTimeoutSeconds*1000);
    stroemPaymentChannelClientConnection.setWireParser(wireParser);

    log.debug("Start NIO");
    InetSocketAddress inetSocketAddress = new InetSocketAddress(issuerHost, STROEM_PORT);
    // Initiate the outbound network connection. We don't need to keep this around. The wireParser object will handle
    // things from here on out.
    try {
      new NioClient(inetSocketAddress, wireParser, socketTimeoutSeconds * 1000);
      log.debug("Initation of TCP over NIO done");
    } catch (IOException e) {
      // Actually this is a bug: IOException is never thrown here
      throw new IllegalStateException("Now NioClient actually throws IOException (change this code)! Message: " + e.getMessage()); // This can't happen
    }
  }

  /*
   * (Stroem protobuf -> Java)
   * Returns a Listener, which handles messages coming from the network, in this case StroemMessage (protobuf).
   *
   */
  private ProtobufParser.Listener<StroemMessage> buildStroemMessageListener() {
    ProtobufParser.Listener<StroemMessage> stroemMessageListener = new ProtobufParser.Listener<StroemMessage>() {

      // The initStep field might change depending on the received message.
      @Override
      public void messageReceived(ProtobufParser<StroemMessage> handler, StroemMessage msg) {
        log.debug("callback - messageReceived - start");
        try {
          StroemStep newStroemStep = stroemMessageReceiver.receiveMessage(msg, stroemStep);
          if (newStroemStep != null) {
            setStroemStep(newStroemStep);
          }
        } catch (WrongStroemServerVersionException e) {
          // This happens before the payment channel has begun INITIATE.
          String errorMsg = "Incorrect server version: " + e.getMessage();
          log.warn(errorMsg);
          channelOpenFuture.set(new StroemIssuerConnectionResult(StroemIssuerConnectionResult.StatusCode.WRONG_ISSUER_STROEM_VERSION, errorMsg));
        } catch (InsufficientMoneyException e) {
          // We should only get this exception during INITIATE, so channelOpen wasn't called yet.
          String errorMsg = "Cannot create a channel since we do not have the money: " + e.getMessage();
          log.info(errorMsg);
          channelOpenFuture.set(new StroemIssuerConnectionResult(StroemIssuerConnectionResult.StatusCode.INSUFFICIENT_MONEY, errorMsg));
        } catch (StroemProtocolException e) {
          // (This could happen anytime)
          log.error("A Stroem protocol error occurred: " + e.getCode().name());
          if(!channelOpenFuture.isDone()) {
            channelOpenFuture.set(new StroemIssuerConnectionResult(e));
          }
          currentFuture.setException(e);
        }
      }

      @Override
      public void connectionOpen(ProtobufParser<StroemMessage> handler) {
        log.debug("callback - connectionOpen - start");
        if(stroemStep != StroemStep.START) {
          log.warn("When a TCP channel just opened the Stroem init step should not be " + stroemStep.name());
          setStroemStep(StroemStep.START);
        }

        // First thing to do is to send the Stroem Version
        StroemProtos.StroemClientVersion stroemVersionMsg = StroemProtos.StroemClientVersion.newBuilder()
            .setVersion(CLIENT_STROEM_VERSION).build();
        StroemMessage msg = StroemMessage.newBuilder()
            .setType(StroemMessage.MessageType.STROEM_CLIENT_VERSION)
            .setStroemClientVersion(stroemVersionMsg)
            .build();
        wireParser.write(msg);

        setStroemStep(StroemStep.WAITING_FOR_SERVER_STROM_VERSION);
        // Now we will wait for the issuer's response (see StroemMessageReceiver.receiveStroemVersion())
      }

      @Override
      public void connectionClosed(ProtobufParser<StroemMessage> handler) {
        log.debug("callback - connectionClosed - start");
        paymentChannelClient.connectionClosed();
        setStroemStep(StroemStep.CONNECTION_CLOSED);

        if(!channelOpenFuture.isDone()) {
          // If this happens when the channel opens we need to mark this as an error.
          channelOpenFuture.set(new StroemIssuerConnectionResult(StroemIssuerConnectionResult.StatusCode.TCP_SOCKET_CLOSED, "The TCP socket died"));
        }
      }
    };

    return stroemMessageListener;
  }






  /**
   * <p>Gets a future which returns this when the channel is successfully opened, or throws an exception if there is
   * an error before the channel has reached the open state.</p>
   *
   * <p>After this future completes successfully, you may call incrementPayment().
   */
  public ListenableFuture<StroemIssuerConnectionResult> getChannelOpenFuture() {
    return channelOpenFuture;
  }

  /**
   * Increments the total value of the payment channel, which we pay the issuer.
   * A. This method will call the issuer and pay the issuer using the payment channel.
   * B. Then it will extract the payment info from the issuer's ack, and use it to prepare the negotiation.
   * C. A tailor made Negotiator object will be returned, and the wallet developer is supposed to use it for negotiation.
   * D. When negotiation has been done, the wallet developer should send the new promissory note to the merchant.
   *
   * @param merchantPaymentDetailsBytes The raw data from the "stroem_message" field in the PaymentDetails protobuf message
   *                                    received from the merchant.
   * @param myTransactionKey - A (potentially new) key pair that will be used during this transaction. Must be used to sign the
   *                later.
   * @return StroemNegotiator - use this object to sign and negotiate the promissory note.
   * @throws ValueOutOfRangeException If the size is negative or would pay more than this channel's total value
   * @throws ExecutionException If the ack future is interrupted
   * @throws InterruptedException If the ack future is interrupted
   * @throws IllegalStateException If the channel has been closed or is not yet open
   *                               (see {@link StroemIssuerConnection#getChannelOpenFuture()} for the second)

   */
  public synchronized StroemNegotiator incrementPayment(
      byte[] merchantPaymentDetailsBytes,
      ECKey myTransactionKey
  ) throws ValueOutOfRangeException, ExecutionException,  InterruptedException {

    log.debug("1. Begin incrementPayment.");
    verifyStroemStateIsChannelOpen();
    setStroemStep(StroemStep.WAITING_FOR_PAYMENT_ACK);

    ECPoint myPublicKey = myTransactionKey.getPubKeyPoint();
    JavaToScalaBridge.PromissoryNoteRequestReturnBundle returnBundle = JavaToScalaBridge.buildPromissoryNoteRequestProto(merchantPaymentDetailsBytes, myPublicKey);

    log.debug("2. Verify that the merchant has the correct issuer public key.");
    StroemEntity realIssuerProtoEntity = this.stroemMessageReceiver.getIssuerGivenEntity();
    verifyIssuerEntity(realIssuerProtoEntity.getName(), realIssuerProtoEntity.getPublicKey(), returnBundle);

    StroemProtos.StroemMessage messageRequestProto = returnBundle.getPromissoryNoteRequestProto();
    Coin sizeFromMerchant = returnBundle.getAmount();

    log.debug("3. About to pay the issuer (do an incrementPayment call).");
    ListenableFuture<PaymentIncrementAck> ackFuture = paymentChannelClient.incrementPayment(sizeFromMerchant, messageRequestProto.toByteString(), this.userKeySetup);

    PaymentIncrementAck ack = ackFuture.get();
    log.debug("4. Ack received. ");
    setStroemStep(StroemStep.PAYMENT_DONE);

    log.debug("5. Prepare for negotiation. ");
    ByteString infoByteString = ack.getInfo();
    PaymentInstrument.PromissoryNote promissoryNote = JavaToScalaBridge.buildPromissoryNoteFromBytes(infoByteString);
    ECPoint merchantPublicKey = returnBundle.getMerchantPublicKey();
    final PaymentInstrument.PaymentInfo paymentInfo = Messages.displayTextToPaymentInfo(returnBundle.getDisplayText());
    PaymentInstrument.NegotiateInfo negotiateInfo = JavaToScalaBridge.validateForNegotiate(promissoryNote, myPublicKey, merchantPublicKey, paymentInfo.bytes());

    log.debug("6. End (return StroemNegotiator).");
    return new StroemNegotiator(negotiateInfo);
  }

  /**
   * Use this method to validate that the merchant has got the Issuer's latest public key
   * (pub key may change over time)
   *
   * @param realIssuerName
   * @param realIssuerPublicKey
   * @param returnBundle
   */
  private void verifyIssuerEntity(String realIssuerName, byte[] realIssuerPublicKey, JavaToScalaBridge.PromissoryNoteRequestReturnBundle returnBundle) {
    if (realIssuerName.equals(returnBundle.getIssuerName())) {
      byte[] publicKey = returnBundle.getIssuerPublicKey().getEncoded();
      if(Arrays.equals(publicKey, realIssuerPublicKey)) {
        return; // All OK. Do nothing
      } else {
        throw new IllegalStateException("Merchant's key is old, must contact the issuer to get the correct one");
      }
    } else {
      throw new IllegalStateException("Issuer from merchant (" + returnBundle.getIssuerName() + ") is not same as issuers name (" + realIssuerName + ")");
    }
  }

  private void verifyStroemStateIsChannelOpen() {
    switch (this.stroemStep) {
      case CONNECTION_OPEN:
        log.debug("This is the first payment on this TCP session");
        break;
      case PAYMENT_DONE:
        log.debug("Last payment is completed, OK to make a new payment.");
        break;
      case WAITING_FOR_PAYMENT_ACK:
        throw new IllegalStateException("The last payment has not yet been completed. Cannot start new."); // Should not be possible since synchronized
      case CONNECTION_CLOSED:
        throw new IllegalStateException("Cannot make payment on a closed channel");
      default:
        throw new IllegalStateException("Cannot make payments when the connection's state is: " + this.stroemStep);
    }
  }

  // Called from StroemPaymentChannelClientConnection
  public void setConnectionOpen(boolean freshChannel) {
    this.freshChannel = freshChannel;
    setStroemStep(StroemStep.CONNECTION_OPEN);
    StroemIssuerConnectionResult result = new StroemIssuerConnectionResult(StroemIssuerConnection.this);
    channelOpenFuture.set(result);
  }

  // Called from StroemPaymentChannelClientConnection
  public boolean isSetteling() {
    return settling;
  }

  /**
   * <p>Gets the {@link PaymentChannelClientState} object which stores the current state of the connection with the
   * server.</p>
   *
   * <p>Note that if you call any methods which update state directly the server will not be notified and channel
   * initialization logic in the connection may fail unexpectedly.</p>
   */
  public PaymentChannelClientState state() {
    return paymentChannelClient.state();
  }

  /**
   * Closes the connection, notifying the issuer it should settle the channel by broadcasting the most recent payment
   * transaction.
   */
  public void settle() {
    // Shutdown is a little complicated.
    //
    // This call will cause the CLOSE message to be written to the wire, and then the destroyConnection() method that
    // we defined above will be called, which in turn will call wireParser.closeConnection(), which in turn will invoke
    // NioClient.closeConnection(), which will then close the socket triggering interruption of the network
    // thread it had created. That causes the background thread to die, which on its way out calls
    // ProtobufParser.connectionClosed which invokes the connectionClosed method we defined above which in turn
    // then configures the open-future correctly and closes the state object. Phew!
    try {
      paymentChannelClient.settle();
    } catch (IllegalStateException e) {
      // Already closed...oh well
    }
  }

  private synchronized void setStroemStep(StroemStep ss) {
    log.debug("-- setting old state " + this.stroemStep + " to " + ss);
    this.stroemStep = ss;
  }

  /**
   * Use this method if you plan to store the StroemPaymentChannelConfiguration somewhere.
   *
   * @return the StroemPaymentChannelConfiguration used during creation, if present.
   */
  @Nullable
  public StroemPaymentChannelConfiguration getStroemPaymentChannelConfiguration() {
    return channelConfiguration;
  }

  /**
   * Disconnects the network connection but doesn't request the issuer to settle the channel first (literally just
   * unplugs the network socket and marks the stored channel state as inactive).
   */
  public void disconnectWithoutSettlement() {
    wireParser.closeConnection();
  }
}
