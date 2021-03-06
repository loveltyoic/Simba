/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.remote;

import java.util.List;

import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 *
 */
public class RemoteAngularObjectRegistry extends AngularObjectRegistry {
  Logger logger = LoggerFactory.getLogger(RemoteAngularObjectRegistry.class);
  private InterpreterGroup interpreterGroup;

  public RemoteAngularObjectRegistry(String interpreterId,
      AngularObjectRegistryListener listener,
      InterpreterGroup interpreterGroup) {
    super(interpreterId, listener);
    this.interpreterGroup = interpreterGroup;
  }

  private RemoteInterpreterProcess getRemoteInterpreterProcess() {
    if (interpreterGroup.size() == 0) {
      throw new RuntimeException("Can't get remoteInterpreterProcess");
    }
    Interpreter p = interpreterGroup.get(0);
    while (p instanceof WrappedInterpreter) {
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }

    if (p instanceof RemoteInterpreter) {
      return ((RemoteInterpreter) p).getInterpreterProcess();
    } else {
      throw new RuntimeException("Can't get remoteInterpreterProcess");
    }
  }

  /**
   * When ZeppelinServer side code want to add angularObject to the registry,
   * this method should be used instead of add()
   * @param name
   * @param o
   * @param noteId
   * @return
   */
  public AngularObject addAndNotifyRemoteProcess(String name, Object o, String noteId) {
    Gson gson = new Gson();
    RemoteInterpreterProcess remoteInterpreterProcess = getRemoteInterpreterProcess();
    if (!remoteInterpreterProcess.isRunning()) {
      return null;
    }

    Client client = null;
    try {
      client = remoteInterpreterProcess.getClient();
      client.angularObjectAdd(name, noteId, gson.toJson(o));
      return super.add(name, o, noteId, true);
    } catch (Exception e) {
      logger.error("Error", e);
    } finally {
      if (client != null) {
        remoteInterpreterProcess.releaseClient(client);
      }
    }
    return null;
  }

  /**
   * When ZeppelinServer side code want to remove angularObject from the registry,
   * this method should be used instead of remove()
   * @param name
   * @param noteId
   * @param emit
   * @return
   */
  public AngularObject removeAndNotifyRemoteProcess(String name, String noteId) {
    RemoteInterpreterProcess remoteInterpreterProcess = getRemoteInterpreterProcess();
    if (!remoteInterpreterProcess.isRunning()) {
      return null;
    }

    Client client = null;
    try {
      client = remoteInterpreterProcess.getClient();
      client.angularObjectRemove(name, noteId);
      return super.remove(name, noteId);
    } catch (Exception e) {
      logger.error("Error", e);
    } finally {
      if (client != null) {
        remoteInterpreterProcess.releaseClient(client);
      }
    }
    return null;
  }
  
  public void removeAllAndNotifyRemoteProcess(String noteId) {
    List<AngularObject> all = getAll(noteId);
    for (AngularObject ao : all) {
      removeAndNotifyRemoteProcess(ao.getName(), noteId);
    }
  }

  @Override
  protected AngularObject createNewAngularObject(String name, Object o, String noteId) {
    RemoteInterpreterProcess remoteInterpreterProcess = getRemoteInterpreterProcess();
    if (remoteInterpreterProcess == null) {
      throw new RuntimeException("Remote Interpreter process not found");
    }
    return new RemoteAngularObject(name, o, noteId, getInterpreterGroupId(),
        getAngularObjectListener(),
        getRemoteInterpreterProcess());
  }
}
