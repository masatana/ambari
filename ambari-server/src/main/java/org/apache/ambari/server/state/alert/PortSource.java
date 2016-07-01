/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.server.state.alert;

import com.google.gson.annotations.SerializedName;

/**
 * Alert when the source type is defined as {@link SourceType#PORT}
 * <p/>
 * Equality checking for instances of this class should be executed on every
 * member to ensure that reconciling stack differences is correct.
 */
public class PortSource extends Source {

  @SerializedName("uri")
  private String m_uri = null;

  @SerializedName("default_port")
  private int m_port = 0;

  @SerializedName("socket_command")
  private String m_command = null;

  /**
   * @return the URI to check for a valid port
   */
  public String getUri() {
    return m_uri;
  }

  /**
   * @param uri
   *          the URI to check for a valid port
   */
  public void setUri(String uri) {
    m_uri = uri;
  }

  /**
   * @return the port to check on the given URI.
   */
  public int getPort() {
    return m_port;
  }

  /**
   * @param port
   *          the port to check on the given URI.
   */
  public void setPort(int port) {
    m_port = port;
  }

  /**
   * @return the command to check on the given URI.
   */
  public String getCommand() {
    return m_command;
  }

  /**
   * @param command
   *          the command to check on the given URI.
   */
  public void setCommand(String command) {
    m_command = command;
  }

  /**
   *
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + m_port;
    result = prime * result + ((m_uri == null) ? 0 : m_uri.hashCode());

    return result;
  }

  /**
   *
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!super.equals(obj)) {
      return false;
    }

    if (getClass() != obj.getClass()) {
      return false;
    }

    PortSource other = (PortSource) obj;

    if (m_port != other.m_port) {
      return false;
    }

    if (m_uri == null) {
      if (other.m_uri != null) {
        return false;
      }
    } else if (!m_uri.equals(other.m_uri)) {
      return false;
    }

    return true;
  }

}
