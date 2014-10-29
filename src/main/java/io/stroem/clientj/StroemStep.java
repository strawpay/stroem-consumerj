package io.stroem.clientj;

/**
 * A state model for the Stroem client.
 */
public enum StroemStep {
  START,                               // Before even sending the first message to the server
  WAITING_FOR_SERVER_STROM_VERSION,
  WAITING_FOR_PAYMENT_CHANNEL_INITIATE,
  CONNECTION_OPEN,                     // The TCP connection is now open. Ready to send payments
  WAITING_FOR_PAYMENT_ACK,             // Payment in process, do not disturb.
  PAYMENT_DONE,                        // OK to send payments.
  CONNECTION_CLOSED,
}
