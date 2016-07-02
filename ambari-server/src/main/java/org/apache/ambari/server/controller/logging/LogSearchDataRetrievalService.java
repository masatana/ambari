/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.server.controller.logging;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.AbstractService;
import com.google.inject.Inject;
import org.apache.ambari.server.AmbariService;
import org.apache.ambari.server.configuration.Configuration;
import org.apache.ambari.server.controller.AmbariManagementController;
import org.apache.ambari.server.controller.AmbariServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The {@link LogSearchDataRetrievalService} is an Ambari Service that
 *   is used by the Ambari LogSearch integration code to obtain response
 *   data from the LogSearch server.
 *
 * In order to improve the performance of the LogSearch integration layer in
 *   Ambari, this service implements the following:
 *
 *  <ul>
 *    <li>A cache for LogSearch data that typically is returned by the LogSearch REST API</li>
 *    <li>Implements the remote request for LogSearch data not found in the cache on a separate
 *        thread, which keeps the request from affecting the overall performance of the
 *        Ambari REST API</li>
 *  </ul>
 *
 *  As with other services annotated with {@link AmbariService}, this class may be
 *    injected in order to obtain cached access to the LogSearch responses.
 *
 *  Caches are initially empty in this implementation, and a remote request
 *    to the LogSearch server will be made upon the first request for a given
 *    response.
 *
 *
 */
@AmbariService
public class LogSearchDataRetrievalService extends AbstractService {

  private static Logger LOG = LoggerFactory.getLogger(LogSearchDataRetrievalService.class);

  @Inject
  private Configuration configuration;

  /**
   * A Cache of host+component names to a set of log files associated with
   *  that Host/Component combination.  This data is retrieved from the
   *  LogSearch server, but cached here for better performance.
   */
  private Cache<String, Set<String>> logFileNameCache;

  /**
   * a Cache of host+component names to a generated URI that
   *  can be used to access the "tail" of a given log file.
   *
   * This data is generated by ambari-server, but cached here to
   *  avoid re-creating these strings upon multiple calls to the
   *  associated HostComponent resource.
   */
  private Cache<String, String> logFileTailURICache;

  /**
   * Executor instance to be used to run REST queries against
   * the LogSearch service.
   */
  private Executor executor;

  @Override
  protected void doStart() {

    LOG.debug("Initializing caches");
    // initialize the log file name cache
    logFileNameCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();
    // initialize the log file tail URI cache
    logFileTailURICache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();

    // initialize the Executor
    executor = Executors.newSingleThreadExecutor();
  }

  @Override
  protected void doStop() {
    LOG.debug("Invalidating LogSearch caches");
    // invalidate the cache
    logFileNameCache.invalidateAll();

    logFileTailURICache.invalidateAll();
  }

  /**
   * This method attempts to obtain the log file names for the specified component
   *   on the specified host.  A cache lookup is first attempted. If the cache does not contain
   *   this data, an asynchronous task is launched in order to make the REST request to
   *   the LogSearch server to obtain this data.
   *
   * Once the data is available in the cache, subsequent calls for a given Host/Component
   *   combination should return non-null.
   *
   * @param component the component name
   * @param host the host name
   * @param cluster the cluster name
   *
   * @return a Set<String> that includes the log file names associated with this Host/Component
   *         combination, or null if that object does not exist in the cache.
   */
  public Set<String> getLogFileNames(String component, String host, String cluster) {
    String key = generateKey(component, host);

    // check cache for data
    Set<String> cacheResult =
      logFileNameCache.getIfPresent(key);

    if (cacheResult != null) {
      LOG.debug("LogFileNames result for key = {} found in cache", key);
      return cacheResult;
    } else {
      // queue up a thread to make the LogSearch REST request to obtain this information
      LOG.debug("LogFileNames result for key = {} not in cache, queueing up remote request", key);
      startLogSearchFileNameRequest(host, component, cluster);
    }

    return null;
  }

  public String getLogFileTailURI(String baseURI, String component, String host, String cluster) {
    String key = generateKey(component, host);

    String result = logFileTailURICache.getIfPresent(key);
    if (result != null) {
      // return cached result
      return result;
    } else {
      // create URI and add to cache before returning
      LoggingRequestHelper helper =
        new LoggingRequestHelperFactoryImpl().getHelper(getController(), cluster);
      String tailFileURI =
        helper.createLogFileTailURI(baseURI, component, host);

      if (tailFileURI != null) {
        logFileTailURICache.put(key, tailFileURI);
        return tailFileURI;
      }
    }

    return null;
  }

  private void startLogSearchFileNameRequest(String host, String component, String cluster) {
    executor.execute(new LogSearchFileNameRequestRunnable(host, component, cluster));
  }

  private AmbariManagementController getController() {
    return AmbariServer.getController();
  }



  private static String generateKey(String component, String host) {
    return component + "+" + host;
  }


  /**
   * A {@link Runnable} used to make requests to the remote LogSearch server's
   *   REST API.
   *
   * This implementation will update a cache shared with the {@link LogSearchDataRetrievalService},
   *   which can then be used for subsequent requests for the same data.
   *
   */
  private class LogSearchFileNameRequestRunnable implements Runnable {

    private final String host;

    private final String component;

    private final String cluster;

    private LogSearchFileNameRequestRunnable(String host, String component, String cluster) {
      this.host = host;
      this.component = component;
      this.cluster = cluster;
    }

    @Override
    public void run() {
      LOG.debug("LogSearchFileNameRequestRunnable: starting...");
      LoggingRequestHelper helper =
        new LoggingRequestHelperFactoryImpl().getHelper(getController(), cluster);

      if (helper != null) {
        // make request to LogSearch service
        Set<String> logFileNamesResult =
          helper.sendGetLogFileNamesRequest(component, host);

        // update the cache if result is available
        if (logFileNamesResult != null) {
          LOG.debug("LogSearchFileNameRequestRunnable: request was successful, updating cache");
          logFileNameCache.put(generateKey(component, host), logFileNamesResult);
        } else {
          LOG.debug("LogSearchFileNameRequestRunnable: remote request was not successful");
        }
      } else {
        LOG.debug("LogSearchFileNameRequestRunnable: request helper was null.  This may mean that LogSearch is not available, or could be a potential connection problem.");
      }
    }
  }
}
