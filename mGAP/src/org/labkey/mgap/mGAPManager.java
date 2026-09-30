/*
 * Copyright (c) 2015 LabKey Corporation
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

package org.labkey.mgap;

import jakarta.mail.Message;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.security.xml.GroupEnumType;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;
import static org.labkey.api.util.IntegerUtils.asInteger;

public class mGAPManager
{
    private static final Logger _log = LogHelper.getLogger(mGAPManager.class, "mGAPManager Logger");

    private static final mGAPManager _instance = new mGAPManager();
    public static final String ContainerPropName = "MGAPContainer";
    public static final String NotifyPropName = "MGAPContactUsers";
    public static final String OmimApiKeyPropName = "OmimApiKey";
    public static final String GROUP_NAME = "mGAP Users";

    public static final String DATA_DIR_NAME = "mgapData";

    private mGAPManager()
    {
        // prevent external construction with a private default constructor
    }

    public static mGAPManager get()
    {
        return _instance;
    }

    public Container getMGapContainer()
    {
        Module m = ModuleLoader.getInstance().getModule(mGAPModule.NAME);
        ModuleProperty mp = m.getModuleProperties().get(mGAPManager.ContainerPropName);
        String path = mp.getEffectiveValue(ContainerManager.getRoot());
        if (path == null)
            return null;

        return ContainerManager.getForPath(path);
    }

    public String getOmimApiKey(Container c)
    {
        Module m = ModuleLoader.getInstance().getModule(mGAPModule.NAME);
        ModuleProperty mp = m.getModuleProperties().get(mGAPManager.OmimApiKeyPropName);

        return mp.getEffectiveValue(c);
    }

    public Set<User> getNotificationUsers()
    {
        Module m = ModuleLoader.getInstance().getModule(mGAPModule.NAME);
        ModuleProperty mp = m.getModuleProperties().get(mGAPManager.NotifyPropName);
        String userNames = mp.getEffectiveValue(ContainerManager.getRoot());
        userNames = StringUtils.trimToNull(userNames);
        if (userNames == null)
            return null;

        Set<User> ret = new HashSet<>();
        for (String username : userNames.split(","))
        {
            User u = UserManager.getUserByDisplayName(username);
            if (u == null)
            {
                try
                {
                    u = UserManager.getUser(new ValidEmail(username));
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    //ignore
                }
            }

            if (u == null)
            {
                _log.error("Unknown user registered for mGAP notifications: {}", username);
            }

            if (u != null)
            {
                ret.add(u);
            }
        }

        return ret;
    }

    public String getReplyEmail(Container c)
    {
        LookAndFeelProperties lfp = LookAndFeelProperties.getInstance(c);
        String email = lfp.getSystemEmailAddress();
        if (email == null)
        {
            return AppProps.getInstance().getAdministratorContactEmail(true);
        }

        return email;
    }

    public void approveUsers(List<Integer> requestIds, Container c, User adminUser, ViewContext vc, BindException errors)
    {
        if (!c.hasPermission(adminUser, AdminPermission.class))
        {
            throw new UnauthorizedException("User must be an admin to approve users");
        }

        List<SecurityManager.NewUserStatus> newUserStatusList = new ArrayList<>();
        List<User> existingUsersGivenAccess = new ArrayList<>();
        try (DbScope.Transaction transaction = CoreSchema.getInstance().getScope().ensureTransaction())
        {
            TableInfo ti = mGAPSchema.getInstance().getSchema().getTable(mGAPSchema.TABLE_USER_REQUESTS);
            for (int requestId : requestIds)
            {
                TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowId"), requestId), null);
                Map<String, Object> map = ts.getMap(requestId);

                User u;
                if (map.get("userId") != null)
                {
                    Integer userId = asInteger(map.get("userId"));
                    u = UserManager.getUser(userId);
                    existingUsersGivenAccess.add(u);
                }
                else
                {
                    ValidEmail ve = new ValidEmail((String) map.get("email"));
                    u = UserManager.getUser(ve);
                    if (u != null)
                    {
                        existingUsersGivenAccess.add(u);
                    }
                    else
                    {
                        SecurityManager.NewUserStatus st = SecurityManager.addUser(ve, adminUser);
                        u = st.getUser();
                        u.setFirstName((String) map.get("firstName"));
                        u.setLastName((String) map.get("lastName"));
                        UserManager.updateUser(adminUser, u);

                        if (st.isLdapOrSsoEmail())
                        {
                            existingUsersGivenAccess.add(st.getUser());
                        }
                        else
                        {
                            newUserStatusList.add(st);
                        }
                    }
                }

                Map<String, Object> row = new HashMap<>();
                row.put("rowId", requestId);
                row.put("userId", u.getUserId());
                Table.update(adminUser, ti, row, requestId);

                Container mGapContainer = mGAPManager.get().getMGapContainer();
                if (!mGapContainer.hasPermission(u, ReadPermission.class))
                {
                    MutableSecurityPolicy policy = new MutableSecurityPolicy(mGapContainer.getPolicy());
                    policy.addRoleAssignment(u, ReaderRole.class);
                    SecurityPolicyManager.savePolicy(policy, adminUser);
                }
                else
                {
                    _log.info("User already has read permission on mGAP container: {}", u.getDisplayName(adminUser));
                }
            }

            transaction.commit();
        }
        catch (ValidEmail.InvalidEmailException | SecurityManager.UserManagementException e)
        {
            errors.reject(ERROR_MSG, e.getMessage());
            _log.error("Error creating mGAP user", e);
        }

        Set<User> allUsers = new HashSet<>(existingUsersGivenAccess);
        try
        {
            //send emails:
            for (SecurityManager.NewUserStatus st : newUserStatusList)
            {
                vc = new ViewContext(vc);
                vc.setUser(st.getUser());
                SecurityManager.sendRegistrationEmail(vc, st.getEmail(), null, st, null);
                allUsers.add(st.getUser());
            }

            Container mGapContainer = mGAPManager.get().getMGapContainer();
            for (User u : existingUsersGivenAccess)
            {
                boolean isLDAP = AuthenticationManager.isLdapOrSsoEmail(new ValidEmail(u.getEmail()));

                MailHelper.MultipartMessage mail = MailHelper.createMultipartMessage();
                mail.setEncodedHtmlContent("Your account request has been approved for mGAP!  " + "<a href=\"" + AppProps.getInstance().getBaseServerUrl() + mGapContainer.getStartURL(u) + "\">Click here to access the site.</a>" + (isLDAP ? "  Use your normal OHSU email/password to login." : ""));
                mail.setFrom(getReplyEmail(c));
                mail.setSubject("mGap Account Request");
                mail.addRecipients(Message.RecipientType.TO, u.getEmail());

                MailHelper.send(mail, adminUser, c);
            }

            Group g = GroupManager.getGroup(mGapContainer, mGAPManager.GROUP_NAME, GroupEnumType.SITE);
            if (g == null)
            {
                g = SecurityManager.createGroup(ContainerManager.getRoot(), mGAPManager.GROUP_NAME, adminUser);
            }

            SecurityManager.addMembers(g, allUsers);
        }
        catch (Exception e)
        {
            errors.reject(ERROR_MSG, e.getMessage());
            _log.error("Error creating mGAP user", e);
        }
    }

    public String getDefaultSpecies()
    {
        return "Rhesus macaque";
    }
}