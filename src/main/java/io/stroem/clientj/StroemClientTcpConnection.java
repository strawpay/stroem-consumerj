package io.stroem.clientj;

import io.stroem.payments.epn.ECPaymentInstrumentBitcoinj;
import io.stroem.payments.epn.PaymentInstrument;
import org.bitcoinj.core.*;
import org.bitcoinj.net.NioClient;
import org.bitcoinj.net.ProtobufParser;
import org.bitcoinj.protocols.channels.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.InvalidProtocolBufferException;
import io.stroem.clientj.domain.StroemEntity;
import io.stroem.proto.Stroem;
import io.stroem.proto.Stroem.StroemMessage;

import com.google.protobuf.ByteString;

import io.stroem.clientj.domain.StroemPromissoryNote;
import org.bitcoin.paymentchannel.Protos;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.spongycastle.math.ec.ECPoint;
import org.spongycastle.crypto.params.KeyParameter;

import static com.google.common.base.Preconditions.checkState;

/**
 * A simple utility class that runs the stroem protocol over a raw TCP socket using NIO, standalone.
 */
public class StroemClientTcpConnection {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(StroemClientTcpConnection.class);

  public static final int STROEM_PORT = 4399;
  public static final int CLIENT_STROEM_VERSION = 1;
  public static final String CURRENCY = "BTC";
  public static final long SAFE_MARGIN_SECONDS = 60*59; // 59 minutes time difference between the client and server clocks is allowed.

  private PaymentChannelClient paymentChannelClient;

  private StroemMessageReceiver stroemMessageReceiver;

  // Used to write messages to the socket
  private final ProtobufParser<StroemMessage> wireParser;

  // Holds the status of an initialization of the payment channel
  private final SettableFuture<StroemClientTcpConnection> channelOpenFuture = SettableFuture.create();
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
  private String serverIdName;
  private ECKey myKey;
  private Coin maxValue;
  private long paymentChannelTimeoutSeconds;
  @Nullable private KeyParameter userKeySetup;

  // Temporary (state) variables
  private StroemStep stroemStep = StroemStep.START;

  private Sha256Hash makeServerId(String s) {
    return Sha256Hash.create(s.getBytes());
  }
  /**
   *  Tell it to terminate the payment relationship and thus broadcast the micropayment transactions. We will
   *  resume control in destroyConnection below.
   */
  public synchronized void settlePaymentChannel() {
    settling = true;
    currentFuture = settlementFuture = SettableFuture.create();
    if (paymentChannelClient == null) {
      // Have to connect first.
      initializePayments();
    }
    paymentChannelClient.settle();
  }

