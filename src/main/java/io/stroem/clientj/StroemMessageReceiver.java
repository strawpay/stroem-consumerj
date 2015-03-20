package io.stroem.clientj;

import io.stroem.clientj.domain.StroemEntity;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.protocols.channels.PaymentChannelClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.stroem.proto.StroemProtos;
import org.bitcoin.paymentchannel.Protos;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkState;



/**
 * Handles incoming Stroem messages.
 */
public class StroemMessageReceiver {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(StroemMessageReceiver.class);

  // Used to keep track of whether or not the "socket" ie connection is open and we can generate messages
  @VisibleForTesting  boolean connectionOpen = false;

  private final PaymentChannelClient paymentChannelClient;

  private StroemEntity hubGivenEntity;

  public StroemEntity getHubGivenEntity() {
    return hubGivenEntity;
  }

  public StroemMessageReceiver(PaymentChannelClient paymentChannelClient) {
    this.paymentChannelClient = paymentChannelClient;
  }

  public StroemStep receiveMessage(StroemProtos.StroemMessage msg, StroemStep previousStep)
      throws InsufficientMoneyException, WrongStroemServerVersionException, StroemProtocolException {

    switch (msg.getType()) {
      case PAYMENTCHANNEL_MESSAGE:
        checkState(msg.hasPaymentChannelMessage());
        readPaymentChannelMessage(msg.getPaymentChannelMessage());
        return previousStep;
      case STROEM_SERVER_VERSION:
        checkState(msg.hasStroemServerVersion());
        return receiveStroemVersion(msg.getStroemServerVersion(), previousStep);
      case PROMISSORY_NOTE:
        checkState(msg.hasPromissoryNote());
        receivePromissoryNote(msg.getPromissoryNote());
        return previousStep;
      case ERROR:
        checkState(msg.hasError());
        receiveError(msg.getError());
        return previousStep;
      default:
        String errMsg = "Client got unknown Stroem message type " + msg.getType().name();
        log.error(errMsg);
        throw new StroemProtocolException(errMsg);
    }
  }

  /**
   * Just extract the payment channel message and send it to PaymentChannelClient.
   */
  private void readPaymentChannelMessage(StroemProtos.PaymentChannelMessage msg) throws InsufficientMoneyException {
    ByteString byteString = msg.getPaymentChannelMessage();
    try {
      Protos.TwoWayChannelMessage paymentChannelMsg = Protos.TwoWayChannelMessage.newBuilder().mergeFrom(byteString).build();
      log.debug("Received a StroemMessage of type PaymentChannel: " + paymentChannelMsg.getType());
      paymentChannelClient.receiveMessage(paymentChannelMsg);
    } catch (InvalidProtocolBufferException e) {
      log.error("Unable to read the payment channel protobuf message: "+ e.getMessage());
      throw new RuntimeException(e);
    }
  }

  /**
   * Check that the server version is same as we have.
   * If so, open the payment channel
   */
  private StroemStep receiveStroemVersion(StroemProtos.StroemServerVersion msg, StroemStep step) throws WrongStroemServerVersionException {
    log.debug("Received Stroem server version");
    if(step == StroemStep.WAITING_FOR_SERVER_STROM_VERSION) {
      int serverVersion = msg.getVersion();
      if(serverVersion == StroemClientTcpConnection.CLIENT_STROEM_VERSION) {
        paymentChannelClient.connectionOpen();
        hubGivenEntity = new StroemEntity(msg.getEntity());
        return StroemStep.WAITING_FOR_PAYMENT_CHANNEL_INITIATE;
      } else {
        throw new WrongStroemServerVersionException("Server version should be " + StroemClientTcpConnection.CLIENT_STROEM_VERSION + " but was " + serverVersion);
      }
    } else {
      throw new IllegalStateException("Can't get STROM_VERSION from server before client sent STROM_VERSION");
    }
  }

  /**
   *
   */
  private void receivePromissoryNote(StroemProtos.PromissoryNote msg) {
    log.debug("Received Stroem promissory note");
    //  TODO: Not needed yet
    throw new IllegalStateException("not impl");

  }

  /**
   * Try to recognize the error code.
   */
  private void receiveError(StroemProtos.Error msg) throws StroemProtocolException {
    log.error("Server sent ERROR {} with explanation {}", msg.getCode().name(), msg.hasExplanation() ? msg.getExplanation() : "(none)");
    StroemProtocolException.Code code;
    code = StroemProtocolException.Code.fromId(msg.getCode().getNumber());
    throw new StroemProtocolException(code, msg.getExplanation());
  }



}
