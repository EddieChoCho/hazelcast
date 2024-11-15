/*
 * Copyright (c) 2008-2024, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.cache.impl.operation;

import com.hazelcast.cache.impl.CacheDataSerializerHook;
import com.hazelcast.cache.impl.record.CacheRecord;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.nio.serialization.impl.Versioned;
import com.hazelcast.spi.impl.operationservice.BackupOperation;

import javax.annotation.Nullable;
import java.io.IOException;

import static com.hazelcast.internal.namespace.NamespaceUtil.callWithNamespace;
import static com.hazelcast.internal.namespace.NamespaceUtil.runWithNamespace;

/**
 * Backup operation for the operation of adding cache entries into record stores.
 *
 * @see CacheEntryProcessorOperation
 * @see CachePutOperation
 * @see CachePutIfAbsentOperation
 * @see CacheReplaceOperation
 * @see CacheGetAndReplaceOperation
 */
public class CachePutBackupOperation
        extends KeyBasedCacheOperation implements BackupOperation, Versioned {

    private CacheRecord cacheRecord;
    private boolean wanOriginated;

    public CachePutBackupOperation() {
    }

    public CachePutBackupOperation(String name, Data key, CacheRecord cacheRecord, @Nullable String userCodeNamespace) {
        this(name, key, cacheRecord, false, userCodeNamespace);
    }

    public CachePutBackupOperation(String name, Data key, CacheRecord cacheRecord, boolean wanOriginated,
                                   @Nullable String userCodeNamespace) {
        super(name, key, userCodeNamespace);
        if (cacheRecord == null) {
            throw new IllegalArgumentException("Cache record of backup operation cannot be null!");
        }
        this.cacheRecord = cacheRecord;
        this.wanOriginated = wanOriginated;
        this.userCodeNamespace = userCodeNamespace;
    }

    @Override
    public void run() {
        if (recordStore != null) {
            runWithNamespace(userCodeNamespace, () -> recordStore.putRecord(key, cacheRecord, true));
            response = Boolean.TRUE;
        }
    }

    @Override
    public void afterRun() {
        if (recordStore != null && !wanOriginated) {
            runWithNamespace(userCodeNamespace, () -> publishWanUpdate(key, cacheRecord));
        }
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeObject(cacheRecord);
        out.writeBoolean(wanOriginated);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        cacheRecord = callWithNamespace(userCodeNamespace, in::readObject);
        wanOriginated = in.readBoolean();
    }

    @Override
    public int getClassId() {
        return CacheDataSerializerHook.PUT_BACKUP;
    }

    @Override
    public boolean requiresTenantContext() {
        return true;
    }
}
