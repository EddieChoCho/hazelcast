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

package com.hazelcast.jet.impl.statemachine.applicationmaster.processors;

import com.hazelcast.jet.dag.Vertex;
import com.hazelcast.jet.impl.Dummy;
import com.hazelcast.jet.impl.application.ApplicationContext;
import com.hazelcast.jet.impl.application.ExecutorContext;
import com.hazelcast.jet.impl.container.ApplicationMaster;
import com.hazelcast.jet.impl.container.ContainerPayloadProcessor;
import com.hazelcast.jet.impl.container.ProcessingContainer;
import com.hazelcast.jet.impl.executor.Task;
import com.hazelcast.jet.impl.statemachine.container.requests.ContainerExecuteRequest;
import com.hazelcast.logging.ILogger;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ExecuteApplicationProcessor implements ContainerPayloadProcessor<Dummy> {
    private final long secondsToAwait;
    private final ExecutorContext executorContext;
    private final ApplicationMaster applicationMaster;
    private final ApplicationContext applicationContext;
    private final ILogger logger;

    public ExecuteApplicationProcessor(ApplicationMaster applicationMaster) {
        this.applicationMaster = applicationMaster;
        applicationContext = applicationMaster.getApplicationContext();
        secondsToAwait = applicationContext.getApplicationConfig().getSecondsToAwait();
        executorContext = applicationContext.getExecutorContext();
        logger = applicationContext.getNodeEngine().getLogger(ExecuteApplicationProcessor.class);
    }

    @Override
    public void process(Dummy payload) throws Exception {
        applicationMaster.registerExecution();

        startContainers();

        List<Task> networkTasks = applicationContext.getExecutorContext().getNetworkTasks();
        List<Task> processingTasks = applicationContext.getExecutorContext().getProcessingTasks();

        try {
            executorContext.getNetworkExecutor().submitTaskContext(networkTasks);
            executorContext.getProcessingExecutor().submitTaskContext(processingTasks);
        } catch (Throwable e) {
            try {
                for (Task networkTask : networkTasks) {
                    networkTask.interrupt(e);
                }

            } finally {
                for (Task processingTask : processingTasks) {
                    processingTask.interrupt(e);
                }

            }

            if (e instanceof Exception) {
                throw (Exception) e;
            } else {
                throw (Error) e;
            }
        }
    }

    private void startContainers() throws Exception {
        Iterator<Vertex> iterator = applicationMaster.getDag().getRevertedTopologicalVertexIterator();

        while (iterator.hasNext()) {
            Vertex vertex = iterator.next();
            ProcessingContainer processingContainer = applicationMaster.getContainerByVertex(vertex);
            processingContainer.handleContainerRequest(new ContainerExecuteRequest()).get(secondsToAwait, TimeUnit.SECONDS);
        }
    }
}
