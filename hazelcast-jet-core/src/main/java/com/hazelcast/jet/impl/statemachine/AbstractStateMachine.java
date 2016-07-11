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

package com.hazelcast.jet.impl.statemachine;

import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.jet.JetException;
import com.hazelcast.jet.impl.application.ApplicationContext;
import com.hazelcast.jet.impl.container.RequestPayload;
import com.hazelcast.jet.impl.container.task.AbstractTask;
import com.hazelcast.jet.impl.executor.StateMachineExecutor;
import com.hazelcast.jet.impl.util.BasicCompletableFuture;
import com.hazelcast.jet.impl.util.BooleanHolder;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.spi.NodeEngine;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public abstract class AbstractStateMachine
        <Input extends StateMachineEvent,
                State extends StateMachineState,
                Output extends StateMachineOutput> implements StateMachine<Input, State, Output> {
    protected final String name;
    protected volatile Output output;
    protected volatile State state = defaultState();

    private final ILogger logger;
    private final Map<State, Map<Input, State>> stateTransitionMatrix;
    private final ApplicationContext applicationContext;
    private final StateMachineRequestProcessor<Input> processor;
    private final BlockingQueue<RequestPayload<Input, Output>> eventsQueue =
            new LinkedBlockingDeque<>();

    protected AbstractStateMachine(String name,
                                   Map<State, Map<Input, State>> stateTransitionMatrix,
                                   StateMachineRequestProcessor<Input> processor,
                                   NodeEngine nodeEngine,
                                   ApplicationContext applicationContext) {
        this.name = name;
        this.processor = processor;
        this.applicationContext = applicationContext;
        this.stateTransitionMatrix = stateTransitionMatrix;
        this.logger = Logger.getLogger(StateMachine.class);

        if (nodeEngine != null) {
            getExecutor().addTask(new EventsProcessor(this.eventsQueue));
        }
    }

    protected abstract StateMachineExecutor getExecutor();

    protected abstract State defaultState();

    @Override
    public State currentState() {
        return state;
    }

    public <P> ICompletableFuture<Output> handleRequest(StateMachineRequest<Input, P> request) {
        BasicCompletableFuture<Output> future
                = new BasicCompletableFuture<>(applicationContext.getNodeEngine(), logger);
        RequestPayload<Input, Output> payload =
                new RequestPayload<>(request.getContainerEvent(), future, request.getPayload());

        if (!this.eventsQueue.offer(payload)) {
            throw new JetException("Can't add request to the stateMachine " + name);
        }

        return payload.getFuture();
    }

    protected abstract Output output(Input input, State nextState);

    @Override
    public Output getOutput() {
        return output;
    }

    public ApplicationContext getApplicationContext() {
        return this.applicationContext;
    }

    private class EventsProcessor extends AbstractTask {
        private final Queue<RequestPayload<Input, Output>> requestsQueue;

        EventsProcessor(Queue<RequestPayload<Input, Output>> requestsQueue) {
            this.requestsQueue = requestsQueue;
        }

        @Override
        public boolean execute(BooleanHolder didWorkHolder) {
            RequestPayload<Input, Output> requestHolder = this.requestsQueue.poll();

            try {
                if (requestHolder == null) {
                    didWorkHolder.set(false);
                    return true;
                }

                Input event = requestHolder.getEvent();
                BasicCompletableFuture<Output> future = requestHolder.getFuture();

                try {
                    Map<Input, State> transmissions = stateTransitionMatrix.get(state);

                    if (transmissions == null) {
                        future.setResult(new InvalidEventException(event, state, name));
                        return true;
                    }

                    State nextState = transmissions.get(event);

                    if (nextState != null) {
                        if (processor != null) {
                            processor.processRequest(requestHolder.getEvent(), requestHolder.getPayload());
                        }

                        if (logger.isFineEnabled()) {
                            logger.fine("Transitioned from state=" + state + " to=" + nextState + " on event " + event);
                        }
                        state = nextState;
                        output = output(event, nextState);
                        future.setResult(output);
                    } else {
                        output = output(event, null);
                        Throwable error = new InvalidEventException(event, state, name);
                        logger.warning(error.getMessage(), error);
                        future.setResult(error);
                    }
                } catch (Throwable e) {
                    if (logger != null) {
                        logger.warning(e.getMessage(), e);
                    }

                    future.setResult(e);
                }

                return true;
            } finally {
                didWorkHolder.set(this.requestsQueue.size() > 0);
            }
        }
    }
}
