/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.sync.server.gcm;

import org.jboss.aerogear.sync.server.gcm.GcmDiffSyncHandler.GcmPacketExtension;
import org.jboss.aerogear.sync.PatchMessage;
import org.jboss.aerogear.sync.server.Subscriber;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Packet;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.UUID;

import static org.jboss.aerogear.sync.server.gcm.GcmMessages.createJsonMessage;

public class GcmSubscriber implements Subscriber<XMPPConnection> {

    private final String id;
    private final String googleRegistrationId;
    private final XMPPConnection connection;

    public GcmSubscriber(final String id, final String googleRegistrationId, final XMPPConnection connection) {
        this.id = id;
        this.googleRegistrationId = googleRegistrationId;
        this.connection = connection;
    }

    @Override
    public String clientId() {
        return id;
    }

    @Override
    public XMPPConnection channel() {
        return connection;
    }

    @Override
    public void patched(final PatchMessage<?> patchMessage) {
        try {
            send(createJsonMessage(googleRegistrationId, "m-" + UUID.randomUUID(), patchMessage.asJson()));
        } catch (Exception ex) {
            Logger.getLogger(GcmSubscriber.class.getName()).log(Level.SEVERE, null, ex);
            //throw new RuntimeException(ex);
        }

    }

    protected void send(String jsonRequest) throws SmackException.NotConnectedException {
        final Packet request = new GcmPacketExtension(jsonRequest).toPacket();
        connection.sendPacket(request);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + (id != null ? id.hashCode() : 0);
        hash = 89 * hash + (googleRegistrationId != null ? googleRegistrationId.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        final Subscriber<String> other = (Subscriber<String>) obj;
        if (id == null ? other.clientId() != null : !id.equals(other.clientId())) {
            return false;
        }
        if (googleRegistrationId == null ? other.channel() != null : !googleRegistrationId.equals(other.channel())) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "GcmSubscriber{" + "clientId=" + id + ", googleRegistrationid=" + googleRegistrationId + '}';
    }

}
