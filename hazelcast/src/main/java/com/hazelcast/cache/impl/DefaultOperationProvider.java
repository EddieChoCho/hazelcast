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

package com.hazelcast.cache.impl;

import com.hazelcast.cache.impl.operation.CacheClearOperationFactory;
import com.hazelcast.cache.impl.operation.CacheContainsKeyOperation;
import com.hazelcast.cache.impl.operation.CacheEntryProcessorOperation;
import com.hazelcast.cache.impl.operation.CacheFetchEntriesOperation;
import com.hazelcast.cache.impl.operation.CacheFetchKeysOperation;
import com.hazelcast.cache.impl.operation.CacheGetAllOperationFactory;
import com.hazelcast.cache.impl.operation.CacheGetAndRemoveOperation;
import com.hazelcast.cache.impl.operation.CacheGetAndReplaceOperation;
import com.hazelcast.cache.impl.operation.CacheGetOperation;
import com.hazelcast.cache.impl.operation.CacheLoadAllOperationFactory;
import com.hazelcast.cache.impl.operation.CacheMergeOperation;
import com.hazelcast.cache.impl.operation.CacheMergeOperationFactory;
import com.hazelcast.cache.impl.operation.CachePutAllOperation;
import com.hazelcast.cache.impl.operation.CachePutIfAbsentOperation;
import com.hazelcast.cache.impl.operation.CachePutOperation;
import com.hazelcast.cache.impl.operation.CacheRemoveAllOperationFactory;
import com.hazelcast.cache.impl.operation.CacheRemoveOperation;
import com.hazelcast.cache.impl.operation.CacheReplaceOperation;
import com.hazelcast.cache.impl.operation.CacheSetExpiryPolicyOperation;
import com.hazelcast.cache.impl.operation.CacheSizeOperationFactory;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.internal.iteration.IterationPointer;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.spi.impl.operationservice.Operation;
import com.hazelcast.spi.impl.operationservice.OperationFactory;
import com.hazelcast.spi.merge.SplitBrainMergePolicy;
import com.hazelcast.spi.merge.SplitBrainMergeTypes.CacheMergeTypes;

import javax.annotation.Nullable;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.processor.EntryProcessor;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provide operations other then {@link InMemoryFormat#NATIVE}
 */
@SuppressWarnings("ClassDataAbstractionCoupling")
public class DefaultOperationProvider implements CacheOperationProvider {

    protected final String nameWithPrefix;
    @Nullable
    protected final String userCodeNamespace;

    public DefaultOperationProvider(String nameWithPrefix, @Nullable String userCodeNamespace) {
        this.nameWithPrefix = nameWithPrefix;
        this.userCodeNamespace = userCodeNamespace;
    }

    @Override
    public Operation createPutOperation(Data key, Data value, ExpiryPolicy policy, boolean get, int completionId) {
        return new CachePutOperation(nameWithPrefix, key, value, policy, get, completionId, userCodeNamespace);
    }

    @Override
    public Operation createPutAllOperation(List<Map.Entry<Data, Data>> entries, ExpiryPolicy policy, int completionId) {
        return new CachePutAllOperation(nameWithPrefix, entries, policy, completionId, userCodeNamespace);
    }

    @Override
    public Operation createGetOperation(Data key, ExpiryPolicy policy) {
        return new CacheGetOperation(nameWithPrefix, key, policy, userCodeNamespace);
    }

    @Override
    public Operation createContainsKeyOperation(Data key) {
        return new CacheContainsKeyOperation(nameWithPrefix, key, userCodeNamespace);
    }

    @Override
    public Operation createPutIfAbsentOperation(Data key, Data value, ExpiryPolicy policy, int completionId) {
        return new CachePutIfAbsentOperation(nameWithPrefix, key, value, policy, completionId, userCodeNamespace);
    }

    @Override
    public Operation createRemoveOperation(Data key, Data oldValue, int completionId) {
        return new CacheRemoveOperation(nameWithPrefix, key, oldValue, completionId, userCodeNamespace);
    }

    @Override
    public Operation createGetAndRemoveOperation(Data key, int completionId) {
        return new CacheGetAndRemoveOperation(nameWithPrefix, key, completionId, userCodeNamespace);
    }

    @Override
    public Operation createReplaceOperation(Data key, Data oldValue, Data newValue, ExpiryPolicy policy, int completionId) {
        return new CacheReplaceOperation(nameWithPrefix, key, oldValue, newValue, policy, completionId, userCodeNamespace);
    }

    @Override
    public Operation createGetAndReplaceOperation(Data key, Data value, ExpiryPolicy policy, int completionId) {
        return new CacheGetAndReplaceOperation(nameWithPrefix, key, value, policy, completionId, userCodeNamespace);
    }

    @Override
    public Operation createEntryProcessorOperation(Data key, Integer completionId, EntryProcessor
            entryProcessor, Object... arguments) {
        return new CacheEntryProcessorOperation(nameWithPrefix, key, completionId, entryProcessor, userCodeNamespace, arguments);
    }

    @Override
    public Operation createFetchKeysOperation(IterationPointer[] pointers, int fetchSize) {
        return new CacheFetchKeysOperation(nameWithPrefix, pointers, fetchSize, userCodeNamespace);
    }

    @Override
    public Operation createFetchEntriesOperation(IterationPointer[] pointers, int fetchSize) {
        return new CacheFetchEntriesOperation(nameWithPrefix, pointers, fetchSize, userCodeNamespace);
    }

    @Override
    public Operation createMergeOperation(String name, List<CacheMergeTypes<Object, Object>> mergingEntries,
                                          SplitBrainMergePolicy<Object, CacheMergeTypes<Object, Object>, Object> policy) {
        return new CacheMergeOperation(name, mergingEntries, policy, userCodeNamespace);
    }

    @Override
    public OperationFactory createMergeOperationFactory(String name, int[] partitions,
                                                        List<CacheMergeTypes<Object, Object>>[] mergingEntries,
                                                        SplitBrainMergePolicy<Object, CacheMergeTypes<Object, Object>,
                                                                Object> policy) {
        return new CacheMergeOperationFactory(name, partitions, mergingEntries, policy, userCodeNamespace);
    }

    @Override
    public Operation createSetExpiryPolicyOperation(List<Data> keys, Data expiryPolicy) {
        return new CacheSetExpiryPolicyOperation(nameWithPrefix, keys, expiryPolicy, userCodeNamespace);
    }

    @Override
    public OperationFactory createGetAllOperationFactory(Set<Data> keySet, ExpiryPolicy policy) {
        return new CacheGetAllOperationFactory(nameWithPrefix, keySet, policy);
    }

    @Override
    public OperationFactory createLoadAllOperationFactory(Set<Data> keySet, boolean replaceExistingValues) {
        return new CacheLoadAllOperationFactory(nameWithPrefix, keySet, replaceExistingValues);
    }

    @Override
    public OperationFactory createClearOperationFactory() {
        return new CacheClearOperationFactory(nameWithPrefix);
    }

    @Override
    public OperationFactory createRemoveAllOperationFactory(Set<Data> keySet, Integer completionId) {
        return new CacheRemoveAllOperationFactory(nameWithPrefix, keySet, completionId);
    }

    @Override
    public OperationFactory createSizeOperationFactory() {
        return new CacheSizeOperationFactory(nameWithPrefix);
    }
}
