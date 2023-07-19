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

package org.labkey.test.tests.mgap;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.util.Pair;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.External;
import org.labkey.test.categories.LabModule;
import org.labkey.test.tests.external.labModules.JBrowseTest;
import org.labkey.test.tests.external.labModules.SequenceTest;
import org.labkey.test.util.external.labModules.LabModuleHelper;

import java.util.List;

@Category({External.class, LabModule.class})
public class MgapTest extends BaseWebDriverTest
{
    protected LabModuleHelper _helper = new LabModuleHelper(this);

    @Test
    public void testMgapModule() throws Exception
    {
        setupTest();

    }

    private void setupTest() throws Exception
    {
        _containerHelper.createProject(getProjectName(), "mGAP");

        goToProjectHome();

        beginAt("/mgap/" + getProjectName() + "/updateAnnotations.view");
        waitAndClickAndWait(Locator.button("OK"));

        if (!SequenceTest.isExternalPipelineEnabled(getProjectName()))
        {
            log("JBrowseTest.testFullTextSearch() requires external tools, including DISCVRSeq.jar, skipping");
            return;
        }

        String seq = SequenceTest.readSeqFromFile(JBrowseTest.GRCH37_GENOME);
        SequenceTest.ensureRefSeqExists(this, "1", seq);
        SequenceTest.createReferenceGenome(this, 1, JBrowseTest.JB_GENOME_NAME, "1");

        SequenceTest.addOutputFile(this, JBrowseTest.MGAP_TEST_VCF, JBrowseTest.JB_GENOME_NAME, "TestVCF", "VCF File", "This is an output file to test VCF full-text search", false);
    }

    private void testFullTextSearch() throws Exception
    {
        if (!SequenceTest.isExternalPipelineEnabled(getProjectName()))
        {
            log("JBrowseTest.testFullTextSearch() requires external tools, including DISCVRSeq.jar, skipping");
            return;
        }

        Pair<String, String> info = JBrowseTest.prepareSearchSession(this, _helper, getProjectName(), false);
        String sessionId = info.first;
        String trackId = info.second;


    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Override
    protected String getProjectName()
    {
        return "MgapTestProject";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return List.of("jbrowse", "mGAP");
    }
}