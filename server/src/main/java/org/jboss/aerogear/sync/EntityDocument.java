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

import javax.persistence.*;

@Entity
@NamedQueries({
        @NamedQuery(name = "revision", query = "from EntityDocument d where d.id = :id and version = :revision")
})
public class EntityDocument implements Document {
    @Id
    private String id;

    @Version
    private Integer version;

    private String content;

    public EntityDocument() {}

    public EntityDocument(String id, String revision, String content) {
        this.id = id;
        this.version = Integer.valueOf(revision);
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String revision() {
        return String.valueOf(version);
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String content() {
        return content;
    }


}
