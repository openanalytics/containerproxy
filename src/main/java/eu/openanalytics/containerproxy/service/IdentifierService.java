/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.util.Sha1;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Very simple service that keeps track of the identifiers of this ShinyProxy instance + server.
 */
@Service
public class IdentifierService {

    private final Logger logger = LogManager.getLogger(getClass());
    /**
     * String that identifies this "run" or "server" of ShinyProxy.
     * This is unique every time ShinyProxy starts and independent of the InstanceId.
     */
    public String runtimeId = null;
    /**
     * String that identifies this "instance" of ShinyProxy Configuration.
     * This value is determined by the configuration (i.e. application.yml) of ShinyProxy.
     * It is not unique across multiple runs or multiple servers.
     * This value only changes when the configuration changes.
     */
    public String instanceId = null;
    /**
     * String identifying the realm ShinyProxy operates in.
     */
    public String realmId = null;

    public Long version = null;

    @Inject
    private Environment environment;

    @PostConstruct
    public void init() throws IOException, NoSuchAlgorithmException {
        String podName = environment.getProperty("SP_KUBE_POD_NAME");
        if (podName != null) {
            runtimeId = StringUtils.right(podName, 4);
        } else {
            runtimeId = UUID.randomUUID().toString();
        }

        logger.info("ShinyProxy runtimeId:                   " + runtimeId);

        instanceId = calculateInstanceId();
        logger.info("ShinyProxy instanceID (hash of config): " + instanceId);

        realmId = environment.getProperty("proxy.realm-id");
        if (realmId != null) {
            logger.info("ShinyProxy realmId:                     " + realmId);
        }

        version = environment.getProperty("proxy.version", Long.class);
        if (version != null) {
            logger.info("ShinyProxy version:                     " + version);
        }
    }

    private File getPathToConfigFile() {
        String path = environment.getProperty("spring.config.location");
        if (path != null) {
            return Paths.get(path).toFile();
        }

        File file = Paths.get(ContainerProxyApplication.CONFIG_FILENAME).toFile();
        if (file.exists()) {
            return file;
        }

        return null;
    }

    /**
     * Calculates a hash of the config file (i.e. application.yaml).
     */
    private String calculateInstanceId() throws IOException, NoSuchAlgorithmException {
        /**
         * We need a hash of some "canonical" version of the config file.
         * The hash should not change when e.g. comments are added to the file.
         * Therefore we read the application.yml file into an Object and then
         * dump it again into YAML. We also sort the keys of maps and properties so that
         * the order does not matter for the resulting hash.
         */
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

        File file = getPathToConfigFile();
        if (file == null) {
            // this should only happen in tests
            instanceId = "unknown-instance-id";
            return instanceId;
        }

        Object parsedConfig = objectMapper.readValue(file, Object.class);
        instanceId = Sha1.hash(parsedConfig);
        return instanceId;
    }

}
