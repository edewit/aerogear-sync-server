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
package org.jboss.aerogear.sync;

import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class SyncManagerITest {
    static SyncManager syncManager;

    @Test
    public void create() {
        final String json = "{\"model\": \"Toyota\"}";
        final Document document = syncManager.create(UUID.randomUUID().toString(), json);
        assertThat(document.content(), equalTo(json));
    }

    @Test
    public void read() throws DocumentNotFoundException {
        final String json = "{\"model\": \"mazda\"}";
        final Document created = syncManager.create(UUID.randomUUID().toString(), json);
        final Document read = syncManager.read(created.id(), created.revision());
        assertThat(read.id(), equalTo(created.id()));
        assertThat(read.revision(), equalTo(created.revision()));
        assertThat(read.content(), equalTo(json));
    }

    @Test
    public void update() throws ConflictException {
        final String updatedJson = "{\"model\": \"mazda\"}";
        final Document created = syncManager.create(UUID.randomUUID().toString(), "{\"model\": \"mazda\"}");
        final Document updated = createDocument(created.id(), created.revision(), updatedJson);
        final Document read = syncManager.update(updated);
        assertThat(read.content(), equalTo(updatedJson));
    }

    @Test
    public void updateWithConflict() throws ConflictException, DocumentNotFoundException {
        final Document created = syncManager.create(UUID.randomUUID().toString(), "{\"model\": \"toyota\"}");
        // update the document which will cause a new revision to be generated.
        final String mazda = "{\"model\": \"mazda\"}";
        syncManager.update(createDocument(created.id(), created.revision(), mazda));
        final String honda = "{\"model\": \"honda\"}";
        try {
            // now try to update using the original revision which is not the latest revision
            syncManager.update(createDocument(created.id(), created.revision(), honda));
        } catch (final ConflictException e) {
            final Document latest = e.latest();
            assertThat(latest.content(), equalTo(mazda));
            // verify that we can update using the latest revision
            final Document updated = syncManager.update(createDocument(created.id(), latest.revision(), honda));
            final Document read = syncManager.read(latest.id(), updated.revision());
            assertThat(read.content(), equalTo(honda));
        }
    }

    @Test
    public void delete() {
        final Document created = syncManager.create(UUID.randomUUID().toString(), "{\"model\": \"lada\"}");
        final String deleteRevision = syncManager.delete(created.id(), created.revision());
        assertThat(deleteRevision, is(not(equalTo(created.revision()))));
    }

    public abstract Document createDocument(final String id, final String revision, final String content);

}
