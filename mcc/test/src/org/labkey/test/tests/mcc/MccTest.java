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

package org.labkey.test.tests.mcc;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.ModulePropertyValue;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.External;
import org.labkey.test.categories.LabModule;
import org.labkey.test.util.DataRegionTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Category({External.class, LabModule.class})
public class MccTest extends BaseWebDriverTest
{
    @Test
    public void testMccModule() throws Exception
    {
        _containerHelper.enableModule("Mcc");

        doRequestFormTest();
        doRequestFormTestWithFailure();

        testInvalidId();
    }

    private static class FormElement
    {
        String inputType = "input";
        String databaseValue = null;

        final String inputName;
        final Object fieldValue;
        final String databaseFieldName;

        public FormElement(String inputName, String databaseFieldName, Object fieldValue)
        {
            this.inputName = inputName;
            this.fieldValue = fieldValue;
            this.databaseFieldName = databaseFieldName;
        }

        public String getDatabaseFieldName()
        {
            return databaseFieldName;
        }

        public FormElement checkBox()
        {
            if (!(fieldValue instanceof Boolean))
            {
                throw new IllegalStateException("Checkbox must have a boolean input fieldValue");
            }

            this.inputType = "checkbox";
            return this;
        }

        public FormElement select(String databaseValue)
        {
            this.databaseValue = databaseValue;
            this.inputType = "select";

            return this;
        }

        public FormElement radio()
        {
            if (!(fieldValue instanceof Boolean))
            {
                throw new IllegalStateException("Radio must have a boolean input fieldValue");
            }

            this.inputType = "radio";
            return this;
        }

        public Object getDatabaseValue()
        {
            return databaseValue == null ? fieldValue : databaseValue;
        }

        public FormElement inputType(String inputType)
        {
            this.inputType = inputType;
            return this;
        }

        public String getInputName()
        {
            if ("radio".equals(inputType))
            {
                return inputName + ((boolean)fieldValue ? "-yes" : "-no");
            }

            return inputName;
        }

        public Locator getLocator()
        {
            return Locator.tagWithId("radio".equals(inputType) || "checkbox".equals(inputType) ? "input" : inputType, getInputName());
        }

        public void waitFor(BaseWebDriverTest test)
        {
            test.waitForElement(getLocator());
        }

        public void setFieldValue(BaseWebDriverTest test)
        {
            waitFor(test);
            if ("checkbox".equals(inputType) || "radio".equals(inputType))
            {
                test.waitAndClick(getLocator());
            }
            else
            {
                test.setFormElement(getLocator(), String.valueOf(fieldValue));
            }
        }
    }

    // Field name / field value:
    final FormElement[] FORM_DATA = new FormElement[]{
            new FormElement("investigator-last-name", "lastName", "last name"),
            new FormElement("investigator-first-name", "firstName", "first name"),
            new FormElement("investigator-middle-initial", "middleinitial", "m"),
            new FormElement("is-early-stage-investigator", "earlystageinvestigator", true).radio(),
            new FormElement("institution-name", "institutionname", "institution name"),
            new FormElement("institution-city", "institutioncity", "institution city"),
            new FormElement("institution-state", "institutionstate", "institution state"),
            new FormElement("institution-type", "institutiontype", "Minority serving").select("minorityServing"),
            new FormElement("institution-country", "institutioncountry", "institution country"),
            new FormElement("official-last-name", "officiallastname", "official last name"),
            new FormElement("official-first-name", "officialfirstname", "official first name"),
            new FormElement("official-email", "officialemail", "official@email.com"),
            //TODO: multi-select
            new FormElement("funding-source", "fundingsource", "NIH-supported research").select("nih"),
            new FormElement("funding-grant-number", "grantnumber", "grant number"),
            new FormElement("experiment-rationale", "experimentalrationale", "rationale").inputType("textarea"),
            new FormElement("methods-proposed", "methodsproposed", "methods").inputType("textarea"),
            new FormElement("collaborations", "collaborations", "collaborations").inputType("textarea"),
            new FormElement("animal-breeding-is-planning-to-breed-animals", "isbreedinganimals", false).radio(),
            new FormElement("research-area", "researcharea", "Rare disease research").select("rare-disease"),
            new FormElement("existing-marmoset-colony", "existingmarmosetcolony", "Existing marmoset colony").select("existing"),
            new FormElement("existing-nhp-facilities", "existingnhpfacilities", "Existing NHP facilities").select("existing"),
            new FormElement("animal-welfare", "animalwelfare", "welfare").inputType("textarea"),
            new FormElement("certify", "certify", true).checkBox(),
            new FormElement("vet-last-name", "vetlastname", "vet last name"),
            new FormElement("vet-first-name", "vetfirstname", "vet first name"),
            new FormElement("vet-email", "vetemail", "vet@email.com"),
            new FormElement("iacuc-approval", "iacucapproval", "Provisional").select("provisional")
    };

