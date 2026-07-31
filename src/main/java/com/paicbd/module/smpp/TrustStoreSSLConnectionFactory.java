package com.paicbd.module.smpp;

import com.paicbd.smsc.exception.RTException;
import org.jsmpp.session.connection.Connection;
import org.jsmpp.session.connection.ConnectionFactory;
import org.jsmpp.session.connection.socket.SocketConnection;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;

/**
 * A {@link ConnectionFactory} that establishes SSL/TLS client connections
 * using a PKCS12 truststore to verify the remote SMPP server's certificate.
 */
public class TrustStoreSSLConnectionFactory implements ConnectionFactory {

    private final SSLSocketFactory sslSocketFactory;

    public TrustStoreSSLConnectionFactory(String truststorePath, String truststorePassword) {
        if (truststorePath == null || truststorePath.isBlank()) {
            throw new IllegalArgumentException("Truststore file path must not be empty when TLS is enabled");
        }
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            KeyStore truststore = KeyStore.getInstance("PKCS12");
            truststore.load(fis, truststorePassword != null ? truststorePassword.toCharArray() : null);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(truststore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            this.sslSocketFactory = sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RTException("Failed to initialize TLS truststore from '" + truststorePath + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Connection createConnection(String host, int port) throws IOException {
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);
        sslSocket.startHandshake();
        return new SocketConnection(sslSocket);
    }
}
