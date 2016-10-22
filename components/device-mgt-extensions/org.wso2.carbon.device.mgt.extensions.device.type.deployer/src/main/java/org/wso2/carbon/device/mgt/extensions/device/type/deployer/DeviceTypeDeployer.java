/*
 *   Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.wso2.carbon.device.mgt.extensions.device.type.deployer;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.deployment.AbstractDeployer;
import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.deployment.repository.util.DeploymentFileData;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.w3c.dom.Document;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.device.mgt.common.spi.DeviceManagementService;
import org.wso2.carbon.device.mgt.extensions.device.type.deployer.config.DeviceManagementConfiguration;
import org.wso2.carbon.device.mgt.extensions.device.type.deployer.config.exception.DeviceTypeConfigurationException;
import org.wso2.carbon.device.mgt.extensions.device.type.deployer.internal.DeviceTypeManagementDataHolder;
import org.wso2.carbon.device.mgt.extensions.device.type.deployer.template.DeviceTypeConfigIdentifier;
import org.wso2.carbon.device.mgt.extensions.device.type.deployer.template.DeviceTypeManagerService;
import org.wso2.carbon.device.mgt.extensions.device.type.deployer.util.DeviceTypeConfigUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class DeviceTypeDeployer extends AbstractDeployer {

    private static Log log = LogFactory.getLog(DeviceTypeDeployer.class);
    private ConfigurationContext configurationContext;
    protected Map<String, ServiceRegistration> deviceTypeServiceRegistrations = new ConcurrentHashMap();
    protected Map<String, DeviceTypeConfigIdentifier> deviceTypeConfigurationDataMap = new ConcurrentHashMap();

    @Override
    public void init(ConfigurationContext configurationContext) {
        this.configurationContext = configurationContext;
    }

    @Override
    public void setDirectory(String s) {

    }

    @Override
    public void setExtension(String s) {

    }

    @Override
    public void deploy(DeploymentFileData deploymentFileData) throws DeploymentException {
        try {
            DeviceManagementConfiguration deviceManagementConfiguration = getDeviceTypeConfiguration(
                    deploymentFileData.getFile().getAbsoluteFile());
            String deviceType = deviceManagementConfiguration.getDeviceType();
            String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain(true);
            if (deviceType != null && !deviceType.isEmpty() && tenantDomain != null
                    && !tenantDomain.isEmpty()) {
                DeviceTypeConfigIdentifier deviceTypeConfigIdentifier = new DeviceTypeConfigIdentifier(deviceType,
                                                                                                       tenantDomain);
                ServiceRegistration serviceRegistration = registerDeviceType(deviceTypeConfigIdentifier,
                                                                             deviceManagementConfiguration);
                this.deviceTypeServiceRegistrations.put(deploymentFileData.getAbsolutePath(), serviceRegistration);
                this.deviceTypeConfigurationDataMap.put(deploymentFileData.getAbsolutePath(),
                                                        deviceTypeConfigIdentifier);
            }
        } catch (Throwable e) {
            log.error("Cannot deploy deviceType : " + deploymentFileData.getName(), e);
            throw new DeploymentException("Device type file " + deploymentFileData.getName() + " is not deployed ", e);
        }

    }

    @Override
    public void undeploy(String filePath) throws DeploymentException {
        DeviceTypeConfigIdentifier deviceTypeConfigIdentifier = this.deviceTypeConfigurationDataMap.get(filePath);
        unregisterDeviceType(filePath);
        this.deviceTypeConfigurationDataMap.remove(filePath);
        log.info("Device Type undeployed successfully : " + deviceTypeConfigIdentifier.getDeviceType() + " for tenant "
                         + deviceTypeConfigIdentifier.getTenantDomain());
    }

    private DeviceManagementConfiguration getDeviceTypeConfiguration(File configurationFile)
            throws DeviceTypeConfigurationException {
        try {
            Document doc = DeviceTypeConfigUtil.convertToDocument(configurationFile);

            /* Un-marshaling Webapp Authenticator configuration */
            JAXBContext ctx = JAXBContext.newInstance(DeviceManagementConfiguration.class);
            Unmarshaller unmarshaller = ctx.createUnmarshaller();
            //unmarshaller.setSchema(getSchema());
            return (DeviceManagementConfiguration) unmarshaller.unmarshal(doc);
        } catch (JAXBException e) {
            throw new DeviceTypeConfigurationException("Error occurred while un-marshalling the file " +
                                                               configurationFile.getAbsolutePath(), e);
        }
    }

    private ServiceRegistration registerDeviceType(DeviceTypeConfigIdentifier deviceTypeConfigIdentifier,
                                                   DeviceManagementConfiguration deviceManagementConfiguration) {
        DeviceTypeManagerService deviceTypeManagerService = new DeviceTypeManagerService(deviceTypeConfigIdentifier,
                                                                                         deviceManagementConfiguration);
        BundleContext bundleContext = DeviceTypeManagementDataHolder.getInstance().getBundleContext();
        return bundleContext.registerService(DeviceManagementService.class.getName(), deviceTypeManagerService, null);
    }

    private void unregisterDeviceType(String filePath) {
        if (log.isDebugEnabled()) {
            log.debug("De-activating Device Management Service.");
        }
        try {
            if (this.deviceTypeServiceRegistrations.get(filePath) != null) {
                this.deviceTypeServiceRegistrations.get(filePath).unregister();
            }
            if (log.isDebugEnabled()) {
                log.debug(" Device Management Service has been successfully de-activated");
            }
        } catch (Throwable e) {
            log.error("Error occurred while de-activating Deactivating device management service.", e);
        }
        deviceTypeServiceRegistrations.remove(filePath);
    }
}
