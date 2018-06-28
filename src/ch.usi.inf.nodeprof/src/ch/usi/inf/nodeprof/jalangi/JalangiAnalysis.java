/*******************************************************************************
 * Copyright 2018 Dynamic Analysis Group, Università della Svizzera Italiana (USI)
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
package ch.usi.inf.nodeprof.jalangi;

import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.object.DynamicObject;

import ch.usi.inf.nodeprof.ProfiledTagEnum;
import ch.usi.inf.nodeprof.jalangi.factory.BinaryFactory;
import ch.usi.inf.nodeprof.jalangi.factory.BranchFactory;
import ch.usi.inf.nodeprof.jalangi.factory.BuiltinFactory;
import ch.usi.inf.nodeprof.jalangi.factory.ConditionalFactory;
import ch.usi.inf.nodeprof.jalangi.factory.EvalFactory;
import ch.usi.inf.nodeprof.jalangi.factory.GetElementFactory;
import ch.usi.inf.nodeprof.jalangi.factory.GetFieldFactory;
import ch.usi.inf.nodeprof.jalangi.factory.InvokeFactory;
import ch.usi.inf.nodeprof.jalangi.factory.LiteralFactory;
import ch.usi.inf.nodeprof.jalangi.factory.LoopFactory;
import ch.usi.inf.nodeprof.jalangi.factory.PutElementFactory;
import ch.usi.inf.nodeprof.jalangi.factory.PutFieldFactory;
import ch.usi.inf.nodeprof.jalangi.factory.ReadFactory;
import ch.usi.inf.nodeprof.jalangi.factory.RootFactory;
import ch.usi.inf.nodeprof.jalangi.factory.UnaryFactory;
import ch.usi.inf.nodeprof.jalangi.factory.WriteFactory;
import ch.usi.inf.nodeprof.utils.GlobalConfiguration;
import ch.usi.inf.nodeprof.utils.GlobalObjectCache;
import ch.usi.inf.nodeprof.utils.Logger;

/**
 * Java representation of the Jalangi analysis object created in Jalangi ChainedAnalysisNoCheck
 */
public class JalangiAnalysis {
    /**
     * mapping from the callback names to the function
     */
    private final HashMap<String, DynamicObject> callbacks;
    /**
     * The Jalangi analysis object
     *
     * this will be used as the receiver of the callback events
     *
     */
    public final Object jsAnalysis;

    /**
     * The instrument
     */
    final NodeProfJalangi instrument;

    @TruffleBoundary
    public JalangiAnalysis(NodeProfJalangi nodeprofJalangi, Object jsAnalysis) {
        this.instrument = nodeprofJalangi;
        this.jsAnalysis = jsAnalysis;
        this.callbacks = new HashMap<>();
    }

