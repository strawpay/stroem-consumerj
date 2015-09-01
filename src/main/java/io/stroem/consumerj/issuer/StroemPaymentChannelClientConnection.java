package io.stroem.consumerj.issuer;

import com.google.common.util.concurrent.SettableFuture;
import io.stroem.consumerj.StroemIssuerConnection;
import io.stroem.proto.StroemProtos;
import org.bitcoin.paymentchannel.Protos;
import org.bitcoinj.net.ProtobufParser;
import org.bitcoinj.protocols.channels.PaymentChannelClient;
import org.bitcoinj.protocols.channels.PaymentChannelCloseException;
import org.slf4j.LoggerFactory;

/**
 * Responsible for sending payment channel messages (among other things) to the server.
 *
 * Pretty tight coupled to {@link StroemIssuerConnection}, which this class is sharing the wireParser with.
 * This class will report back to {@link StroemIssuerConnection} when the channel has been open,
 * and will update various futures.
 *
 */
public class StroemPaymentChannelClientConnection implements PaymentChannelClient.ClientConnection {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(StroemPaymentChannelClientConnection.class);

    public static final long SAFE_MARGIN_SECONDS = 60*59; // 59 minutes time difference between the client and server clocks is allowed.

    private final StroemIssuerConnection stroemIssuerConnection;

    private ProtobufParser<StroemProtos.StroemMessage> wireParser;

    private PaymentChannelClient paymentChannelClient;

    private long paymentChannelTimeoutSeconds;

    // Futures
    private final SettableFuture<StroemIssuerConnectionResult> channelOpenFuture;
    private final SettableFuture<StroemSettleResult> settlementFuture;


    public StroemPaymentChannelClientConnection(StroemIssuerConnection stroemIssuerConnection,
                                                SettableFuture<StroemIssuerConnectionResult> channelOpenFuture,
                                                SettableFuture<StroemSettleResult> settlementFuture,
                                                long paymentChannelTimeoutSeconds
    ) {
        this.stroemIssuerConnection = stroemIssuerConnection;
        this.channelOpenFuture = channelOpenFuture;
        this.settlementFuture = settlementFuture;
        this.paymentChannelTimeoutSeconds = paymentChannelTimeoutSeconds;
    }

    public void setWireParser(ProtobufParser<StroemProtos.StroemMessage> wireParser) {
        this.wireParser = wireParser;
    }

    public void setPaymentChannelClient(PaymentChannelClient paymentChannelClient) {
        this.paymentChannelClient = paymentChannelClient;
    }


    // PaymentChannel protobuf objects need to be transformed to Stroem protobuf and sent via TCP.
    @Override
    public void sendToServer(Protos.TwoWayChannelMessage paymentMsg) {
        log.debug("callback - sendToServer - Sending Payment Channel message of type: " + paymentMsg.getType());
        StroemProtos.PaymentChannelMessage stroemPaymentChannelMsg = StroemProtos.PaymentChannelMessage.newBuilder()
                .setPaymentChannelMessage(paymentMsg.toByteString()).build();
        StroemProtos.StroemMessage msg = StroemProtos.StroemMessage.newBuilder()
                .setType(StroemProtos.StroemMessage.MessageType.PAYMENTCHANNEL_MESSAGE)
                .setPaymentChannelMessage(stroemPaymentChannelMsg)
                .build();
        wireParser.write(msg);
        log.debug("Written to protobuf parser.");
    }

    // When a connection is destroyed, we must close some futures.
    @Override
    public void destroyConnection(PaymentChannelCloseException.CloseReason reason) {
        log.debug("callback - destroyConnection - start (channel state = " + stroemIssuerConnection.state().getState() + ")");

        // When we get this callback during the opening of a channel we have usually already set the
        // explicit error on the channelOpenFuture (so it will be "done").
        if (!channelOpenFuture.isDone()) {
            log.error("Safety catch: normally we will never get here: " + reason.name());
            channelOpenFuture.set(new StroemIssuerConnectionResult(StroemIssuerConnectionResult.StatusCode.ERROR,
                    "Unable to open payment channel for reason : " + reason.name()));
            return;
        }

        if (stroemIssuerConnection.isSettling()) {
            log.debug("Client requested close.");
            if (reason == PaymentChannelCloseException.CloseReason.CLIENT_REQUESTED_CLOSE) {
                log.info("Payment channel settled successfully.");
                if (!settlementFuture.isDone()) {
                    settlementFuture.set(new StroemSettleResult(StroemSettleResult.StatusCode.OK, ""));
                }
            } else {
                String err = "Something went wrong during settle: " + reason.name();
                log.error(err);
                if (!settlementFuture.isDone()) {
                    settlementFuture.set(new StroemSettleResult(StroemSettleResult.StatusCode.ERROR, err));
                }
            }
        } else {
            log.debug("Client did not require close, reason: " + reason.name());
            if (reason == PaymentChannelCloseException.CloseReason.CLIENT_REQUESTED_CLOSE) {
                String err = "Client has not requested settle, but server says we have!"; // This is a bug
                log.error(err);
                throw new IllegalStateException(err);
            } else {
                // Unexpected close
                // TODO: We will get here upon error during payment: See https://github.com/bitcoinj/bitcoinj/issues/1067
                log.error("Payment channel terminating with reason {}", reason);
            }
        }
        wireParser.closeConnection();
    }

    // This implementation only allows responses where the server agrees to the clients demands.
    @Override
    public boolean acceptExpireTime(long expireTime) {
        log.debug("callback - acceptExpireTime - start");
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
        log.info("callback - payment channel {}", wasInitiated ? "was initiated." : "found.");
        wireParser.setSocketTimeout(0); // We will set the timeout on the socket instead
        stroemIssuerConnection.setConnectionOpen(wasInitiated);
    }

}
