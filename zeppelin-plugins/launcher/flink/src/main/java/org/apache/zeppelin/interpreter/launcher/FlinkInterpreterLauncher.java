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

package org.apache.zeppelin.interpreter.launcher;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.Set;
import java.util.stream.Collectors;

public class FlinkInterpreterLauncher extends StandardInterpreterLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlinkInterpreterLauncher.class);
  private static final Set<String> FLINK_EXECUTION_MODES = Sets.newHashSet(
          "local", "remote", "yarn", "yarn-application", "kubernetes-application");

  public FlinkInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) {
    super(zConf, recoveryStorage);
  }

  @Override
  public Map<String, String> buildEnvFromProperties(InterpreterLaunchContext context)
          throws IOException {
    Map<String, String> envs = super.buildEnvFromProperties(context);

    //LL
    LOGGER.info("in FlinkInterpreterLauncher\n");
    // update FLINK related environment variables
    String flinkHome = getFlinkHome(context);
    LOGGER.info("flink home is: %s", flinkHome);
    // 
    if (!envs.containsKey("FLINK_CONF_DIR")) {
      envs.put("FLINK_CONF_DIR", flinkHome + "/conf");
    }
    envs.put("FLINK_LIB_DIR", flinkHome + "/lib");
    envs.put("FLINK_PLUGINS_DIR", flinkHome + "/plugins");

    normalizeConfiguration(context);

    String flinkExecutionMode = context.getProperties().getProperty("flink.execution.mode");

    //LL
    LOGGER.info("flink execution mode is %s,\n",flinkExecutionMode);

    if (!FLINK_EXECUTION_MODES.contains(flinkExecutionMode)) {
      throw new IOException("Not valid flink.execution.mode: " +
              flinkExecutionMode + ", valid modes ares: " +
              FLINK_EXECUTION_MODES.stream().collect(Collectors.joining(", ")));
    }
    // application mode specific logic
    if (isApplicationMode(flinkExecutionMode)) {
      updateEnvsForApplicationMode(flinkExecutionMode, envs, context);
    }

    if (isK8sApplicationMode(flinkExecutionMode)) {
      String flinkAppJar = context.getProperties().getProperty("flink.app.jar");
      //LL
      LOGGER.info("flinkAppJar %s \n",flinkAppJar);
      if (StringUtils.isBlank(flinkAppJar)) {
        throw new IOException("flink.app.jar is not specified for kubernetes-application mode");
      }
      envs.put("FLINK_APP_JAR", flinkAppJar);
      LOGGER.info("K8s application's FLINK_APP_JAR : " + flinkAppJar);
      context.getProperties().put("zeppelin.interpreter.forceShutdown", "false");
    } else {
      String flinkAppJar = chooseFlinkAppJar(flinkHome);
      LOGGER.info("Choose FLINK_APP_JAR for non k8s-application mode: {}", flinkAppJar);
      envs.put("FLINK_APP_JAR", flinkAppJar);
    }

    if ("yarn".equalsIgnoreCase(flinkExecutionMode) ||
            "yarn-application".equalsIgnoreCase(flinkExecutionMode)) {
      boolean runAsLoginUser = Boolean.parseBoolean(context
              .getProperties()
              .getProperty("zeppelin.flink.run.asLoginUser", "true"));
      String userName = context.getUserName();
      if (runAsLoginUser && !"anonymous".equals(userName)) {
        envs.put("HADOOP_USER_NAME", userName);
      }
    }

    return envs;
  }

  // do mapping between configuration of different execution modes.
  private void normalizeConfiguration(InterpreterLaunchContext context) {
    Properties intpProperties = context.getProperties();
    setNewProperty(intpProperties, "flink.jm.memory", "jobmanager.memory.process.size", true);
    setNewProperty(intpProperties, "flink.tm.memory", "taskmanager.memory.process.size", true);
    setNewProperty(intpProperties, "flink.tm.slot", "taskmanager.numberOfTaskSlots", false);
    setNewProperty(intpProperties, "flink.yarn.appName", "yarn.application.name", false);
    setNewProperty(intpProperties, "flink.yarn.queue", "yarn.application.queue", false);
  }

  /**
   * flink.jm.memory and flink.tm.memory only support int value and the unit is mb. (e.g. 1024)
   * And you need to specify unit for jobmanager.memory.process.size and
   * taskmanager.memory.process.size, e.g. 1024 mb.
   * @param properties
   * @param oldKey
   * @param newKey
   * @param isMemoryProperty
   */
  private void setNewProperty(Properties properties,
                              String oldKey,
                              String newKey,
                              boolean isMemoryProperty) {
    String value = properties.getProperty(oldKey);
    if (StringUtils.isNotBlank(value) && !properties.containsKey(newKey)) {
      if (isMemoryProperty) {
        properties.put(newKey, value + "mb");
      } else {
        properties.put(newKey, value);
      }
    }
  }

  private String chooseFlinkAppJar(String flinkHome) throws IOException {
    File flinkLibFolder = new File(flinkHome, "lib");
    List<File> flinkDistFiles =
            Arrays.stream(flinkLibFolder.listFiles(file -> file.getName().contains("flink-dist_")))
                    .collect(Collectors.toList());

    // LL
    LOGGER.info("flinkHome is \n");
    LOGGER.info("flinkHome\n");

    LOGGER.info("Print all file in flinkhome\n");
    List<File> flinkDistFilestest =
            Arrays.stream(flinkLibFolder.listFiles())
                    .collect(Collectors.toList());
    for (int i=0; i<flinkDistFilestest.size(); i++) {
      LOGGER.info(flinkDistFilestest.get(i).getName());
      LOGGER.info("\n");
    }  

    LOGGER.info("Print all file in finkDistFiles\n");
    for (int i=0; i<flinkDistFiles.size(); i++) {
      LOGGER.info(flinkDistFiles.get(i).getName());
      LOGGER.info("\n");
    }  
    
    LOGGER.info(Integer.toString(flinkDistFiles.size()));
    LOGGER.info("Only has one file, get file name: ");
    LOGGER.info(flinkDistFiles.get(0).getName());
    if (flinkDistFiles.size() < 1) {
      throw new IOException("Files not loading");
    }
    // 
    if (flinkDistFiles.size() > 1) {
      throw new IOException("More than 1 flink-dist files: " +
              flinkDistFiles.stream()
                      .map(file -> file.getAbsolutePath())
                      .collect(Collectors.joining(",")));
    }
    String scalaVersion = "2.11";
    if (flinkDistFiles.get(0).getName().contains("2.12")) {
      scalaVersion = "2.12";
    }

    // LL
    LOGGER.info("scala version should be 2.11, we have: ");
    LOGGER.info(scalaVersion);

    final String flinkScalaVersion = scalaVersion;
    File flinkInterpreterFolder =
            new File(ZeppelinConfiguration.create().getInterpreterDir(), "flink");
    LOGGER.info("flinkInterpreterFolder path: ");
    LOGGER.info(ZeppelinConfiguration.create().getInterpreterDir() + "flink");
    LOGGER.info("\n");

    List<File> flinkScalaJars =
            Arrays.stream(flinkInterpreterFolder
                    .listFiles(file -> file.getName().endsWith(".jar")))
            .filter(file -> file.getName().contains(flinkScalaVersion))
            .collect(Collectors.toList());

    List<File> allfiles = Arrays.stream(flinkInterpreterFolder.listFiles()).collect(Collectors.toList());
    LOGGER.info("print all files in flinkInterpreterFolder\n");
    for (int i=0; i<allfiles.size(); i++) {
      LOGGER.info(allfiles.get(i).getName());
      LOGGER.info("\n");
    }  

    LOGGER.info("determined scala version: flinkScalaVersion, flinkScalaJars list should contain one file\n");
    LOGGER.info(Integer.toString(flinkScalaJars.size()));
    LOGGER.info("now print all names\n");
    for (int i=0; i<flinkScalaJars.size(); i++) {
      LOGGER.info(flinkScalaJars.get(i).getName());
      LOGGER.info("\n");
    }        
    if (flinkScalaJars.size() < 1) {
      throw new IOException("flinkScalaJars not loading");
    }
    if (flinkScalaJars.size() > 1) {
      throw new IOException("More than 1 flink scala files: " +
              flinkScalaJars.stream()
                      .map(file -> file.getAbsolutePath())
                      .collect(Collectors.joining(",")));
    }

    return flinkScalaJars.get(0).getAbsolutePath();
  }

  private boolean isApplicationMode(String mode) {
    return isYarnApplicationMode(mode) || isK8sApplicationMode(mode);
  }

  private boolean isYarnApplicationMode(String mode) {
    return "yarn-application".equals(mode);
  }

  private boolean isK8sApplicationMode(String mode) {
    return "kubernetes-application".equals(mode);
  }

  /**
   * Get FLINK_HOME in the following orders:
   *    1. FLINK_HOME in interpreter setting
   *    2. FLINK_HOME in system environment variables.
   *
   * @param context
   * @return
   * @throws IOException
   */
  private String getFlinkHome(InterpreterLaunchContext context) throws IOException {
    String flinkHome = context.getProperties().getProperty("FLINK_HOME");
    if (StringUtils.isBlank(flinkHome)) {
      flinkHome = System.getenv("FLINK_HOME");
    }
    if (StringUtils.isBlank(flinkHome)) {
      throw new IOException("FLINK_HOME is not specified");
    }
    File flinkHomeFile = new File(flinkHome);
    if (!flinkHomeFile.exists()) {
      throw new IOException(String.format("FLINK_HOME '%s' doesn't exist", flinkHome));
    }
    if (!flinkHomeFile.isDirectory()) {
      throw new IOException(String.format("FLINK_HOME '%s' is a file, but should be directory",
              flinkHome));
    }
    return flinkHome;
  }

  private void updateEnvsForApplicationMode(String mode,
                                            Map<String, String> envs,
                                            InterpreterLaunchContext context) throws IOException {
    // ZEPPELIN_FLINK_APPLICATION_MODE is used in interpreter.sh
    envs.put("ZEPPELIN_FLINK_APPLICATION_MODE", mode);

    StringJoiner flinkConfStringJoiner = new StringJoiner("|");
    // set yarn.ship-files
    List<String> yarnShipFiles = getYarnShipFiles(context);
    if (!yarnShipFiles.isEmpty()) {
      flinkConfStringJoiner.add("-D");
      flinkConfStringJoiner.add("yarn.ship-files=" +
              yarnShipFiles.stream().collect(Collectors.joining(";")));
    }

    // set yarn.application.name
    String yarnAppName = context.getProperties().getProperty("flink.yarn.appName");
    if (StringUtils.isNotBlank(yarnAppName)) {
      flinkConfStringJoiner.add("-D");
      flinkConfStringJoiner.add("yarn.application.name=" + yarnAppName);
    }

    // add other configuration for both k8s and yarn
    for (Map.Entry<Object, Object> entry : context.getProperties().entrySet()) {
      String key = entry.getKey().toString();
      String value = entry.getValue().toString();
      if (!key.equalsIgnoreCase("yarn.ship-files") &&
              !key.equalsIgnoreCase("flink.yarn.appName")) {
        if (CharMatcher.whitespace().matchesAnyOf(value)) {
          LOGGER.warn("flink configuration key {} is skipped because it contains white space",
                  key);
        } else {
          flinkConfStringJoiner.add("-D");
          flinkConfStringJoiner.add(key + "=" + value);
        }
      }
    }
    envs.put("ZEPPELIN_FLINK_APPLICATION_MODE_CONF", flinkConfStringJoiner.toString());
  }

  /**
   * Used in yarn-application mode.
   *
   * @param context
   * @return
   * @throws IOException
   */
  private List<String> getYarnShipFiles(InterpreterLaunchContext context) throws IOException {
    // Extract yarn.ship-files, add hive-site.xml automatically if hive is enabled
    // and HIVE_CONF_DIR is specified
    List<String> yarnShipFiles = new ArrayList<>();
    String hiveConfDirProperty = context.getProperties().getProperty("HIVE_CONF_DIR");
    if (StringUtils.isNotBlank(hiveConfDirProperty) &&
            Boolean.parseBoolean(context.getProperties()
                    .getProperty("zeppelin.flink.enableHive", "false"))) {
      File hiveSiteFile = new File(hiveConfDirProperty, "hive-site.xml");
      if (hiveSiteFile.isFile() && hiveSiteFile.exists()) {
        yarnShipFiles.add(hiveSiteFile.getAbsolutePath());
      } else {
        LOGGER.warn("Hive site file: {} doesn't exist or is not a directory", hiveSiteFile);
      }
    }
    if (context.getProperties().containsKey("yarn.ship-files")) {
      yarnShipFiles.add(context.getProperties().getProperty("yarn.ship-files"));
    }

    return yarnShipFiles;
  }
}
