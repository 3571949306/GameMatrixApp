package com.gamecenter.app.ai;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AiRequestGateTest {

    @Test
    public void onlyOneRequestCanBeActive() {
        AiRequestGate gate = new AiRequestGate();

        AiRequestGate.RequestToken first = gate.tryAcquire();

        assertNotNull(first);
        assertNull(gate.tryAcquire());
        assertTrue(gate.isActive(first));
        assertTrue(gate.finish(first));
        assertFalse(gate.isActive(first));
        assertNotNull(gate.tryAcquire());
    }

    @Test
    public void invalidatingConversationRejectsLateCallbackAndProtectsNewRequest() {
        AiRequestGate gate = new AiRequestGate();
        AiRequestGate.RequestToken oldRequest = gate.tryAcquire();

        gate.invalidateConversation();
        AiRequestGate.RequestToken newRequest = gate.tryAcquire();

        assertFalse(gate.isActive(oldRequest));
        assertFalse(gate.finish(oldRequest));
        assertTrue(gate.isActive(newRequest));
        assertTrue(gate.finish(newRequest));
    }
}
