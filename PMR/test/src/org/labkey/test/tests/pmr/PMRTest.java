/*
 * Copyright (c) 2023 LabKey Corporation
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

package org.labkey.test.tests.pmr;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.ModulePropertyValue;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.External;
import org.labkey.test.categories.LabModule;
import org.labkey.test.util.SqlserverOnlyTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Category({External.class, LabModule.class})
public class PMRTest extends BaseWebDriverTest implements SqlserverOnlyTest
{
    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    @BeforeClass
    public static void setupProject() throws Exception
    {
        PMRTest init = (PMRTest)getCurrentTest();
        init.doSetup();
    }


    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), "PMR");

        setModuleProperties(Arrays.asList(
                new ModulePropertyValue("EHR", "/" + getProjectName(), "EHRStudyContainer", "/" + getProjectName()),
                new ModulePropertyValue("EHR", "/" + getProjectName(), "EHRAdminUser", getCurrentUser())
        ));

        importStudy(getProjectName());

        goToHome();
    }

    private void importStudy(String containerPath)
    {
        beginAt(WebTestHelper.getBaseURL() + "/pmr/" + containerPath + "/importStudy.view");
        clickButton("OK");
        waitForPipelineJobsToComplete(1, "Study import", false, MAX_WAIT_SECONDS * 2500);
    }


    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testPMRModule()
    {
        _containerHelper.enableModule("PMR");
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "PMRTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Collections.singletonList("PMR");
    }
}