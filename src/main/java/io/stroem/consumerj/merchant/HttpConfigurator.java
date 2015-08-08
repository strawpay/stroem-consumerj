package io.stroem.consumerj.merchant;

import io.stroem.proto.StroemProtos;
import org.bitcoinj.protocols.payments.PaymentProtocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 *
 */
public class HttpConfigurator {

    public static final int HTTP_TIMEOUT_MS = 15000; // Using 15 sec (the same value as Schildbach does)

    public static final String MIMETYPE_PAYMENTREQUEST = "application/stroem-paymentrequest";
    public static final String MIMETYPE_PAYMENT = "application/stroem-payment";
    public static final String MIMETYPE_PAYMENTACK = "application/stroem-paymentack";

    public static HttpURLConnection buildHttpURLConnectionForGettingOffer(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(HTTP_TIMEOUT_MS);
        connection.setReadTimeout(HTTP_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", MIMETYPE_PAYMENTREQUEST); //  Should we accept normal BIP70 too?
        return connection;
    }

    public static HttpURLConnection buildHttpURLConnectionForPostingTheNote(URI paymentUri, StroemProtos.StroemMessage stroemMessage) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) paymentUri.toURL().openConnection();
        connection.setConnectTimeout(HTTP_TIMEOUT_MS);
        connection.setReadTimeout(HTTP_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", MIMETYPE_PAYMENT);
        connection.setRequestProperty("Accept", MIMETYPE_PAYMENTACK);
        connection.setRequestProperty("Content-Length", Integer.toString(stroemMessage.getSerializedSize()));
        return connection;
    }

    /**
     * Close everything.
     */
    public static void closeAll(OutputStream os, InputStream is, HttpURLConnection connection) {
        if (os != null) {
            try {
                os.close();
            } catch (final IOException x) {
                // swallow
            }
        }
        closeAll(is, connection);
    }

    public static void closeAll(InputStream is, HttpURLConnection connection) {
        if (is != null) {
            try {
                is.close();
            } catch (final IOException x) {
                // swallow
            }
        }
        closeAll(connection);
    }

    public static void closeAll(HttpURLConnection connection) {
        if (connection != null)
            connection.disconnect();
    }
}
