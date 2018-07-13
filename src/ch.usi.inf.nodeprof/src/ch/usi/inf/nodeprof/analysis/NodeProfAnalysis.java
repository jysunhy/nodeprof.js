/*******************************************************************************
 * Copyright 2018 Dynamic Analysis Group, Università della Svizzera Italiana (USI)
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package ch.usi.inf.nodeprof.analysis;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;

import ch.usi.inf.nodeprof.ProfiledTagEnum;
import ch.usi.inf.nodeprof.handlers.BaseEventHandlerNode;
import ch.usi.inf.nodeprof.handlers.MultiEventHandler;
import ch.usi.inf.nodeprof.jalangi.NodeProfJalangi;
import ch.usi.inf.nodeprof.utils.GlobalConfiguration;
import ch.usi.inf.nodeprof.utils.Logger;
import com.oracle.truffle.api.source.Source;

public abstract class NodeProfAnalysis {
    private final Env env;
    private final Instrumenter instrumenter;
    private final String name;

    @TruffleBoundary
    public NodeProfAnalysis(String name, Instrumenter instrumenter, Env env) {
        this.name = name;
        this.instrumenter = instrumenter;
        this.env = env;
        this.handlers = new HashMap<>();
    }

    public Instrumenter getInstrumenter() {
        return this.instrumenter;
    }

    public Env getEnv() {
        return this.env;
    }

    public String getName() {
        return name;
    }

    private static final int maxAnalyses = 100;
    private static int numAnalysis = 0;
    private static final NodeProfAnalysis[] enabled = new NodeProfAnalysis[maxAnalyses];

    public static NodeProfAnalysis[] getEnabledAnalyses() {
        return enabled;
    }

    @TruffleBoundary
    protected static NodeProfAnalysis getOrCreateAnalysis(String analysisClass, Instrumenter instrumenter,
                    Env env) {
        String[] packageHeaders = {"", "ch.usi.inf.nodeprof.", "ch.usi.inf.nodeprof.examples.", "ch.usi.inf.nodeprof.jalangi."};
        /**
         * create the NodeProfAnalysis using reflection
         */
        for (String packageHeader : packageHeaders) {
            try {
                NodeProfAnalysis newAnalysis = (NodeProfAnalysis) Class.forName(
                                packageHeader + analysisClass).getConstructor(Instrumenter.class, Env.class).newInstance(instrumenter, env);
                try {
                    newAnalysis.onLoad();
                } catch (Exception e) {
                    Logger.error("error happens in loading the analysis " + analysisClass);
                    e.printStackTrace();
                    System.exit(-1);
                }
                newAnalysis.initCallbacks();
                newAnalysis.analysisReady();
                return newAnalysis;
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
                /**
                 * continue if it is related to reflection because the given analysis name can be
                 * wrong
                 */
                continue;
            }

        }
        return null;
    }

    /**
     * executed after constructor, could throw an exception
     */
    public void onLoad() throws Exception {

    }

    public static void addAnalysis(NodeProfAnalysis analysis) {
        enabled[numAnalysis++] = analysis;
    }

    @TruffleBoundary
    public static void enableAnalysis(Instrumenter instrumenter, Env env, String analysisClass) {
        if (!analysisClass.isEmpty()) {
            NodeProfAnalysis analysis;
            if (analysisClass.contains("NodeProfJalangi")) {
                analysis = new NodeProfJalangi(instrumenter, env);
                try {
                    analysis.onLoad();
                } catch (Exception e) {
                    Logger.error("error happens in loading the analysis " + analysisClass);
                    e.printStackTrace();
                    // System.exit(-1);
                }
            } else {
                analysis = getOrCreateAnalysis(analysisClass, instrumenter, env);
            }
            if (analysis != null) {
                addAnalysis(analysis);
            } else {
                Logger.debug("loading analysis... " + analysisClass);
            }
        }
    }

    public AnalysisFilterSourceList getFilter() {
        return AnalysisFilterSourceList.getDefault();
    }

    /**
     * register callbacks when constructing the analysis if they are pre-known
     */
    public abstract void initCallbacks();

    /**
     * Clear all states of an analysis. For example, when we run the analysis for many rounds, we
     * need to reset all states so that the performance and results of the analysis won't get
     * affected.
     */
    public abstract void onClear();

    /**
     * Result dump
     */
    public abstract void printResult();

    /**
     * Callback at the end of the execution for the analysis
     *
     * first print the result and then clean the states
     */

    @TruffleBoundary
    public void onDispose() {
        printResult();
        onClear();
    }

    /**
     *
     * @param result the result to be compared
     * @return true if no error found during the analysis
     */
    public boolean checkResult(Object result) {
        return true;
    }

    /**
     * We cache the callbacks registered via NodeProfAnalysis.onCallback(), and enable them together
     * with onReady(). When multiple callbacks together are enabled together, the ordering is
     * guaranteed.
     */
    private HashMap<ProfiledTagEnum, ArrayList<AnalysisFactory<BaseEventHandlerNode>>> handlers;

    @TruffleBoundary
    public void onCallback(ProfiledTagEnum e,
                    AnalysisFactory<BaseEventHandlerNode> factory) {
        if (GlobalConfiguration.DEBUG) {
            Logger.debug("adding callback for tag " + e.getTag().getSimpleName());
        }
        if (!handlers.containsKey(e)) {
            handlers.put(e, new ArrayList<AnalysisFactory<BaseEventHandlerNode>>());
        }
        handlers.get(e).add(factory);
        return;
    }

    /**
     * enable all the callbacks not yet enabled using the default analysis filter
     */
    @TruffleBoundary
    public void analysisReady() {
        assert (getFilter() != null);
        analysisReady(getFilter(), handlers);
    }

    /**
     * enable all the callbacks not yet enabled using the provided source filter
     */

    @TruffleBoundary
    public void analysisReady(AnalysisFilterBase filter) {
        analysisReady(filter, handlers);
    }

    @TruffleBoundary
    private void analysisReady(AnalysisFilterBase sourceFilter, HashMap<ProfiledTagEnum, ArrayList<AnalysisFactory<BaseEventHandlerNode>>> handlerMapping) {
        // check if any new callback is registered
        if (handlerMapping.size() > 0) {
            ArrayList<Class<?>> nonBuiltinCallbacks = new ArrayList<>();
            for (Entry<ProfiledTagEnum, ArrayList<AnalysisFactory<BaseEventHandlerNode>>> entry : handlerMapping.entrySet()) {
                entry.getKey().usedAnalysis++;
                if (entry.getKey() != ProfiledTagEnum.BUILTIN) {
                    nonBuiltinCallbacks.add(entry.getKey().getTag());
                }
            }

            SourceSectionFilter inputFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class, StandardTags.StatementTag.class).build();

            // A built-in node has also the root tag, so we need a separate factory
            if (handlerMapping.containsKey(ProfiledTagEnum.BUILTIN)) {
                SourceSectionFilter builtinFilter = SourceSectionFilter.newBuilder().tagIs(ProfiledTagEnum.BUILTIN.getTag()).sourceIs(AnalysisFilterSourceList.getBuiltinFilter()).build();
                getInstrumenter().attachExecutionEventFactory(
                                builtinFilter,
                                inputFilter,
                                new ExecutionEventNodeFactory() {
                                    @TruffleBoundary
                                    public ExecutionEventNode create(EventContext context) {
                                        InstrumentableNode instrumentedNode = (InstrumentableNode) context.getInstrumentedNode();
                                        if (instrumentedNode.hasTag(ProfiledTagEnum.BUILTIN.getTag())) {
                                            return createAndSimplifyExecutionEventNode(context, ProfiledTagEnum.BUILTIN, handlerMapping.get(ProfiledTagEnum.BUILTIN));
                                        } else {
                                            return new ExecutionEventNode() {
                                            };
                                        }
                                    }
                                });
            }

            if (nonBuiltinCallbacks.size() > 0) {
                Class<?>[] eventTags = new Class<?>[nonBuiltinCallbacks.size()];
                nonBuiltinCallbacks.toArray(eventTags);
                SourceSectionFilter eventFilter = SourceSectionFilter.newBuilder().tagIs(eventTags).sourceIs(sourceFilter).build();

                getInstrumenter().attachExecutionEventFactory(
                                eventFilter,
                                inputFilter,
                                new ExecutionEventNodeFactory() {

                                    @Override
                                    @TruffleBoundary
                                    public ExecutionEventNode create(EventContext context) {
                                        int count = 0;
                                        InstrumentableNode instrumentedNode = (InstrumentableNode) context.getInstrumentedNode();
                                        for (Entry<ProfiledTagEnum, ArrayList<AnalysisFactory<BaseEventHandlerNode>>> entry : handlerMapping.entrySet()) {
                                            if (instrumentedNode.hasTag(entry.getKey().getTag()) && entry.getKey() != ProfiledTagEnum.BUILTIN) {
                                                count += 1;
                                            }
                                        }
                                        // a node should never have two tags the same time(except for the built-in)
                                        if (count > 1) {
                                            Logger.error("a node has more than 1 profiling tags!!");
                                            String tags = "";
                                            for (Entry<ProfiledTagEnum, ArrayList<AnalysisFactory<BaseEventHandlerNode>>> entry : handlerMapping.entrySet()) {
                                                if (instrumentedNode.hasTag(entry.getKey().getTag()) && entry.getKey() != ProfiledTagEnum.BUILTIN) {
                                                    tags += entry.getKey().getTag().getSimpleName() + " ";
                                                }
                                            }
                                            Logger.error(context.getInstrumentedSourceSection(), context.getInstrumentedNode().getClass().getName() + " has tags: " + tags);
                                        }

                                        assert (count <= 1);
                                        for (Entry<ProfiledTagEnum, ArrayList<AnalysisFactory<BaseEventHandlerNode>>> entry : handlerMapping.entrySet()) {
                                            try {
                                                ProfiledTagEnum key = entry.getKey();
                                                Source source = context.getInstrumentedSourceSection().getSource();
                                                if (instrumentedNode.hasTag(key.getTag()) && key != ProfiledTagEnum.BUILTIN && sourceFilter.testTag(source, key)) {
                                                    return createAndSimplifyExecutionEventNode(context, entry.getKey(), entry.getValue());
                                                }
                                            } catch (Exception exception) {
                                                exception.printStackTrace();
                                            }
                                        }
                                        // if there is no handler for this node, return an empty
                                        // ExecutionEventNode which should bring zero overhead after
                                        // compilation
                                        return new ExecutionEventNode() {
                                        };
                                    }

                                });
            }
        }
        this.handlers = new HashMap<>();
    }

    /**
     * create the ExecutionEventNode with the proper handler
     *
     * @param context
     * @param key
     * @param listOfHandlers
     * @return a ProfilerExecutionEventNode or an empty ExecutionEventNode
     */
    @TruffleBoundary
    private static ExecutionEventNode createAndSimplifyExecutionEventNode(EventContext context, ProfiledTagEnum key, ArrayList<AnalysisFactory<BaseEventHandlerNode>> listOfHandlers) {
        BaseEventHandlerNode handler = null;
        if (listOfHandlers.size() == 1) {
            // create the handler using the only factory
            handler = listOfHandlers.get(0).create(context);
        } else {
            // create the handlers one by one
            BaseEventHandlerNode[] components = new BaseEventHandlerNode[listOfHandlers.size()];
            int numNonEmpty = 0;
            BaseEventHandlerNode last = null;
            for (int i = 0; i < components.length; i++) {
                components[i] = listOfHandlers.get(i).create(context);
                if (components[i] != null) {
                    numNonEmpty++;
                    last = components[i];
                }
            }
            if (numNonEmpty == 0) {
                handler = null;
            } else if (numNonEmpty == 1) {
                handler = last;
            } else {
                BaseEventHandlerNode[] handlers = new BaseEventHandlerNode[numNonEmpty];
                int cnt = 0;
                for (int i = 0; i < components.length; i++) {
                    if (components[i] != null) {
                        handlers[cnt++] = components[i];
                    }
                }
                handler = MultiEventHandler.create(handlers);
            }
        }
        if (handler != null) {
            return new ProfilerExecutionEventNode(key, context,
                            handler);
        } else {
            return new ExecutionEventNode() {
            };
        }
    }

    /**
     * a quick way to create instrumentatino for all events via a simple factory
     *
     * @param factory
     */

    @TruffleBoundary
    public void onAllCallback(ExecutionEventNodeFactory factory) {
        onAllCallback(factory, getFilter());
    }

    @TruffleBoundary
    public void onAllCallback(ExecutionEventNodeFactory factory,
                    SourcePredicate sourcePredicate) {
        getInstrumenter().attachExecutionEventFactory(
                        SourceSectionFilter.newBuilder().tagIs(ProfiledTagEnum.getTags()).sourceIs(sourcePredicate).build(),
                        SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class, StandardTags.StatementTag.class).build(),
                        factory);
    }
}
