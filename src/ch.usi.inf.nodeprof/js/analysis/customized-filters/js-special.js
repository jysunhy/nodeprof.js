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
 //DO NOT INSTRUMENT
((function(sandbox){

  var internals = new Set();
  var builtins = new Set();
  var mute = false;
  var entered = false;

  function NodeInternal() {
    const analysis = 'node-console';
    this.getField = function(iid, base, offset, val, isComputed, isOpAssign, isMethodCall) {
      console.log("%s: getField: %s / %s / %d", analysis, offset, J$.iidToLocation(iid), arguments.length);
    };
    this.functionEnter = function (iid, f, dis, args) {
      if (f.name == '' || mute)
        return;
      console.log("%s: functionEnter: %s / %s / %d", analysis, f.name, J$.iidToLocation(iid), arguments.length);
      entered = true;
    };
    this.endExecution = function () {
      if (!entered)
        return;
      mute = true;
      console.log(internals);
    };
  }
  sandbox.addAnalysis(new NodeInternal(), function filter(source) {
    if (source.internal && source.name.includes('console')) {
      internals.add(source.name);
      return true;
    }
  });

  function BI() {
    const analysis = 'builtin';
    this.builtinEnter = function(builtinName, func, base, args){
      if(builtinName){
        builtins.add(builtinName);
      }
    }
    this.endExecution = function () {
      if (!entered)
        return;
      mute = true;
      console.log([...builtins].filter(x => x.includes('create')));
    };
  }
  sandbox.addAnalysis(new BI(), function filter(source) {
    if (source.name === '<builtin>') {
      return true;
    }
  });
}
)(J$));