  /**
   * Attempts to open a new connection to and open a payment channel over the Stroem protocol, using the given serverId.
   * Blocking until the connection is open
   *
   * @param server The host where the server is listening.
   * @param socketTimeoutSeconds The connection timeout and read timeout during initialization. This should be large enough
   *                       to accommodate ECDSA signature operations and network latency.
   * @param paymentChannelTimeoutSeconds How long the payment channel should stay open. Server not care about this value.
   * @param wallet The wallet which will be paid from, and where completed transactions will be committed.
   *               Must already have a {@link StoredPaymentChannelClientStates} object in its extensions set.
   * @param myKey A freshly generated keypair used for the multisig contract and refund output.
   * @param userKeySetup Key derived from a user password, used to decrypt myKey, if it is encrypted, during setup.
   * @param maxValue The maximum value this channel is allowed to request
   * @param serverId A unique ID which is used to attempt reopening of an existing channel.
   *                 This must be unique to the server, and, if your application is exposing payment channels to some
   *                 API, this should also probably encompass some caller UID to avoid applications opening channels
   *                 which were created by others.
   *
   * @throws java.io.IOException if there's an issue using the network.
   * @throws ValueOutOfRangeException if the balance of wallet is lower than maxValue.
   */
  public StroemClientTcpConnection(String server, int socketTimeoutSeconds, long paymentChannelTimeoutSeconds, Wallet wallet, ECKey myKey,
                                   @Nullable KeyParameter userKeySetup, Coin maxValue, String serverId) throws IOException, ValueOutOfRangeException {

    // Initiate some members
    this.wallet = wallet;
    this.serverIdName = serverId;
    this.serverIdHash = StroemClientUtil.makeServerIdFromString(serverId);
    this.myKey = myKey;
    this.maxValue = maxValue;
    this.paymentChannelTimeoutSeconds = paymentChannelTimeoutSeconds;
    this.userKeySetup = userKeySetup;

    log.debug("1. Start to init TCP over NIO");

    // 1. Handles messages going out on the network (Java objects -> Stroem protobuf)
    PaymentChannelClient.ClientConnection clientConnection = buildPaymentChannelClientConnection();
    log.debug("2. client connection built");

    paymentChannelClient = new PaymentChannelClient(wallet, myKey, maxValue, serverIdHash, paymentChannelTimeoutSeconds, userKeySetup, clientConnection);
    log.debug("3. payment client built");

    stroemMessageReceiver = new StroemMessageReceiver(paymentChannelClient);
    log.debug("4. stroem message receiver built");

    // 2. This listener handles messages coming in from network (Stroem protobuf -> java objects)
    ProtobufParser.Listener<StroemMessage> stroemMessageListener = buildStroemMessageListener();
    log.debug("5. stroem message listener built");

    StroemMessage defaultInstance = StroemMessage.getDefaultInstance();
    log.debug("6. default instance built");

    wireParser = new ProtobufParser<StroemMessage>(stroemMessageListener, defaultInstance, Short.MAX_VALUE, socketTimeoutSeconds*1000);

    log.debug("Start NIO");
    InetSocketAddress inetSocketAddress = new InetSocketAddress(server, STROEM_PORT);
    // Initiate the outbound network connection. We don't need to keep this around. The wireParser object will handle
    // things from here on out.
    new NioClient(inetSocketAddress, wireParser, socketTimeoutSeconds * 1000);
    log.debug("Initation of TCP over NIO done");
  }

  /*
   * Will set up a paymentChannelClient.
   *
   * @return A future that will be used to determine when the channel has been set up correctly
   */
  private SettableFuture<StroemClientTcpConnection> initializePayments() {

    if (paymentChannelClient == null) {

      log.info("Init payments with server id: " + serverIdName);

      // Handles messages going out on the network.
      PaymentChannelClient.ClientConnection clientConnection = buildPaymentChannelClientConnection();

      paymentChannelClient = new PaymentChannelClient(wallet, myKey, maxValue, serverIdHash, clientConnection);

    } else {
      log.warn("Init payments was called with an already initiated payentChannelClient");
      channelOpenFuture.set(this);
    }
    return channelOpenFuture;
  }

