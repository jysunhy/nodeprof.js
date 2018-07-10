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
package ch.usi.inf.nodeprof.handlers;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.regex.RegexBodyNode;
import com.oracle.truffle.regex.RegexRootNode;

/**
 * Abstract event handler for function roots
 */
public abstract class FunctionRootEventHandler extends BaseEventHandlerNode {
    protected final String funcName = context.getInstrumentedNode().getRootNode().getName();
    protected final boolean isRegExp = context.getInstrumentedNode().getRootNode() instanceof RegexRootNode || context.getInstrumentedNode() instanceof RegexBodyNode;
    protected final boolean isBuiltin = context.getInstrumentedNode() instanceof JSBuiltinNode;

    protected final String builtinName;

    public FunctionRootEventHandler(EventContext context) {
        super(context);
        if (isBuiltin) {
            builtinName = getAttribute("name").toString();
        } else {
            builtinName = null;
        }
    }

    public Object getReceiver(VirtualFrame frame) {
        return frame.getArguments()[0];
    }

    public boolean isRegularExpression() {
        return this.isRegExp;
    }

    public boolean isBuiltin() {
        return this.isBuiltin;
    }

    public Object getBuiltinName() {
        return this.isBuiltin ? this.builtinName : Undefined.instance;
    }

    public Object getFunction(VirtualFrame frame) {
        return frame.getArguments()[1];
    }

    public Object[] getArguments(VirtualFrame frame) {
        return frame.getArguments();
    }

    public Object getArgument(VirtualFrame frame, int index) {
        return getArguments(frame)[2 + index];
    }

    @Override
    public boolean isLastIndex(int inputCount, int index) {
        return index == -1;
    }

    public String getFunctionName() {
        return this.funcName;
    }
}
