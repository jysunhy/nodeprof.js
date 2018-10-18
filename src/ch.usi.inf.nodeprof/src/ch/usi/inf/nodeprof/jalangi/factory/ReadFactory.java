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
package ch.usi.inf.nodeprof.jalangi.factory;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.object.DynamicObject;

import ch.usi.inf.nodeprof.handlers.BaseEventHandlerNode;
import ch.usi.inf.nodeprof.handlers.PropertyReadEventHandler;
import ch.usi.inf.nodeprof.handlers.VarReadEventHandler;

public class ReadFactory extends AbstractFactory {

    private final boolean isProperty;

    public ReadFactory(Object jalangiAnalysis, DynamicObject post,
                    boolean isProperty) {
        super("read", jalangiAnalysis, null, post, -1, 5);
        this.isProperty = isProperty;
        setPostArguments(4, true);// TODO, isScriptLocal
    }

    @Override
    public BaseEventHandlerNode create(EventContext context) {
        if (!isProperty) {
            return new VarReadEventHandler(context) {
                @Child DirectCallNode postCall = createPostCallNode();

                @Override
                public void executePre(VirtualFrame frame, Object[] inputs) {

                }

                @Override
                public void executePost(VirtualFrame frame, Object result,
                                Object[] inputs) {
                    if (post != null && !this.isInternal()) {
                        setPostArguments(0, getSourceIID());
                        setPostArguments(1, getName());
                        setPostArguments(2, convertResult(result));
                        setPostArguments(3, false);// isGlobal
                        directCall(postCall, false, getSourceIID());
                    }
                }
            };
        } else {
            return new PropertyReadEventHandler(context) {
                @Child DirectCallNode postCall = createPostCallNode();

                @Override
                public void executePre(VirtualFrame frame, Object[] inputs) {

                }

                @Override
                public void executePost(VirtualFrame frame, Object result,
                                Object[] inputs) {
                    if (post != null && this.isGlobal(inputs)) {
                        setPostArguments(0, getSourceIID());
                        setPostArguments(1, getProperty());
                        setPostArguments(2, convertResult(result));
                        setPostArguments(3, true);// isGlobal
                        directCall(postCall, false, getSourceIID());
                    }
                }

            };
        }
    }

}
