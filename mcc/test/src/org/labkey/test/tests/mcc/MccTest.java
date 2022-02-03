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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.ModulePropertyValue;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.LabModule;
import org.labkey.test.util.DataRegionTable;
import org.openqa.selenium.Keys;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Category({External.class, LabModule.class})
public class MccTest extends BaseWebDriverTest
{
    @Test
    public void testMccModule() throws Exception
    {
        _containerHelper.enableModule("Mcc");

        doRequestFormTest();
    }

    private static class FormElement
    {
        String inputType = "input";
        String databaseFieldName = null;
        boolean isCheckBox = false;

        final String inputName;
        final CharSequence sendKeysValue;

        public FormElement(String inputName, CharSequence sendKeysValue)
        {
            this.inputName = inputName;
            this.sendKeysValue = sendKeysValue;
        }

        public FormElement withDatabaseField(String databaseFieldName)
        {
            this.databaseFieldName = databaseFieldName;

            return this;
        }

        public FormElement isCheckBox()
        {
            this.isCheckBox = true;
            return this;
        }

        public FormElement withInput(String inputType)
        {
            this.inputType = inputType;

            return this;
        }

        public String getFieldName()
        {
            return inputName;
        }

        public String getInputType()
        {
            return inputType;
        }

        public void waitFor(BaseWebDriverTest test)
        {
            Locator loc = Locator.tagWithId(getInputType(), getFieldName());
            test.waitForElement(loc);
        }

        public void setFieldValue(BaseWebDriverTest test)
        {
            Locator loc = Locator.tagWithId(getInputType(), getFieldName());
            waitFor(test);
            if (this.sendKeysValue == null)
            {
                test.waitAndClick(loc);
            }
            else
            {
                loc.findElement(test.getDriver()).sendKeys(this.sendKeysValue);
            }

        }
    }

    // Field name / field value:
    final FormElement[] FORM_DATA = new FormElement[]{
            new FormElement("investigator-last-name", "last name"),
            new FormElement("investigator-first-name", "first name"),
            new FormElement("investigator-middle-initial", "middle initial"),
            new FormElement("is-principal-investigator-yes", null).isCheckBox(),
            new FormElement("institution-name", "institution name"),
            new FormElement("institution-city", "institution city"),
            new FormElement("institution-state", "institution state"),
            new FormElement("institution-country", "institution country"),
            new FormElement("official-last-name", "official last name"),
            new FormElement("official-first-name", "official first name"),
            new FormElement("official-email", "official@email.com"),
            new FormElement("funding-source", Keys.ARROW_DOWN).withInput("select"),
            new FormElement("funding-grant-number", "grant number"),
            new FormElement("experiment-rationale", "rationale").withInput("textarea"),


            new FormElement("animal-cohorts-0-numberofanimals", "1"),
            new FormElement("animal-cohorts-0-sex", Keys.ARROW_DOWN).withInput("select"),
            new FormElement("animal-cohorts-0-othercharacteristics", "characteristics").withInput("textarea"),
            new FormElement("methods-proposed", "methods").withInput("textarea"),
            new FormElement("collaborations", "collaborations").withInput("textarea"),
            new FormElement("animal-breeding-is-planning-to-breed-animals-no", null).isCheckBox(),
            new FormElement("research-area", Keys.ARROW_DOWN).withInput("select"),
            new FormElement("existing-marmoset-colony",  Keys.ARROW_DOWN).withInput("select"),
            new FormElement("existing-nhp-facilities", Keys.ARROW_DOWN).withInput("select"),
            new FormElement("animal-welfare", "welfare").withInput("textarea"),
            new FormElement("certify", null).isCheckBox(),
            new FormElement("vet-last-name", "vet last name"),
            new FormElement("vet-first-name", "vet first name"),
            new FormElement("vet-email", "vet@email.com"),
            new FormElement("iacuc-approval", Keys.ARROW_DOWN).withInput("select")
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

    private void doRequestFormTest() throws Exception
    {
        goToAnimalRequests();
        waitAndClickAndWait(Locator.tagWithText("a", "Submit New Animal Request"));
        waitForElement(getButton("Submit"));

        setFormValues(null);

        waitAndClickAndWait(getButton("Save"));

        // Verify record created:
        Map<String, Object> request = getLastModifiedRequestRow();
        for (FormElement f : FORM_DATA)
        {
            //TODO: check data
        }
    }

    private Map<String, Object> getLastModifiedRequestRow() throws Exception
    {
        SelectRowsCommand sr = new SelectRowsCommand("mcc", "animalrequests");
        sr.addSort(new Sort("modified", Sort.Direction.DESCENDING));
        SelectRowsResponse srr = sr.execute(createDefaultConnection(), getProjectName());
        return srr.getRows().get(0);
    }

    private void doRequestFormTestWithFailure() throws Exception
    {
        goToAnimalRequests();

        waitAndClickAndWait(Locator.tagWithText("a", "Submit New Animal Request"));
        waitForElement(getButton("Submit"));

        // This omits a required field
        setFormValues(Arrays.asList("investigator-last-name"));
        waitAndClick(getButton("Submit"));

        //TODO: ensure errors messages present

        // To full exercise saving, add a cohort and co-investigator

        // TODO: Now add one more cohort, but dont fill it out. This should be invalid.
        waitAndClick(getButton("Submit"));
        // Check for cohort error messages. Now remove that cohort

        // Repeat for Co-Investigator

        // Even though last name is missing, it should still be savable:
        waitAndClickAndWait(getButton("Save"));

        // Now reopen this form:
        DataRegionTable dr = new DataRegionTable.DataRegionFinder(getDriver()).waitFor();
        dr.clickEditRow(0);

        getFormElementByName("investigator-last-name").waitFor(this);

        // TODO: make sure the field values are remembered
        // including cohort and co-investigator

        // Now fill in the missing field:
        getFormElementByName("investigator-last-name").setFieldValue(this);

        waitAndClickAndWait(getButton("Submit"));
    }

    private FormElement getFormElementByName(String name)
    {
        for (FormElement e : FORM_DATA)
        {
            if (name.equals(e.getFieldName()))
            {
                return e;
            }
        }

        throw new IllegalArgumentException("Unknown field: " + name);
    }

    private void setFormValues(@Nullable Collection<String> fieldsToSkip)
    {
        for (FormElement el : FORM_DATA)
        {
            if (fieldsToSkip != null && fieldsToSkip.contains(el.getFieldName()))
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
    public static void setupProject()
    {
        MccTest init = (MccTest)getCurrentTest();
        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), "MCC");
        setModuleProperties(Arrays.asList(
                new ModulePropertyValue("MCC", "/", "MCCContainer", "/" + getProjectName()),
                new ModulePropertyValue("MCC", "/", "MCCRequestContainer", "/" + getProjectName())
        ));
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