/*=========================================================================
 *Copyright (c) 2002-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * more patents listed at http://www.pivotal.io/patents.
 *All Rights Reserved.
 *========================================================================
 */
package com.gemstone.gemfire.admin.jmx.internal;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import com.gemstone.gemfire.admin.DistributedSystemConfig;
import com.gemstone.gemfire.admin.internal.InetAddressUtil;
import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.internal.SocketCreator;
import com.gemstone.gemfire.internal.logging.LogService;

/**
 * Creates <code>ServerSockets</code> for JMX adaptors.
 * <p>
 * The interface {@link mx4j.tools.adaptor.AdaptorServerSocketFactory} is
 * implemented in order to support securing of {@link 
 * mx4j.tools.adaptor.http.HttpAdaptor}.
 * <p>
 * The interface {@link java.rmi.server.RMIServerSocketFactory} is implemented
 * to support the securing of {@link 
 * javax.management.remote.JMXConnectorServer}.  See {@link
 * javax.management.remote.rmi.RMIConnectorServer} for the actual subclass that
 * is used for the JMX RMI adaptor.
 * <p>
 * Complete info on JSSE, including debugging, can be found at
 * <a href="http://java.sun.com/j2se/1.4.2/docs/guide/security/jsse/JSSERefGuide.html">
 * http://java.sun.com/j2se/1.4.2/docs/guide/security/jsse/JSSERefGuide.html</a>
 *
 * @author    Kirk Lund
 * @since     3.5 (old name was SSLAdaptorServerSocketFactory)
 */
public class MX4JServerSocketFactory 
implements mx4j.tools.adaptor.AdaptorServerSocketFactory,
           java.rmi.server.RMIServerSocketFactory {

  private static final Logger logger = LogService.getLogger();
  
  private static final int DEFAULT_BACKLOG = 50;
  
  private final SocketCreator socketCreator;
  private String bindAddress = DistributedSystemConfig.DEFAULT_BIND_ADDRESS;
  private int backlog = DEFAULT_BACKLOG;
  
  /**
   * Constructs new instance of MX4JServerSocketFactory.
   * 
   * @param useSSL
   *          true if ssl is to be enabled
   * @param needClientAuth
   *          true if client authentication is required
   * @param protocols
   *          space-delimited list of ssl protocols to use
   * @param ciphers
   *          space-delimited list of ssl ciphers to use
   * @param gfsecurityProps
   *          vendor properties passed in through gfsecurity.properties
   */
  public MX4JServerSocketFactory(boolean useSSL,
                                       boolean needClientAuth,
                                       String protocols,
                                       String ciphers,
                                       Properties gfsecurityProps) {
    if (protocols == null || protocols.length() == 0) {
      protocols = DistributionConfig.DEFAULT_SSL_PROTOCOLS;
    }
    if (ciphers == null || ciphers.length() == 0) {
      ciphers = DistributionConfig.DEFAULT_SSL_CIPHERS;
    }
    this.socketCreator = SocketCreator.createNonDefaultInstance(
        useSSL, needClientAuth, protocols, ciphers, gfsecurityProps);
  }

  /**
   * Constructs new instance of MX4JServerSocketFactory.
   * 
   * @param useSSL
   *          true if ssl is to be enabled
   * @param needClientAuth
   *          true if client authentication is required
   * @param protocols
   *          space-delimited list of ssl protocols to use
   * @param ciphers
   *          space-delimited list of ssl ciphers to use
   * @param bindAddress
   *          host or address to bind to (bind-address)
   * @param backlog
   *          how many connections are queued
   * @param gfsecurityProps
   *          vendor properties passed in through gfsecurity.properties
   */
  public MX4JServerSocketFactory(boolean useSSL,
                                       boolean needClientAuth,
                                       String protocols,
                                       String ciphers,
                                       String bindAddress, // optional for RMI impl
                                       int backlog, // optional for RMI impl
                                       Properties gfsecurityProps) {
    this(useSSL, needClientAuth, protocols, ciphers, gfsecurityProps);
    this.bindAddress = bindAddress;
    this.backlog = backlog;
  }

  // -------------------------------------------------------------------------
  //   mx4j.tools.adaptor.AdaptorServerSocketFactory impl...
  // -------------------------------------------------------------------------
  
	public ServerSocket createServerSocket(int port, 
                                         int backlog, 
                                         String bindAddress) throws IOException {
          if ("".equals(bindAddress)) {
            return socketCreator.createServerSocket(
              port, backlog);

          } else {
            return socketCreator.createServerSocket(
              port, backlog, InetAddressUtil.toInetAddress(bindAddress));
          }
  }
  
  // -------------------------------------------------------------------------
  //   java.rmi.server.RMIServerSocketFactory impl...
  // -------------------------------------------------------------------------
  
   public ServerSocket createServerSocket(int port) throws IOException {
     ServerSocket sock = null;    
     if ("".equals(bindAddress)) {
       sock = socketCreator.createServerSocket(port, this.backlog);
     } else {
       sock = socketCreator.createServerSocket(
               port, this.backlog, InetAddressUtil.toInetAddress(this.bindAddress));
     }
     
     if (logger.isDebugEnabled()) {
      logger.debug("MX4JServerSocketFactory RMIServerSocketFactory, INetAddress {}, LocalPort {}, LocalSocketAddress {}",
          sock.getInetAddress(), sock.getLocalPort(), sock.getLocalSocketAddress());
     }
     return sock;
   }
   
}