    private Locator getButton(String text)
    {
        return Locator.tagWithText("button", text);
    }

    private void goToAnimalRequests()
    {
        goToProjectHome();
        waitAndClickAndWait(Locator.tagContainingText("div", "Animal Requests"));

        waitForElement(Locator.tagWithText("a", "Submit New Animal Request"));
    }

    private void waitForSaveToComplete()
    {
        waitForElementToDisappear(Locator.tagWithText("h2", "Saving").withClass("text-xl"));
    }

    private void doRequestFormTest() throws Exception
    {
        goToAnimalRequests();
        waitAndClickAndWait(Locator.tagWithText("a", "Submit New Animal Request"));
        waitForElement(getButton("Save"));

        setAllFormValues();

        addCohort(0);

        addCoinvestigator(0);
        addCoinvestigator(1);

        waitAndClick(getButton("Save"));
        waitForSaveToComplete();

        // Verify record created:
        Map<String, Object> request = getLastModifiedRequestRow();
        for (FormElement f : FORM_DATA)
        {
            Assert.assertEquals(request.get(f.databaseFieldName), f.getDatabaseValue());
        }

        assertCohortValues((String)request.get("objectid"), 1);
        assertCoinvestigatorValues((String)request.get("objectid"), 2);
    }

    private FormElement[] getCoinvestigatorFields(int idx)
    {
        return new FormElement[]{
            new FormElement("coinvestigators-" + idx + "-lastName", "lastname", "coinvestigators-lastName"),
            new FormElement("coinvestigators-" + idx + "-firstName", "firstname", "coinvestigators-firstName"),
            new FormElement("coinvestigators-" + idx + "-middleInitial", "middleInitial", "mi"),
            new FormElement("coinvestigators-" + idx + "-institution", "institution", "coinvestigators-institution")
        };
    }

    private FormElement[] getCohortFields(int idx)
    {
        return new FormElement[]{
                new FormElement("animal-cohorts-" + idx + "-numberofanimals", "numberofanimals", 12),
                new FormElement("animal-cohorts-" + idx + "-sex", "sex", "Male").select("male"),
                new FormElement("animal-cohorts-" + idx + "-othercharacteristics", "institution", "cohort-othercharacteristics").inputType("textarea")
        };
    }

    private void addCoinvestigator(int idx)
    {
        waitAndClick(Locator.tagWithAttribute("input", "value", "Add Co-investigator"));

        FormElement[] fields = getCoinvestigatorFields(idx);
        for (FormElement f : fields)
        {
            f.setFieldValue(this);
        }
    }

    private void addCohort(int idx)
    {
        if (idx > 0)
        {
            waitAndClick(Locator.tagWithText("button", "Add Cohort"));
        }

        FormElement[] cohort = getCohortFields(idx);
        for (FormElement f : cohort)
        {
            f.setFieldValue(this);
        }
    }

    private void assertCoinvestigatorValues(String requestId, int expectedRecords) throws Exception
    {
        List<Map<String, Object>> rows = getCoinvestigatorRecords(requestId);
        Assert.assertEquals(rows.size(), expectedRecords);

        for (int i=0;i<rows.size();i++)
        {
            Map<String, Object> row = rows.get(i);
            FormElement[] cohort = getCoinvestigatorFields(i);
            for (FormElement f : cohort)
            {
                Assert.assertEquals(f.getDatabaseValue(), row.get(f.getDatabaseFieldName()));
            }
        }
    }

