/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.hibernate;

import com.hazelcast.config.MapConfig;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cache.spi.support.QueryResultsRegionTemplate;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.SessionFactoryImpl;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

@RunWith(HazelcastSerialClassRunner.class)
@Category(SlowTest.class)
public class RegionFactoryDefaultSlowTest
        extends HibernateSlowTestSupport {

    protected Properties getCacheProperties() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastCacheRegionFactory.class.getName());
        return props;
    }

    @Test
    public void testQueryCacheCleanup() {
        MapConfig mapConfig = getHazelcastInstance(sf).getConfig().getMapConfig("default-query-results-region");
        final float baseEvictionRate = 0.2f;
        final int numberOfEntities = 100;
        final int defaultCleanupPeriod = 60;
        final int maxSize = mapConfig.getMaxSizeConfig().getSize();
        final int evictedItemCount = numberOfEntities - maxSize + (int) (maxSize * baseEvictionRate);
        insertDummyEntities(numberOfEntities);
        for (int i = 0; i < numberOfEntities; i++) {
            executeQuery(sf, i);
        }

        QueryResultsRegionTemplate regionTemplate = (QueryResultsRegionTemplate) (((SessionFactoryImpl) sf).getCache()).getDefaultQueryResultsCache().getRegion();
        RegionCache cache = ((HazelcastStorageAccessImpl) regionTemplate.getStorageAccess()).getDelegate();
        assertEquals(numberOfEntities, cache.getElementCountInMemory());
        sleep(defaultCleanupPeriod);

        assertEquals(numberOfEntities - evictedItemCount, cache.getElementCountInMemory());
    }

    @SuppressWarnings("Duplicates")
    @Test
    public void testUpdateEntity() {
        final long dummyId = 0;
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        session.save(new DummyEntity(dummyId, null, 0, null));
        tx.commit();

        tx = session.beginTransaction();
        DummyEntity ent = session.get(DummyEntity.class, dummyId);
        ent.setName("updatedName");
        session.update(ent);
        tx.commit();
        session.close();

        session = sf2.openSession();
        DummyEntity entity = session.get(DummyEntity.class, dummyId);
        assertEquals("updatedName", entity.getName());
    }
}
