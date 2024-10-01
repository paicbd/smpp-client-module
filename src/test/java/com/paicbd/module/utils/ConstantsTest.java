package com.paicbd.module.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConstantsTest {

    @Test
    void testPrivateConstructor() throws NoSuchMethodException {
        Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    void testConstantsValues() {
        assertEquals("STOPPED", Constants.STOPPED);
        assertEquals("BINDING", Constants.BINDING);
        assertEquals("BOUND", Constants.BOUND);
        assertEquals("UNBINDING", Constants.UNBINDING);
        assertEquals("UNBOUND", Constants.UNBOUND);
        assertEquals("gw", Constants.TYPE);
        assertEquals("/app/handler-status", Constants.WEBSOCKET_STATUS_ENDPOINT);
        assertEquals("/app/session-confirm", Constants.SESSION_CONFIRM_ENDPOINT);
        assertEquals("/app/updateGateway", Constants.UPDATE_GATEWAY_ENDPOINT);
        assertEquals("/app/connectGateway", Constants.CONNECT_GATEWAY_ENDPOINT);
        assertEquals("/app/response-smpp-client", Constants.RESPONSE_SMPP_CLIENT_ENDPOINT);
        assertEquals("/app/stopGateway", Constants.STOP_GATEWAY_ENDPOINT);
        assertEquals("/app/deleteGateway", Constants.DELETE_GATEWAY_ENDPOINT);
        assertEquals("/app/updateErrorCodeMapping", Constants.UPDATE_ERROR_CODE_MAPPING_ENDPOINT);
        assertEquals("/app/update/routingRules", Constants.UPDATE_ROUTING_RULE_ENDPOINT);
        assertEquals("/app/delete/routingRules", Constants.DELETE_ROUTING_RULE_ENDPOINT);
        assertEquals("status", Constants.PARAM_UPDATE_STATUS);
        assertEquals("sessions", Constants.PARAM_UPDATE_SESSIONS);
        assertEquals("GW", Constants.ORIGIN_GATEWAY_TYPE);
    }
}