    private void assertCohortValues(String requestId, int expectedRecords) throws Exception
    {
        List<Map<String, Object>> cohortRows = getCohortRecords(requestId);
        Assert.assertEquals(cohortRows.size(), expectedRecords);

        for (int i=0;i<cohortRows.size();i++)
        {
            Map<String, Object> row = cohortRows.get(i);
            FormElement[] cohort = getCohortFields(i);
            for (FormElement f : cohort)
            {
                Assert.assertEquals(f.getDatabaseValue(), row.get(f.getDatabaseFieldName()));
            }
        }
    }

    private Map<String, Object> getLastModifiedRequestRow() throws Exception
    {
        SelectRowsCommand sr = new SelectRowsCommand("mcc", "animalrequests");
        sr.addSort(new Sort("modified", Sort.Direction.DESCENDING));
        List<String> cols = new ArrayList<>(Arrays.stream(FORM_DATA).map(FormElement::getDatabaseFieldName).collect(Collectors.toList()));
        cols.add("objectid");
        sr.setColumns(cols);

        SelectRowsResponse srr = sr.execute(createDefaultConnection(), getProjectName());
        return srr.getRows().get(0);
    }

    private List<Map<String, Object>> getCohortRecords(String requestId) throws Exception
    {
        SelectRowsCommand sr = new SelectRowsCommand("mcc", "requestcohorts");
        sr.addSort(new Sort("rowid", Sort.Direction.ASCENDING));
        sr.setColumns(Arrays.stream(getCohortFields(0)).map(FormElement::getDatabaseFieldName).collect(Collectors.toList()));
        sr.addFilter(new Filter("requestid", requestId));

        SelectRowsResponse srr = sr.execute(createDefaultConnection(), getProjectName());

        return srr.getRows();
    }

    private List<Map<String, Object>> getCoinvestigatorRecords(String requestId) throws Exception
    {
        SelectRowsCommand sr = new SelectRowsCommand("mcc", "coinvestigators");
        sr.addSort(new Sort("rowid", Sort.Direction.ASCENDING));
        sr.setColumns(Arrays.stream(getCohortFields(0)).map(FormElement::getDatabaseFieldName).collect(Collectors.toList()));
        sr.addFilter(new Filter("requestid", requestId));

        SelectRowsResponse srr = sr.execute(createDefaultConnection(), getProjectName());

        return srr.getRows();
    }

