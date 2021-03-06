package com.gemstone.gemfire.distributed;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.gemstone.gemfire.distributed.AbstractLauncher.Status;
import com.gemstone.gemfire.distributed.ServerLauncher.Builder;
import com.gemstone.gemfire.internal.process.ProcessControllerFactory;
import com.gemstone.gemfire.internal.process.ProcessStreamReader;
import com.gemstone.gemfire.internal.process.ProcessType;
import com.gemstone.gemfire.internal.process.ProcessUtils;
import com.gemstone.gemfire.lang.AttachAPINotFoundException;
import com.gemstone.gemfire.test.junit.categories.IntegrationTest;

/**
 * Subclass of ServerLauncherRemoteDUnitTest which forces the code to not find 
 * the Attach API which is in the JDK tools.jar.  As a result ServerLauncher
 * ends up using the FileProcessController implementation.
 * 
 * @author Kirk Lund
 * @since 8.0
 */
@Category(IntegrationTest.class)
public class ServerLauncherRemoteFileJUnitTest extends ServerLauncherRemoteJUnitTest {
  
  @Before
  public void setUpServerLauncherRemoteFileTest() throws Exception {
    System.setProperty(ProcessControllerFactory.PROPERTY_DISABLE_ATTACH_API, "true");
  }
  
  @After
  public void tearDownServerLauncherRemoteFileTest() throws Exception {   
  }
  
  @Override
  @Test
  /**
   * Override and assert Attach API is NOT found
   */
  public void testIsAttachAPIFound() throws Exception {
    final ProcessControllerFactory factory = new ProcessControllerFactory();
    assertFalse(factory.isAttachAPIFound());
  }
  
  @Override
  @Test
  /**
   * Override because FileProcessController cannot request status with PID
   */
  public void testStatusUsingPid() throws Throwable {
    final List<String> jvmArguments = getJvmArguments();
    jvmArguments.add("-D" + getUniqueName() + "=true");
    
    final List<String> command = new ArrayList<String>();
    command.add(new File(new File(System.getProperty("java.home"), "bin"), "java").getCanonicalPath());
    for (String jvmArgument : jvmArguments) {
      command.add(jvmArgument);
    }
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(ServerLauncher.class.getName());
    command.add(ServerLauncher.Command.START.getName());
    command.add(getUniqueName());
    command.add("--disable-default-server");
    command.add("--redirect-output");

    this.process = new ProcessBuilder(command).directory(this.temporaryFolder.getRoot()).start();
    this.processOutReader = new ProcessStreamReader.Builder(this.process).inputStream(this.process.getInputStream()).build().start();
    this.processErrReader = new ProcessStreamReader.Builder(this.process).inputStream(this.process.getErrorStream()).build().start();

    // wait for server to start
    int pid = 0;
    ServerLauncher pidLauncher = null; 
    this.launcher = new ServerLauncher.Builder()
        .setWorkingDirectory(this.temporaryFolder.getRoot().getCanonicalPath())
        .build();
    try {
      waitForServerToStart();

      // validate the pid file and its contents
      this.pidFile = new File(this.temporaryFolder.getRoot(), ProcessType.SERVER.getPidFileName());
      assertTrue(this.pidFile.exists());
      pid = readPid(this.pidFile);
      assertTrue(pid > 0);
      assertTrue(ProcessUtils.isProcessAlive(pid));

      // validate log file was created
      final String logFileName = getUniqueName()+".log";
      assertTrue("Log file should exist: " + logFileName, new File(this.temporaryFolder.getRoot(), logFileName).exists());

      // use launcher with pid
      pidLauncher = new Builder()
          .setPid(pid)
          .build();

      assertNotNull(pidLauncher);
      assertFalse(pidLauncher.isRunning());

      // status with pid only should throw AttachAPINotFoundException
      try {
        pidLauncher.status();
        fail("FileProcessController should have thrown AttachAPINotFoundException");
      } catch (AttachAPINotFoundException e) {
        // passed
      }
      
    } catch (Throwable e) {
      this.errorCollector.addError(e);
    }

    // stop the server
    try {
      assertEquals(Status.STOPPED, this.launcher.stop().getStatus());
      waitForPidToStop(pid, true);
      waitForFileToDelete(this.pidFile);
    } catch (Throwable e) {
      this.errorCollector.addError(e);
    } finally {
      new File(ProcessType.SERVER.getStatusRequestFileName()).delete(); // TODO: delete
    }
  }
  
  @Override
  @Test
  /**
   * Override because FileProcessController cannot request stop with PID
   */
  public void testStopUsingPid() throws Throwable {
    final List<String> jvmArguments = getJvmArguments();
    jvmArguments.add("-D" + getUniqueName() + "=true");
    
    final List<String> command = new ArrayList<String>();
    command.add(new File(new File(System.getProperty("java.home"), "bin"), "java").getCanonicalPath());
    for (String jvmArgument : jvmArguments) {
      command.add(jvmArgument);
    }
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(ServerLauncher.class.getName());
    command.add(ServerLauncher.Command.START.getName());
    command.add(getUniqueName());
    command.add("--disable-default-server");
    command.add("--redirect-output");

    this.process = new ProcessBuilder(command).directory(this.temporaryFolder.getRoot()).start();
    this.processOutReader = new ProcessStreamReader.Builder(this.process).inputStream(this.process.getInputStream()).inputListener(createLoggingListener("sysout", getUniqueName() + "#sysout")).build().start();
    this.processErrReader = new ProcessStreamReader.Builder(this.process).inputStream(this.process.getErrorStream()).inputListener(createLoggingListener("syserr", getUniqueName() + "#syserr")).build().start();

    // wait for server to start
    int pid = 0;
    ServerLauncher pidLauncher = null; 
    this.launcher = new ServerLauncher.Builder()
        .setWorkingDirectory(this.temporaryFolder.getRoot().getCanonicalPath())
        .build();
    try {
      waitForServerToStart();

      // validate the pid file and its contents
      this.pidFile = new File(this.temporaryFolder.getRoot(), ProcessType.SERVER.getPidFileName());
      assertTrue(this.pidFile.exists());
      pid = readPid(this.pidFile);
      assertTrue(pid > 0);
      assertTrue(ProcessUtils.isProcessAlive(pid));

      // validate log file was created
      final String logFileName = getUniqueName()+".log";
      assertTrue("Log file should exist: " + logFileName, new File(this.temporaryFolder.getRoot(), logFileName).exists());

      // use launcher with pid
      pidLauncher = new Builder()
          .setPid(pid)
          .build();

      assertNotNull(pidLauncher);
      assertFalse(pidLauncher.isRunning());

      // stop with pid only should throw AttachAPINotFoundException
      try {
        pidLauncher.stop();
        fail("FileProcessController should have thrown AttachAPINotFoundException");
      } catch (AttachAPINotFoundException e) {
        // passed
      }

    } catch (Throwable e) {
      this.errorCollector.addError(e);
    }

    try {
      // stop the server
      assertEquals(Status.STOPPED, this.launcher.stop().getStatus());
      waitForPidToStop(pid);
      waitForFileToDelete(this.pidFile);
      
    } catch (Throwable e) {
      this.errorCollector.addError(e);
    } finally {
      new File(ProcessType.SERVER.getStopRequestFileName()).delete(); // TODO: delete
    }
  }
}
