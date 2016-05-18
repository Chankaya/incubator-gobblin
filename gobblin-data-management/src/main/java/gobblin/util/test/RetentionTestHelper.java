/*
 * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package gobblin.util.test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import gobblin.config.client.ConfigClient;
import gobblin.configuration.ConfigurationKeys;
import gobblin.data.management.retention.DatasetCleaner;
import gobblin.data.management.retention.dataset.CleanableDataset;
import gobblin.data.management.retention.dataset.CleanableDatasetBase;
import gobblin.data.management.retention.profile.ManagedCleanableDatasetFinder;
import gobblin.data.management.retention.profile.MultiCleanableDatasetFinder;
import gobblin.dataset.Dataset;
import gobblin.dataset.DatasetsFinder;
import gobblin.util.PathUtils;

/**
 * Helper methods for Retention integration tests
 */
public class RetentionTestHelper {

  /**
  *
  * Does gobblin retention for test data. {@link DatasetCleaner} which does retention in production can not be directly called as we need to resolve some
  * runtime properties like ${testNameTempPath}. This directory contains all the setup data created for a test by {@link RetentionTestDataGenerator#setup()}.
  * It is unique for each test.
  * The default {@link ConfigClient} used by {@link DatasetCleaner} connects to config store configs. We need to provide a
  * mock {@link ConfigClient} since the configs are in classpath and not on config store.
  *
  * @param retentionConfigClasspathResource this is the same jobProps/config files used while running a real retention job
  * @param testNameTempPath temp path for this test where test data is generated
  */
 public static void clean(FileSystem fs, Path retentionConfigClasspathResource, Path testNameTempPath) throws Exception {

   if (retentionConfigClasspathResource.getName().endsWith(".job")) {

     Properties jobProps = new Properties();
     try (final InputStream stream = RetentionTestHelper.class.getClassLoader().getResourceAsStream(retentionConfigClasspathResource.toString())) {
       jobProps.load(stream);
       for (Entry<Object, Object> entry : jobProps.entrySet()) {
         jobProps.put(entry.getKey(), StringUtils.replace((String)entry.getValue(), "${testNameTempPath}", testNameTempPath.toString()));
       }
     }
     MultiCleanableDatasetFinder finder = new MultiCleanableDatasetFinder(fs, jobProps);
     for (Dataset dataset : finder.findDatasets()) {
       ((CleanableDataset)dataset).clean();
     }
   } else {
     Config testConfig = ConfigFactory.parseResources(retentionConfigClasspathResource.toString())
         .withFallback(ConfigFactory.parseMap(ImmutableMap.of("testNameTempPath", PathUtils.getPathWithoutSchemeAndAuthority(testNameTempPath).toString()))).resolve();

     ConfigClient client = mock(ConfigClient.class);
     when(client.getConfig(any(String.class))).thenReturn(testConfig);
     Properties jobProps = new Properties();
     jobProps.setProperty(CleanableDatasetBase.SKIP_TRASH_KEY, Boolean.toString(true));
     jobProps.setProperty(ConfigurationKeys.CONFIG_MANAGEMENT_STORE_URI, "dummy");

     @SuppressWarnings("unchecked")
     DatasetsFinder<CleanableDataset> finder =
         (DatasetsFinder<CleanableDataset>) ConstructorUtils.invokeConstructor(
             Class.forName(testConfig.getString(MultiCleanableDatasetFinder.DATASET_FINDER_CLASS_KEY)),
             new Object[] { fs, jobProps, testConfig, client });
     for (CleanableDataset dataset : finder.findDatasets()) {
       dataset.clean();
     }
   }
 }
}