  /*
   * Returns a ClientConnection, which handles messages going out on the network.
   */
  private PaymentChannelClient.ClientConnection buildPaymentChannelClientConnection() {
    PaymentChannelClient.ClientConnection clientConnection = new PaymentChannelClient.ClientConnection()  {

      // PaymentChannel protobuf objects need to be transformed to Stroem protobuf and sent via TCP.
      @Override
      public void sendToServer(Protos.TwoWayChannelMessage paymentMsg) {
        log.debug("Sending Payment Channel message of type: " + paymentMsg.getType());
        Stroem.PaymentChannelMessage stroemPaymentChannelMsg = Stroem.PaymentChannelMessage.newBuilder()
            .setPaymentChannelMessage(paymentMsg.toByteString()).build();
        StroemMessage msg = StroemMessage.newBuilder()
            .setType(StroemMessage.MessageType.PAYMENTCHANNEL_MESSAGE)
            .setPaymentChannelMessage(stroemPaymentChannelMsg)
            .build();
        wireParser.write(msg);
      }

      // This method is a bit messy, There might be a simpler way to figure out what error case should go where.
      @Override
      public void destroyConnection(PaymentChannelCloseException.CloseReason reason) {
        if(channelOpenFuture.isDone()) {
          if (reason == PaymentChannelCloseException.CloseReason.CLIENT_REQUESTED_CLOSE) {
            if (settling) {
              log.info("Payment channel settled successfully.");
              settlementFuture.set(null);
            } else {
              throw new IllegalStateException("Client has not requested settle, but server says we have!");
            }
          } else {
            log.warn("Payment channel terminating with reason {}", reason);
            if (reason == PaymentChannelCloseException.CloseReason.SERVER_REQUESTED_TOO_MUCH_VALUE) {
              settlementFuture.setException(new InsufficientMoneyException(paymentChannelClient.getMissing()));
            } else {
              currentFuture.setException(new PaymentChannelCloseException("Unexpected payment channel termination", reason));
            }
          }
        } else {
          channelOpenFuture.setException(new PaymentChannelCloseException("Unable to open payment channel for reason : " + reason, reason));
        }
        wireParser.closeConnection();
      }

      // This implementation only allows responses where the server agrees to the clients demands.
      @Override
      public boolean acceptExpireTime(long expireTime) {
        long currentTimeMillis = System.currentTimeMillis();
        long currentTimeSec = currentTimeMillis / 1000;
        long expectedExpirySec = currentTimeSec + paymentChannelTimeoutSeconds;
        long min = expectedExpirySec - SAFE_MARGIN_SECONDS;
        long max = expectedExpirySec + SAFE_MARGIN_SECONDS;
        log.debug("Expire time = " + expireTime + " must be greater than " + min + " and less than " + max);
        return expireTime > min && expireTime < max;
      }

      @Override
      public void channelOpen(boolean wasInitiated) {
        log.info("Payment channel {}", wasInitiated ? "was initiated." : "found.");
        freshChannel = wasInitiated;
        wireParser.setSocketTimeout(0); // We will set the timeout on the socket instead
        channelOpenFuture.set(StroemClientTcpConnection.this);
      }
    };

    return clientConnection;
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
        try {
          stroemStep = stroemMessageReceiver.receiveMessage(msg, stroemStep);
        } catch (WrongStroemServerVersionException e) {
          // This happens before the payment channel has begun INITIATE.
          log.warn("Incorrect server version: " + e.getMessage());
          channelOpenFuture.setException(e);
        } catch (InsufficientMoneyException e) {
          // We should only get this exception during INITIATE, so channelOpen wasn't called yet.
          log.info("Insufficient money: " + e.getMessage());
          channelOpenFuture.setException(e);
        } catch (StroemProtocolException e) {
          // This could happen anytime,
          log.error("A Stroem protocol error occurred: " + e.getCode().name());
          if(channelOpenFuture.isDone()) {
            channelOpenFuture.setException(e);
          }
          currentFuture.setException(e);
        }
      }

      @Override
      public void connectionOpen(ProtobufParser<StroemMessage> handler) {
        if(stroemStep != StroemStep.START) {
          log.warn("When a TCP channel just opened the Stroem init step should not be " + stroemStep.name());
          stroemStep = StroemStep.START;
        }

        // First thing to do is to send the Stroem Version
        Stroem.StroemClientVersion stroemVersionMsg = Stroem.StroemClientVersion.newBuilder()
            .setVersion(CLIENT_STROEM_VERSION).build();
        StroemMessage msg = StroemMessage.newBuilder()
            .setType(StroemMessage.MessageType.STROEM_CLIENT_VERSION)
            .setStroemClientVersion(stroemVersionMsg)
            .build();
        wireParser.write(msg);

        stroemStep = StroemStep.WAITING_FOR_SERVER_STROM_VERSION;
        // Now we will wait for the server response (see StroemMessageReceiver.receiveStroemVersion())
      }

