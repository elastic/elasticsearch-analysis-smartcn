/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.plugin.analysis.smartcn;

import org.apache.lucene.util.Constants;
import org.elasticsearch.Version;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.analysis.SmartChineseAnalysisBinderProcessor;
import org.elasticsearch.indices.analysis.smartcn.SmartChineseIndicesAnalysisModule;
import org.elasticsearch.plugins.AbstractPlugin;

import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;

/**
 *
 */
public class AnalysisSmartChinesePlugin extends AbstractPlugin {
    protected final ESLogger logger = Loggers.getLogger(AnalysisSmartChinesePlugin.class);

    private final Settings settings;

    public AnalysisSmartChinesePlugin(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String name() {
        return "analysis-smartcn";
    }

    @Override
    public String description() {
        return "Smart Chinese analysis support";
    }

    @Override
    public Collection<Class<? extends Module>> modules() {
        ImmutableList.Builder<Class<? extends Module>> builder = ImmutableList.builder();
        if (checkCompatibility()) {
            builder.add(SmartChineseIndicesAnalysisModule.class);
        }

        return builder.build();
    }

    public void onModule(AnalysisModule module) {
        if (checkCompatibility()) {
            module.addProcessor(new SmartChineseAnalysisBinderProcessor());
        }
    }

    private boolean checkCompatibility() {
        String luceneVersion = null;
        try {
            // We try to read the es-plugin.properties file
            // But as we can have several plugins in the same classloader,
            // we have to find the right es-plugin.properties file
            Enumeration<URL> pluginUrls = settings.getClassLoader().getResources("es-plugin.properties");
            while (pluginUrls.hasMoreElements()) {
                URL pluginUrl = pluginUrls.nextElement();
                InputStream is = pluginUrl.openStream();
                Properties pluginProps = new Properties();
                pluginProps.load(is);
                String plugin = pluginProps.getProperty("plugin");
                // If we don't have the expected plugin, let's continue to the next one
                if (!this.getClass().getName().equals(plugin)) {
                    logger.debug("skipping [{}]", pluginUrl);
                    continue;
                }
                luceneVersion = pluginProps.getProperty("lucene");
                break;
            }

            if (luceneVersion != null) {
                // We only keep the first two parts
                String parts[] = luceneVersion.split("\\.");

                // Should fail if the running node is too old!
                org.apache.lucene.util.Version luceneExpectedVersion = org.apache.lucene.util.Version.parseLeniently(parts[0]+"."+parts[1]);

                if (Version.CURRENT.luceneVersion.equals(luceneExpectedVersion)) {
                    logger.debug("starting analysis plugin for Lucene [{}].", luceneExpectedVersion);
                    return true;
                }
            }
        } catch (Throwable t) {
            // We don't have the expected version... Let's fail after.
            logger.debug("exception raised while checking plugin Lucene version.", t);
        }
        logger.error("cannot start plugin due to incorrect Lucene version: plugin [{}], node [{}].",
                luceneVersion, Constants.LUCENE_MAIN_VERSION);
        return false;
    }
}