    @TruffleBoundary
    public void onReady() {
        if (GlobalConfiguration.DEBUG) {
            Logger.debug("analysis is ready " + callbacks.keySet());
        }
        if (this.callbacks.containsKey("invokeFunPre") || callbacks.containsKey("invokeFun")) {
            this.instrument.onCallback(
                            ProfiledTagEnum.INVOKE,
                            new InvokeFactory(this.jsAnalysis, callbacks.get("invokeFunPre"), callbacks.get("invokeFun")));
        }

        if (this.callbacks.containsKey("putFieldPre") || callbacks.containsKey("putField")) {
            this.instrument.onCallback(
                            ProfiledTagEnum.PROPERTY_WRITE,
                            new PutFieldFactory(this.jsAnalysis, callbacks.get("putFieldPre"), callbacks.get("putField")));
            this.instrument.onCallback(
                            ProfiledTagEnum.ELEMENT_WRITE,
                            new PutElementFactory(this.jsAnalysis, callbacks.get("putFieldPre"), callbacks.get("putField")));
        }

        if (this.callbacks.containsKey("getFieldPre") || callbacks.containsKey("getField")) {
            this.instrument.onCallback(
                            ProfiledTagEnum.PROPERTY_READ,
                            new GetFieldFactory(this.jsAnalysis, callbacks.get("getFieldPre"), callbacks.get("getField")));
            this.instrument.onCallback(
                            ProfiledTagEnum.ELEMENT_READ,
                            new GetElementFactory(this.jsAnalysis, callbacks.get("getFieldPre"), callbacks.get("getField")));
        }

        if (this.callbacks.containsKey("read")) {
            this.instrument.onCallback(ProfiledTagEnum.VAR_READ, new ReadFactory(
                            this.jsAnalysis, callbacks.get("read"), false));
            this.instrument.onCallback(ProfiledTagEnum.PROPERTY_READ, new ReadFactory(
                            this.jsAnalysis, callbacks.get("read"), true));
        }

        if (this.callbacks.containsKey("write")) {
            this.instrument.onCallback(ProfiledTagEnum.VAR_WRITE, new WriteFactory(
                            this.jsAnalysis, callbacks.get("write"), false));
            this.instrument.onCallback(ProfiledTagEnum.PROPERTY_WRITE,
                            new WriteFactory(this.jsAnalysis, callbacks.get("write"),
                                            true));
        }

        if (this.callbacks.containsKey("binaryPre") || this.callbacks.containsKey("binary")) {
            this.instrument.onCallback(ProfiledTagEnum.BINARY,
                            new BinaryFactory(this.jsAnalysis,
                                            callbacks.get("binaryPre"),
                                            callbacks.get("binary")));
        }

        if (this.callbacks.containsKey("literal")) {
            this.instrument.onCallback(ProfiledTagEnum.LITERAL, new LiteralFactory(
                            this.jsAnalysis, callbacks.get("literal")));
        }

        if (this.callbacks.containsKey("unaryPre") || this.callbacks.containsKey("unary")) {
            this.instrument.onCallback(ProfiledTagEnum.UNARY,
                            new UnaryFactory(this.jsAnalysis, callbacks.get("unaryPre"),
                                            callbacks.get("unary")));
        }

        if (this.callbacks.containsKey("conditional")) {
            this.instrument.onCallback(ProfiledTagEnum.CF_COND, new ConditionalFactory(
                            this.jsAnalysis, callbacks.get("conditional"), false));
            this.instrument.onCallback(
                            ProfiledTagEnum.BINARY,
                            new ConditionalFactory(this.jsAnalysis, callbacks.get("conditional"), true));
        }

        if (this.callbacks.containsKey("functionEnter") || this.callbacks.containsKey("functionExit")) {
            this.instrument.onCallback(
                            ProfiledTagEnum.ROOT,
                            new RootFactory(this.jsAnalysis, callbacks.get("functionEnter"), callbacks.get("functionExit")));
        }

        /**
         * TODO
         *
         * Eval not tested
         */
        if (this.callbacks.containsKey("evalPre") || this.callbacks.containsKey("evalPost")) {
            this.instrument.onCallback(
                            ProfiledTagEnum.EVAL,
                            new EvalFactory(this.jsAnalysis, callbacks.get("evalPre"), callbacks.get("evalPost")));
        }

        /**
         *
         * new Function("XXX"); not tested
         */
        if (this.callbacks.containsKey("evalFunctionPre") || this.callbacks.containsKey("evalFunctionPost")) {
            this.instrument.onCallback(
                            ProfiledTagEnum.BUILTIN,
                            new BuiltinFactory(this.jsAnalysis, callbacks.get("evalFunctionPre"), callbacks.get("evalFunctionPost"), "Function"));
        }

        /**
         * TODO
         *
         * Loop not tested
         */
        if (this.callbacks.containsKey("loopEnter") || this.callbacks.containsKey("loopExit")) {
            this.instrument.onCallback(
                            ProfiledTagEnum.CF_ROOT,
                            new LoopFactory(this.jsAnalysis, callbacks.get("loopEnter"), callbacks.get("loopExit")));
        }

        /**
         * TODO
         *
         * Branch not tested
         */
        if (this.callbacks.containsKey("branchEnter") || this.callbacks.containsKey("branchExit")) {
            this.instrument.onCallback(
                            ProfiledTagEnum.CF_BRANCH,
                            new BranchFactory(this.jsAnalysis, callbacks.get("branchEnter"), callbacks.get("branchExit")));
        }
    }

    /**
     * register hooks
     *
     * @param name of the hook
     * @callback function to be called for the specified hook
     */
    @TruffleBoundary
    public void registerCallback(Object name, Object callback) {
        TruffleObject original = JavaInterop.asTruffleObject(callback);
        if (original instanceof DynamicObject) {
            if (GlobalConfiguration.DEBUG) {
                Logger.debug("Jalangi registering callback " + name);
            }
            GlobalObjectCache.getInstance().addDynamicObject((DynamicObject) original);
            this.callbacks.put(name.toString(), (DynamicObject) original);
        }
    }
}