      @Override
      public void connectionClosed(ProtobufParser<StroemMessage> handler) {
        paymentChannelClient.connectionClosed();
        stroemStep = StroemStep.CONNECTION_CLOSED;

        if(!channelOpenFuture.isDone()) {
          // If this happens when the channel opens we need to mark this as an error.
          channelOpenFuture.setException(new PaymentChannelCloseException("The TCP socket died",
              PaymentChannelCloseException.CloseReason.CONNECTION_CLOSED));
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
  public ListenableFuture<StroemClientTcpConnection> getChannelOpenFuture() {
    return channelOpenFuture;
  }

  /**
   * Increments the total value which we pay the server.
   * We will use the entity (who will issue the promissory note) that the server gave us during the version exchange..
   *
   * @param size How many satoshis to increment the payment by (note: not the new total).
   * @param toTheOrderOf The public key to negotiate this note to, i.e. the entity that should receive the returned note next.
   * @param requiredNegotiations The negotiations we want we be required in the promissory note.
   * @param myPublicKey The elliptict curve public key you want to use.
   * @param merchantsPublicKey The merchant's public key.
   * @throws ValueOutOfRangeException If the size is negative or would pay more than this channel's total value
   * @throws IllegalStateException If the channel has been closed or is not yet open
   *                               (see {@link StroemClientTcpConnection#getChannelOpenFuture()} for the second)
   */
  public synchronized StroemPromissoryNote incrementPayment(
        Coin size,
        byte[] toTheOrderOf,
        List<StroemEntity> requiredNegotiations,
        ECPoint myPublicKey,
        ECPoint merchantsPublicKey
  ) throws ValueOutOfRangeException, IllegalStateException, InterruptedException, ExecutionException, InvalidProtocolBufferException {

    verifyStroemState();
    this.stroemStep = StroemStep.WAITING_FOR_PAYMENT_ACK;

    Stroem.PromissoryNoteRequest request = buildPromissoryNoteRequestProto(size, toTheOrderOf, requiredNegotiations);

    ListenableFuture<PaymentIncrementAck> ackFuture = paymentChannelClient.incrementPayment(size, request.toByteString(), this.userKeySetup);
    PaymentIncrementAck ack = ackFuture.get();
    this.stroemStep = StroemStep.PAYMENT_DONE;

    log.debug("Ack received. Now extract the promissory note.");

    return buildPromissoryNoteFromProto(ack.getInfo(), myPublicKey, merchantsPublicKey);
  }

  private void verifyStroemState() {
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

  private Stroem.PromissoryNoteRequest buildPromissoryNoteRequestProto(Coin size, byte[] toTheOrderOf, List<StroemEntity> requiredNegotiations) {
    Stroem.Entity issuerProtoEntity = this.stroemMessageReceiver.getHubGivenEntity().buildProtoBufObject();

    Stroem.PromissoryNoteRequest.Builder requestBuilder = Stroem.PromissoryNoteRequest.newBuilder()
        .setAmount(size.longValue())
        .setCurrency(CURRENCY)
        .setIssuer(issuerProtoEntity)
        .setToTheOrderOf(ByteString.copyFrom(toTheOrderOf));

    int i = 0;
    for(StroemEntity negotiation: requiredNegotiations) {
      requestBuilder.addRequiredLastNegotiations(negotiation.buildProtoBufObject());
    }

    Stroem.PromissoryNoteRequest request = requestBuilder.build();
    log.debug("Promissory note request created.");
    return request;
  }

  private StroemPromissoryNote buildPromissoryNoteFromProto(ByteString byteString, ECPoint myPublicKey, ECPoint merchantPublicKey) throws InvalidProtocolBufferException {
    PaymentInstrument pmts = ECPaymentInstrumentBitcoinj.getInstance();

    byte[] bytes = byteString.toByteArray();
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
    PaymentInstrument.PromissoryNote deserializedPn = pmts.deserialize(byteBuffer);
    PaymentInstrument.PaymentInfo paymentInfo = pmts.createPaymentInfo("I buy this and that and so on...");
    PaymentInstrument.NegotiateInfo info = deserializedPn.validateForNegotiate(myPublicKey, merchantPublicKey, paymentInfo)
        .getOrElse(null);

    log.debug("Promissory note extracted from byte array.");

    byte[] issuerPublicKeyAsBytes =  pmts.keyBytes(deserializedPn.issuer().publicKey());
    StroemEntity issuer = new StroemEntity(deserializedPn.issuer().name(), issuerPublicKeyAsBytes);

    return null; // TODO: Fix
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
   * Closes the connection, notifying the server it should settle the channel by broadcasting the most recent payment
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

  /**
   * Disconnects the network connection but doesn't request the server to settle the channel first (literally just
   * unplugs the network socket and marks the stored channel state as inactive).
   */
  public void disconnectWithoutSettlement() {
    wireParser.closeConnection();
  }
}
