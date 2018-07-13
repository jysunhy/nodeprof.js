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
package ch.usi.inf.nodeprof.test.examples.tests;

import org.junit.Test;

import com.oracle.truffle.api.instrumentation.Instrumenter;

import ch.usi.inf.nodeprof.ProfiledTagEnum;
import ch.usi.inf.nodeprof.analysis.AnalysisFilterSourceList;
import ch.usi.inf.nodeprof.test.AnalysisEventsVerifier;
import ch.usi.inf.nodeprof.test.BasicAnalysisTest;
import ch.usi.inf.nodeprof.test.TestableNodeProfAnalysis;
import ch.usi.inf.nodeprof.test.examples.NonContiguousArray;

public class NonContiguousArrayTest extends BasicAnalysisTest {

    @Override
    public TestableNodeProfAnalysis getAnalysis(Instrumenter _instrumenter) {
        return new NonContiguousArray(_instrumenter, null);
    }

    @Test
    public void testBasic() {
        context.eval("js", "var a = Array(); a[1]=0;");
        AnalysisEventsVerifier verifier = new AnalysisEventsVerifier(this.analysis.getAnalysisEvents()) {
            @Override
            public void verify() {
                dequeueAndVerifyEvent("EW_ARRAY_INT", 1, ProfiledTagEnum.ELEMENT_WRITE, 1, 0);
                dequeueAndVerifyEvent("REPORT", 1, ProfiledTagEnum.ELEMENT_WRITE);
                finish();
            }
        };
        verifier.verify();
    }

    @Test
    public void testMore() {
        context.eval("js", "var a = [1,2,3]; a[\"1\"]=0; a[5]=4; a[6]=0;");
        AnalysisEventsVerifier verifier = new AnalysisEventsVerifier(this.analysis.getAnalysisEvents()) {
            @Override
            public void verify() {
                dequeueAndVerifyEvent("EW_ARRAY_INT", 1, ProfiledTagEnum.ELEMENT_WRITE, 1, 3);
                dequeueAndVerifyEvent("EW_ARRAY_INT", 1, ProfiledTagEnum.ELEMENT_WRITE, 5, 3);
                dequeueAndVerifyEvent("REPORT", 1, ProfiledTagEnum.ELEMENT_WRITE);
                dequeueAndVerifyEvent("EW_ARRAY_INT", 1, ProfiledTagEnum.ELEMENT_WRITE, 6, 6);
                finish();
            }
        };
        verifier.verify();
    }

    @Override
    public AnalysisFilterSourceList getFilter() {
        return AnalysisFilterSourceList.makeSingleIncludeFilter("Unnamed");
    }
}
