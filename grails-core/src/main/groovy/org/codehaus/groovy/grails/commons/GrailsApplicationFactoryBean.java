/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.commons;

import grails.util.Environment;
import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.compiler.GrailsClassLoader;
import org.codehaus.groovy.grails.compiler.support.GrailsResourceLoader;
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * Creates a Grails application object based on Groovy files.
 *
 * @author Steven Devijver
 * @author Graeme Rocher
 * @author Chanwit Kaewkasi
 *
 * @since 0.1
 */
public class GrailsApplicationFactoryBean implements FactoryBean<GrailsApplication>, InitializingBean {

    private static Log LOG = LogFactory.getLog(GrailsApplicationFactoryBean.class);
    private GrailsApplication grailsApplication = null;
    private Resource descriptor;

    public void afterPropertiesSet() throws Exception {
        if (descriptor != null && descriptor.exists()) {
            LOG.info("Loading Grails application with information from descriptor.");

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            List<Class<?>> classes = new ArrayList<Class<?>>();
            InputStream inputStream = null;
            try {
                inputStream = descriptor.getInputStream();

                // Get all the resource nodes in the descriptor.
                // Xpath: /grails/resources/resource, where root is /grails
                GPathResult root = new XmlSlurper().parse(inputStream);
                GPathResult resources = (GPathResult) root.getProperty("resources");
                GPathResult grailsClasses = (GPathResult) resources.getProperty("resource");

                // Each resource node should contain a full class name,
                // so we attempt to load them as classes.
                for (int i = 0; i < grailsClasses.size(); i++) {
                    GPathResult node = (GPathResult) grailsClasses.getAt(i);
                    String className = node.text();
                    try {
                        Class<?> clazz;
                        if (classLoader instanceof GrailsClassLoader) {
                            clazz = classLoader.loadClass(className);
                        }
                        else {
                            clazz = Class.forName(className, true, classLoader);
                        }
                        classes.add(clazz);
                    }
                    catch (ClassNotFoundException e) {
                        LOG.warn("Class with name ["+className+"] was not found, and hence not loaded. Possible empty class or script definition?");
                    }
                }
            }
            finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
            Class<?>[] loadedClasses = classes.toArray(new Class[classes.size()]);
            grailsApplication = new DefaultGrailsApplication(loadedClasses, classLoader);
        }
        else if (!Environment.isWarDeployed()) {
            org.codehaus.groovy.grails.io.support.Resource[] buildResources = GrailsPluginUtils.getPluginBuildSettings().getArtefactResourcesForCurrentEnvironment();

            Resource[] resources = new Resource[buildResources.length];

            for (int i = 0; i < buildResources.length; i++) {
                org.codehaus.groovy.grails.io.support.Resource buildResource = buildResources[i];
                resources[i] = new FileSystemResource(buildResource.getFile());
            }

            grailsApplication = new DefaultGrailsApplication(resources);
        }
        else {
            grailsApplication = new DefaultGrailsApplication();
        }

        ApplicationHolder.setApplication(grailsApplication);
    }

    public GrailsApplication getObject() {
        return grailsApplication;
    }

    public Class<GrailsApplication> getObjectType() {
        return GrailsApplication.class;
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * @deprecated No longer used will be removed in a future release
     * @param resourceLoader
     */
    @Deprecated
    public void setGrailsResourceLoader(GrailsResourceLoader resourceLoader) {
        // do nothing
    }

    public void setGrailsDescriptor(Resource r) {
        descriptor = r;
    }
}
