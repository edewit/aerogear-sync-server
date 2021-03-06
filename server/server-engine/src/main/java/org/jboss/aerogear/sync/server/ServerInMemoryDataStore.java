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
package org.jboss.aerogear.sync.server;

import org.jboss.aerogear.sync.BackupShadowDocument;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.Diff;
import org.jboss.aerogear.sync.Document;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.ShadowDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory implementation of {@link ServerDataStore}.
 * <p>
 * This implementation is mainly intended for testing and example applications.
 *
 * @param <T> The data type data that this implementation can handle.
 * @param <S> The type of {@link Edit}s that this implementation can handle.
 */
public class ServerInMemoryDataStore<T, S extends Edit<? extends Diff>> implements ServerDataStore<T, S> {

    private final Queue<S> emptyQueue = new LinkedList<S>();
    private final ConcurrentMap<String, Document<T>> documents = new ConcurrentHashMap<String, Document<T>>();
    private final ConcurrentMap<Id, ShadowDocument<T>> shadows = new ConcurrentHashMap<Id, ShadowDocument<T>>();
    private final ConcurrentMap<Id, BackupShadowDocument<T>> backups = new ConcurrentHashMap<Id, BackupShadowDocument<T>>();
    private final ConcurrentHashMap<Id, Queue<S>> pendingEdits = new ConcurrentHashMap<Id, Queue<S>>();
    private static final Logger logger = LoggerFactory.getLogger(ServerInMemoryDataStore.class);

    @Override
    public void saveShadowDocument(final ShadowDocument<T> shadowDocument) {
        shadows.put(id(shadowDocument.document()), shadowDocument);
    }

    @Override
    public ShadowDocument<T> getShadowDocument(final String documentId, final String clientId) {
        return shadows.get(id(documentId, clientId));
    }

    @Override
    public void saveBackupShadowDocument(final BackupShadowDocument<T> backupShadow) {
        backups.put(id(backupShadow.shadow().document()), backupShadow);
    }

    @Override
    public BackupShadowDocument<T> getBackupShadowDocument(final String documentId, final String clientId) {
        return backups.get(id(documentId, clientId));
    }

    @Override
    public boolean saveDocument(final Document<T> document) {
        return documents.putIfAbsent(document.id(), document) == null;
    }

    @Override
    public void updateDocument(final Document<T> document) {
        documents.put(document.id(), document);
    }

    @Override
    public Document<T> getDocument(final String documentId) {
        return documents.get(documentId);
    }

    @Override
    public void saveEdits(final S edit, final String documentId, final String clientId) {
        final Id id = id(documentId, clientId);
        final Queue<S> newEdits = new ConcurrentLinkedQueue<S>();
        while (true) {
            final Queue<S> currentEdits = pendingEdits.get(id);
            if (currentEdits == null) {
                newEdits.add(edit);
                final Queue<S> previous = pendingEdits.putIfAbsent(id, newEdits);
                if (previous != null) {
                    newEdits.addAll(previous);
                    if (pendingEdits.replace(id, previous, newEdits)) {
                        break;
                    }
                } else {
                    break;
                }
            } else {
                newEdits.addAll(currentEdits);
                newEdits.add(edit);
                if (pendingEdits.replace(id, currentEdits, newEdits)) {
                    break;
                }
            }
        }
    }

    @Override
    public void removeEdit(final S edit, final String documentId, final String clientId) {
        final Id id = id(documentId, clientId);
        while (true) {
            final Queue<S> currentEdits = pendingEdits.get(id);
            if (currentEdits == null) {
                break;
            }
            final Queue<S> newEdits = new ConcurrentLinkedQueue<S>();
            newEdits.addAll(currentEdits);
            for (Iterator<S> iter = newEdits.iterator(); iter.hasNext();) {
                final S oldEdit = iter.next();
                if (oldEdit.serverVersion() <= edit.serverVersion()) {
                    logger.info("Removing version " + oldEdit);
                    iter.remove();
                }
            }
            if (pendingEdits.replace(id, currentEdits, newEdits)) {
                break;
            }
        }
    }

    @Override
    public Queue<S> getEdits(final String documentId, final String clientId) {
        final Queue<S> edits = pendingEdits.get(id(documentId, clientId));
        if (edits == null) {
            return emptyQueue;
        }
        return edits;
    }

    @Override
    public void removeEdits(final String documentId, final String clientId) {
        pendingEdits.remove(id(documentId, clientId));
    }

    private Id id(final ClientDocument<T> document) {
        return id(document.id(), document.clientId());
    }

    private static Id id(final String documentId, final String clientId) {
        return new Id(documentId, clientId);
    }

    private static class Id {

        private final String clientId;
        private final String documentId;

        Id(final String documentId, final String clientId) {
            this.clientId = clientId;
            this.documentId = documentId;
        }

        public String clientId() {
            return clientId;
        }

        public String getDocumentId() {
            return documentId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Id)) {
                return false;
            }

            final Id id = (Id) o;

            if (clientId != null ? !clientId.equals(id.clientId) : id.clientId != null) {
                return false;
            }
            return documentId != null ? documentId.equals(id.documentId) : id.documentId == null;
        }

        @Override
        public int hashCode() {
            int result = clientId != null ? clientId.hashCode() : 0;
            result = 31 * result + (documentId != null ? documentId.hashCode() : 0);
            return result;
        }
    }
}
