/*
 * Copyright (c) 2020 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.mcc;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.ldk.notification.NotificationService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;

import javax.mail.Address;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class MccManager
{
    private static final Logger _log = LogManager.getLogger(MccManager.class);

    public static final String ContainerPropName = "MCCContainer";
    public static final String NotifyPropName = "MCCContactUsers";
    public static final String MCCRequestNotificationUsers = "MCCRequestNotificationUsers";
    public static final String MCCRequestContainer = "MCCRequestContainer";
    public static final String ZIMSImportPath = "ZIMSImportPath";

    public static final String MCC_GROUP_NAME = "MCC Users";
    public static final String ANIMAL_GROUP_NAME = "MCC Animal Data Access";
    public static final String REQUEST_GROUP_NAME = "MCC Animal Requestors";

    private static final MccManager _instance = new MccManager();

    private MccManager()
    {
        // prevent external construction with a private default constructor
    }

    public static MccManager get()
    {
        return _instance;
    }

    public Container getMCCRequestContainer()
    {
        Module m = ModuleLoader.getInstance().getModule(MccModule.NAME);
        ModuleProperty mp = m.getModuleProperties().get(MccManager.MCCRequestContainer);
        String path = mp.getEffectiveValue(ContainerManager.getRoot());
        if (path == null)
            return null;

        return ContainerManager.getForPath(path);
    }

    public Container getMCCContainer()
    {
        Module m = ModuleLoader.getInstance().getModule(MccModule.NAME);
        ModuleProperty mp = m.getModuleProperties().get(MccManager.ContainerPropName);
        String path = mp.getEffectiveValue(ContainerManager.getRoot());
        if (path == null)
            return null;

        return ContainerManager.getForPath(path);
    }

    public File getZimsImportFolder(Container c)
    {
        Module m = ModuleLoader.getInstance().getModule(MccModule.NAME);
        ModuleProperty mp = m.getModuleProperties().get(MccManager.ZIMSImportPath);
        String val = StringUtils.trimToNull(mp.getValueContainerSpecific(c));
        if (val == null)
        {
            return null;
        }

        return new File(val);
    }

    public Set<Address> getNotificationUserEmails()
    {
        return getUserEmailsForProp(MccManager.NotifyPropName);
    }

    public Set<Address> getRequestNotificationUserEmails()
    {
        return getUserEmailsForProp(MccManager.MCCRequestNotificationUsers);
    }

    private Set<Address> getUserEmailsForProp(String propName)
    {
        Module m = ModuleLoader.getInstance().getModule(MccModule.NAME);
        ModuleProperty mp = m.getModuleProperties().get(propName);
        String userNames = mp.getEffectiveValue(ContainerManager.getRoot());
        userNames = StringUtils.trimToNull(userNames);
        if (userNames == null)
            return null;

        Set<Address> ret = new HashSet<>();
        for (String principalName : userNames.split(","))
        {
            UserPrincipal up = SecurityManager.getPrincipal(principalName, getMCCContainer(), true);
            if (up == null)
            {m
                _log.error("Unknown user/group registered for MCC notifications: " + principalName, new Exception());
                continue;
            }

            try
            {
                ret.addAll(NotificationService.get().getEmailsForPrincipal(up));
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                _log.error("Invalid MCC email: " + principalName, e);
            }
        }

        return ret;
    }
}