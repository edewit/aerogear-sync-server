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

public class HibernateSyncManager implements SyncManager {
    private EntityManager entityManager;

    @Override
    public Document read(String id, String revision) throws DocumentNotFoundException {
        return getEntityManager().createNamedQuery("revision", EntityDocument.class)
                .setParameter("id", id)
                .setParameter("revision", Integer.valueOf(revision))
                .getSingleResult();
    }

    @Override
    public Document read(String id) throws DocumentNotFoundException {
        return getEntityManager().find(EntityDocument.class, id);
    }

    @Override
    public Document create(String id, String json) {
        EntityDocument document = new EntityDocument();
        document.setId(id);
        document.setContent(json);

        getEntityManager().getTransaction().begin();
        getEntityManager().persist(document);
        getEntityManager().getTransaction().commit();

        return document;
    }

    @Override
    public Document update(Document doc) throws ConflictException {
        final EntityTransaction transaction = getEntityManager().getTransaction();

        try {
            transaction.begin();
            getEntityManager().merge(doc);
            getEntityManager().flush();
            transaction.commit();
            return doc;
        } catch (OptimisticLockException e) {
            transaction.rollback();
            try {
                throw new ConflictException(doc, read(doc.id()));
            } catch (DocumentNotFoundException e1) {
                //ignore can't have a conflict without a exciting document
                return null;
            }
        }
    }

    @Override
    public String delete(String id, String revision) {
        try {
            final Document document = read(id, revision);

            getEntityManager().getTransaction().begin();
            getEntityManager().remove(document);
            getEntityManager().getTransaction().commit();

        } catch (DocumentNotFoundException e) {
            return null;
        }

        return null;
    }

    public EntityManager getEntityManager() {
        if (entityManager == null) {
            EntityManagerFactory emf = Persistence.createEntityManagerFactory("unit");
            entityManager = emf.createEntityManager();
        }
        return entityManager;
    }

}
