package com.paicbd.module.e2e;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.bean.InterfaceVersion;
import org.jsmpp.session.BindRequest;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.SMPPServerSessionListener;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Slf4j
@RequiredArgsConstructor
public class SmppServerMock implements Runnable {
    private final ThreadFactory factory = Thread.ofVirtual().name("server_session-", 0).factory();
    private final ExecutorService executorService = Executors.newThreadPerTaskExecutor(factory);

    private final int port;

    @Setter
    private Exception forcedSmppServerException; // Parameter to force the exception when trying to bind connections
    private Exception forcedListenerException;
    private boolean isEmptyShortMessage;

    private MockServerListener sessionMsgListener;

    @SneakyThrows
    @Override
    public void run() {
        log.info("### SmppServerMock start");

        if (forcedSmppServerException != null) {
            throw forcedSmppServerException;
        }
        try (SMPPServerSessionListener listener = new SMPPServerSessionListener(port)) {
            log.info("### Starting listening traffic on port {} for gateway MockSmppServer", port);
            listener.setPduProcessorDegree(10);
            listener.setQueueCapacity(100);
            SMPPServerSession session;
            while ((session = listener.accept()) != null) {
                session.setTransactionTimer(5000);
                log.debug("Session accepted from {}", session.getSessionId());
                SMPPServerSession finalSession = session;
                executorService.submit(() -> {
                    try {
                        BindRequest bindRequest = finalSession.waitForBind(5000);
                        String systemId = bindRequest.getSystemId();
                        sessionMsgListener = new MockServerListener(forcedListenerException, isEmptyShortMessage);
                        finalSession.setMessageReceiverListener(sessionMsgListener);
                        finalSession.addSessionStateListener(sessionMsgListener);
                        finalSession.setEnquireLinkTimer(5000);
                        bindRequest.accept(systemId, InterfaceVersion.IF_50);
                    } catch (Exception e) {
                        log.error("Error binding session", e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error starting SMPPServerSessionListener", e);
        }
    }

    public void setForcedListenerException(Exception forcedListenerException) {
        this.forcedListenerException = forcedListenerException;
        if (Objects.nonNull(sessionMsgListener)) {
            sessionMsgListener.setForcedException(forcedSmppServerException);
        }
    }

    public void setEmptyShortMessage(boolean emptyShortMessage) {
        isEmptyShortMessage = emptyShortMessage;
        if (Objects.nonNull(sessionMsgListener)) {
            sessionMsgListener.setEmptyShortMessage(emptyShortMessage);
        }
    }
}
