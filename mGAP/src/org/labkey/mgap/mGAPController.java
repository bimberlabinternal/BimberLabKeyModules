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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class mGAPController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(mGAPController.class);
    public static final String NAME = "mgap";
    public static final Logger _log = Logger.getLogger(mGAPController.class);

    public mGAPController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class RequestUserAction extends MutatingApiAction<RequestUserForm>
    {
        @Override
        public void validateForm(RequestUserForm form, Errors errors)
        {
            Container mGapContainer = mGAPManager.get().getMGapContainer();
            if (mGapContainer == null)
            {
                errors.reject(ERROR_MSG, "The mGAP project has not been set on this server.  This is an administrator error.");
                return;
            }

            if (StringUtils.isEmpty(form.getEmail()) || StringUtils.isEmpty(form.getEmailConfirmation()))
            {
                errors.reject(ERROR_REQUIRED, "No email address provided");
            }
            else if (StringUtils.isEmpty(form.getFirstName()) || StringUtils.isEmpty(form.getLastName()) || StringUtils.isEmpty(form.getTitle()) || StringUtils.isEmpty(form.getInstitution()) || StringUtils.isEmpty(form.getReason()))
            {
                errors.reject(ERROR_REQUIRED, "You must provide your first and last name, title, institution, and reason for requesting access");
            }
            else
            {
                try
                {
                    ValidEmail email = new ValidEmail(form.getEmail());
                    if (!form.getEmail().equals(form.getEmailConfirmation()))
                    {
                        errors.reject(ERROR_MSG, "The email addresses you have entered do not match.  Please verify your email addresses below.");
                    }
                    else if (UserManager.userExists(email))
                    {
                        errors.reject(ERROR_MSG, "The email address you have entered is already associated with an account.  If you have forgotten your password, you can <a href=\"login-resetPassword.view?\">reset your password</a>.  Otherwise, please contact your administrator.");
                    }

                    TableInfo ti = mGAPSchema.getInstance().getSchema().getTable(mGAPSchema.TABLE_USER_REQUESTS);

                    //first check if this email exists:
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("email"), form.getEmail());
                    filter.addCondition(FieldKey.fromString("container"), mGapContainer.getId());
                    if (new TableSelector(ti, filter, null).exists())
                    {
                        errors.reject(ERROR_MSG, "A login has already been requested for this email.  You should receive a reply shortly from the site administrator.");
                    }
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    errors.reject(ERROR_MSG, "Your email address is not valid. Please verify your email address below.");
                }
            }
        }

        @Override
        public Object execute(RequestUserForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            try
            {
                TableInfo ti = mGAPSchema.getInstance().getSchema().getTable(mGAPSchema.TABLE_USER_REQUESTS);
                Map<String, Object> row = new HashMap<>();
                row.put("email", form.getEmail());
                row.put("firstName", form.getFirstName());
                row.put("lastName", form.getLastName());
                row.put("title", form.getTitle());
                row.put("institution", form.getInstitution());
                row.put("reason", form.getReason());
                row.put("container", mGAPManager.get().getMGapContainer().getId());

                Table.insert(UserManager.getGuestUser(), ti, row);

                Set<User> users = mGAPManager.get().getNotificationUsers();
                if (users != null && !users.isEmpty())
                {
                    try
                    {
                        Set<Address> emails = new HashSet<>();
                        for (User u : users)
                        {
                            emails.add(new InternetAddress(u.getEmail()));
                        }

                        MailHelper.MultipartMessage mail = MailHelper.createMultipartMessage();
                        Container c = mGAPManager.get().getMGapContainer();
                        if (c == null)
                        {
                            c = getContainer();
                        }

                        ActionURL url = QueryService.get().urlFor(getUser(), c, QueryAction.executeQuery, "mGap", "userRequests");
                        mail.setEncodedHtmlContent("A user requested an account on mGap.  <a href=\"" + url .getURIString(true)+ "\">Click here to view/approve this request</a>");
                        mail.setFrom(AppProps.getInstance().getAdministratorContactEmail());
                        mail.setSubject("mGap Account Request");
                        mail.addRecipients(Message.RecipientType.TO, emails.toArray(new Address[emails.size()]));

                        MailHelper.send(mail, getUser(), c);
                    }
                    catch (Exception e)
                    {
                        ExceptionUtil.logExceptionToMothership(null, e);
                    }
                }


            }
            catch (ConfigurationException e)
            {
                errors.reject(ERROR_MSG, "There was a problem sending the registration email.  Please contact your administrator.");
                _log.error("Error adding self registered user", e);
            }

            response.put("success", !errors.hasErrors());
            if (!errors.hasErrors())
                response.put("email", form.getEmail());

            return response;
        }
    }

    public static class RequestUserForm extends Object
    {
        private String email;
        private String emailConfirmation;
        private String firstName;
        private String lastName;
        private String title;
        private String institution;
        private String reason;

        public void setEmail(String email)
        {
            this.email = email;
        }

        public String getEmail()
        {
            return this.email;
        }

        public void setEmailConfirmation(String email)
        {
            this.emailConfirmation = email;
        }

        public String getEmailConfirmation()
        {
            return this.emailConfirmation;
        }

        public String getFirstName()
        {
            return firstName;
        }

        public void setFirstName(String firstName)
        {
            this.firstName = firstName;
        }

        public String getLastName()
        {
            return lastName;
        }

        public void setLastName(String lastName)
        {
            this.lastName = lastName;
        }

        public String getTitle()
        {
            return title;
        }

        public void setTitle(String title)
        {
            this.title = title;
        }

        public String getInstitution()
        {
            return institution;
        }

        public void setInstitution(String institution)
        {
            this.institution = institution;
        }

        public String getReason()
        {
            return reason;
        }

        public void setReason(String reason)
        {
            this.reason = reason;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ApproveUserRequestsAction extends MutatingApiAction<ApproveUserRequestsForm>
    {
        @Override
        public void validateForm(ApproveUserRequestsForm form, Errors errors)
        {
            Container mGapContainer = mGAPManager.get().getMGapContainer();
            if (mGapContainer == null)
            {
                errors.reject(ERROR_MSG, "The mGAP project has not been set on this server.  This is an administrator error.");
                return;
            }

            if (form.getRequestIds() == null || form.getRequestIds().length == 0)
            {
                errors.reject(ERROR_MSG, "No request IDs provided");
            }

            TableInfo ti = mGAPSchema.getInstance().getSchema().getTable(mGAPSchema.TABLE_USER_REQUESTS);
            for (int requestId : form.getRequestIds())
            {
                TableSelector ts = new TableSelector(ti, PageFlowUtil.set("userId"), new SimpleFilter(FieldKey.fromString("rowId"), requestId), null);
                if (!ts.exists())
                {
                    errors.reject(ERROR_MSG, "No request found for request ID: " + requestId);
                    break;
                }

                Integer userId = ts.getObject(Integer.class);
                if (userId != null)
                {
                    errors.reject(ERROR_MSG, "A user already exists for the request: " + requestId);
                    break;
                }
            }
        }

        @Override
        public Object execute(ApproveUserRequestsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            MutableSecurityPolicy policy = new MutableSecurityPolicy(mGAPManager.get().getMGapContainer().getPolicy());
            List<SecurityManager.NewUserStatus> newUserStatusList = new ArrayList<>();
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getScope().ensureTransaction())
            {
                TableInfo ti = mGAPSchema.getInstance().getSchema().getTable(mGAPSchema.TABLE_USER_REQUESTS);
                for (int requestId : form.getRequestIds())
                {
                    TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowId"), requestId), null);
                    Map<String, Object> map = ts.getMap(requestId);

                    SecurityManager.NewUserStatus st = SecurityManager.addUser(new ValidEmail((String)map.get("email")), getUser());
                    newUserStatusList.add(st);

                    User u = st.getUser();
                    u.setFirstName((String)map.get("firstName"));
                    u.setLastName((String)map.get("lastName"));
                    UserManager.updateUser(getUser(), u);

                    Map<String, Object> row = new HashMap<>();
                    row.put("rowId", requestId);
                    row.put("userId", u.getUserId());
                    Table.update(getUser(), ti, row, requestId);

                    policy.addRoleAssignment(u, ReaderRole.class);
                }

                SecurityPolicyManager.savePolicy(policy);

                transaction.commit();
            }

            //send emails:
            for (SecurityManager.NewUserStatus st : newUserStatusList)
            {
                SecurityManager.sendRegistrationEmail(getViewContext(), st.getEmail(), null, st, null);
            }

            response.put("success", !errors.hasErrors());

            return response;
        }
    }

    public static class ApproveUserRequestsForm extends Object
    {
        private int[] requestIds;

        public int[] getRequestIds()
        {
            return requestIds;
        }

        public void setRequestIds(int[] requestIds)
        {
            this.requestIds = requestIds;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @IgnoresTermsOfUse
    public static class DownloadBundleAction extends ExportAction<DownloadBundleForm>
    {
        public void export(DownloadBundleForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            TableInfo ti = DbSchema.get(mGAPSchema.NAME, DbSchemaType.Module).getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES);
            Map<String, Object> row = new TableSelector(ti).getMap(form.getReleaseId());
            Container rowContainer = ContainerManager.getForId((String)row.get("container"));
            if (rowContainer == null)
            {
                errors.reject(ERROR_MSG, "Unknown row container: " + form.getReleaseId());
                return;
            }
            else if (!rowContainer.hasPermission(getUser(), ReadPermission.class))
            {
                throw new UnauthorizedException("Cannot read the folder: " + rowContainer.getPath());
            }

            Set<File> toZip = new HashSet<>();
            String zipName = "mGap_VariantCatalog_v" + FileUtil.makeLegalName((String)row.get("version"));
            zipName = zipName.replaceAll(" ", "_");

            SequenceOutputFile so = SequenceOutputFile.getForId((Integer)row.get("vcfId"));
            if (so == null)
            {
                errors.reject(ERROR_MSG, "Unknown VCF file ID: " + form.getReleaseId());
                return;
            }
            else if (so.getFile() == null || !so.getFile().exists())
            {
                errors.reject(ERROR_MSG, "VCF file does not exist: " + (so.getFile() == null ? form.getReleaseId() : so.getFile().getPath()));
                return;
            }

            toZip.add(so.getFile());
            toZip.add(new File(so.getFile().getPath() + ".tbi"));

            if (form.getIncludeGenome())
            {
                ReferenceGenome genome = SequenceAnalysisService.get().getReferenceGenome((Integer)row.get("genomeId"), getUser());
                if (genome == null)
                {
                    errors.reject(ERROR_MSG, "Unknown genome: " + row.get("genomeId"));
                    return;
                }

                toZip.add(genome.getSourceFastaFile());
                toZip.add(genome.getFastaIndex());
                toZip.add(genome.getSequenceDictionary());
            }

            response.reset();
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + zipName + ".zip\"");

            try (ZipOutputStream out = new ZipOutputStream(response.getOutputStream()))
            {
                for (File f : toZip)
                {
                    ZipEntry entry = new ZipEntry(f.getName());
                    out.putNextEntry(entry);

                    try (InputStream in = new BufferedInputStream(new FileInputStream(f)))
                    {
                        FileUtil.copyData(in, out);
                    }
                }
            }

            mGapAuditTypeProvider.addAuditEntry(getContainer(), getUser(), zipName, "Variant Catalog", (Double)row.get("version"));
        }
    }

    public static class DownloadBundleForm
    {
        private Integer _releaseId;
        private Boolean _includeGenome;

        public Integer getReleaseId()
        {
            return _releaseId;
        }

        public void setReleaseId(Integer releaseId)
        {
            _releaseId = releaseId;
        }

        public Boolean getIncludeGenome()
        {
            return _includeGenome;
        }

        public void setIncludeGenome(Boolean includeGenome)
        {
            _includeGenome = includeGenome;
        }
    }

    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class RequestHelpAction extends MutatingApiAction<RequestHelpForm>
    {
        @Override
        public void validateForm(RequestHelpForm form, Errors errors)
        {
            Container mGapContainer = mGAPManager.get().getMGapContainer();
            if (mGapContainer == null)
            {
                errors.reject(ERROR_MSG, "The mGAP project has not been set on this server.  This is an administrator error.");
                return;
            }

            if (StringUtils.isEmpty(form.getEmail()) || StringUtils.isEmpty(form.getComment()))
            {
                errors.reject(ERROR_REQUIRED, "Must provide both an email address and question/comment");
            }
            else
            {
                try
                {
                    new ValidEmail(form.getEmail());
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    errors.reject(ERROR_MSG, "Your email address is not valid. Please verify your email address below.");
                }
            }
        }

        @Override
        public Object execute(RequestHelpForm form, BindException errors) throws Exception
        {
            ValidEmail email = new ValidEmail(form.getEmail());
            Set<User> users = mGAPManager.get().getNotificationUsers();
            if (users != null && !users.isEmpty())
            {
                try
                {
                    Set<Address> emails = new HashSet<>();
                    for (User u : users)
                    {
                        emails.add(new InternetAddress(u.getEmail()));
                    }

                    MailHelper.MultipartMessage mail = MailHelper.createMultipartMessage();
                    mail.setEncodedHtmlContent("A support request was submitted from mGap by:" + form.getEmail() + "<br><br>Message:<br>" + form.getComment());
                    mail.setFrom(AppProps.getInstance().getAdministratorContactEmail());
                    mail.setSubject("mGap Help Request");
                    mail.addRecipients(Message.RecipientType.TO, emails.toArray(new Address[emails.size()]));

                    MailHelper.send(mail, getUser(), getContainer());
                }
                catch (Exception e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }
            else
            {
                _log.error("A help request was received by mGAP, but the admin emails have not been configured.  The request from: " + form.getEmail());
                _log.error(form.getComment());
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class RequestHelpForm
    {
        private String _email;
        private String _comment;

        public String getEmail()
        {
            return _email;
        }

        public void setEmail(String email)
        {
            _email = email;
        }

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }
    }
}