    private void doRequestFormTestWithFailure() throws Exception
    {
        goToAnimalRequests();

        waitAndClickAndWait(Locator.tagWithText("a", "Submit New Animal Request"));
        waitForElement(getButton("Submit"));

        // This omits a required field
        setFormValues(Arrays.asList("investigator-last-name"));
        waitAndClick(getButton("Submit"));

        waitForElement(Locator.tagWithText("li", "Last Name: Please fill out this field."));

        // Add Co-I
        waitAndClick(Locator.tagWithAttribute("input", "value", "Add Co-investigator"));
        waitForElement(Locator.tagWithText("li", "Institution: Please fill out this field."));

        waitForElementToDisappear(Locator.tagWithText("li", "Institution: Please fill out this field."));

        // Add another, which makes it invalid again
        waitAndClick(Locator.tagWithAttribute("input", "value", "Add Co-investigator"));
        waitAndClick(getButton("Submit"));
        waitForElement(Locator.tagWithText("li", "Institution: Please fill out this field."));

        // Check for error messages. Now remove that Co-I block
        waitAndClick(Locator.tagWithText("p", "Co-Investigator 2").parent("div").followingSibling("div").index(0).child("input"));

        // Ensure form still in error-reporting state
        waitForElement(Locator.tagWithText("li", "Last Name: Please fill out this field."));

        // Repeat for Cohorts
        waitAndClick(Locator.tagWithAttribute("input", "value", "Add Cohort"));
        Locator removeBtn = Locator.tagWithText("p", "Cohort 2").parent("div").followingSibling("div").index(0).child("input");
        waitForElement(removeBtn);
        scrollIntoView(Locator.tagWithAttribute("input", "value", "Add Cohort"));

        // NOTE: this message seems to be browser-dependent. On Chrome is says 'Please fill out this field', but FF says 'Please enter a number'
        waitForElement(Locator.tagContainingText("li", "Number of Animals: Please"));
        waitAndClick(removeBtn);

        // Even though last name is missing, it should still be savable:
        waitAndClickAndWait(getButton("Save"));
        waitForSaveToComplete();

        doAndWaitForPageToLoad(() -> {
            waitAndClickAndWait(getButton("Cancel"));
            assertAlert("You are about to leave this page");
        });

        // Now reopen this form:
        DataRegionTable dr = new DataRegionTable.DataRegionFinder(getDriver()).waitFor();
        dr.clickEditRow(0);

        getFormElementByName("investigator-last-name").waitFor(this);

        // TODO: make sure the field values are remembered
        // including cohort and co-investigator
        for (FormElement f : FORM_DATA)
        {
            if ("investigator-last-name".equals(f.getInputName()))
            {
                continue;
            }

            Assert.assertEquals(getFormElementByName(f.inputName), f.fieldValue);
        }

        // Now fill in the missing field:
        getFormElementByName("investigator-last-name").setFieldValue(this);

        waitAndClickAndWait(getButton("Submit"));

        dr = new DataRegionTable.DataRegionFinder(getDriver()).waitFor();
        Assert.assertEquals("Submitted", dr.getDataAsText(0, "status"));

        dr.clickEditRow(0);
        getFormElementByName("investigator-last-name").waitFor(this);

        waitForElement(getButton("Update Request"));
        assertElementNotPresent(getButton("Submit"));
        impersonateRoles("Editor", "MCC Requestor");

        assertElementNotPresent(getButton("Save"));
        assertElementNotPresent(getButton("Submit"));
        assertElementNotPresent(getButton("Approve Request"));
    }

    private FormElement getFormElementByName(String name)
    {
        for (FormElement e : FORM_DATA)
        {
            if (name.equals(e.databaseFieldName))
            {
                return e;
            }
        }

        throw new IllegalArgumentException("Unknown field: " + name);
    }

    private void setAllFormValues()
    {
        setFormValues(null);
    }

    private void setFormValues(@Nullable Collection<String> fieldsToSkip)
    {
        for (FormElement el : FORM_DATA)
        {
            if (fieldsToSkip != null && fieldsToSkip.contains(el.databaseFieldName))
            {
                continue;
            }

            el.setFieldValue(this);
        }
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @BeforeClass
    public static void setupProject() throws Exception
    {
        MccTest init = (MccTest)getCurrentTest();
        init.doSetup();
    }

    private void doSetup() throws Exception
    {
        // NOTE: delay setting module properties until the study exists, since dashboard depends on it
        _containerHelper.createProject(getProjectName());
        _containerHelper.enableModules(Arrays.asList("MCC", "Study"));

        importStudy();

        _containerHelper.setFolderType("MCC");
        setModuleProperties(Arrays.asList(
                new ModulePropertyValue("MCC", "/", "MCCContainer", "/" + getProjectName()),
                new ModulePropertyValue("MCC", "/", "MCCRequestContainer", "/" + getProjectName()),
                new ModulePropertyValue("MCC", "/", "MCCContactUsers", getCurrentUserName()),
                new ModulePropertyValue("MCC", "/", "MCCRequestNotificationUsers", getCurrentUserName())
        ));
    }

    private void testInvalidId()
    {
        beginAt("/mcc/" + getProjectName() + "/animalRequest.view?requestId=foo");

        assertElementPresent(Locator.tagWithText("div", "There is no request with id: foo"));
    }


    private void importStudy()
    {
        beginAt(WebTestHelper.getBaseURL() + "/mcc/" + getProjectName() + "/importStudy.view");
        clickButton("OK");
        waitForPipelineJobsToComplete(1, "Study import", false, MAX_WAIT_SECONDS * 2500);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Override
    protected String getProjectName()
    {
        return "MccTestProject";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Collections.singletonList("Mcc");
    }
}