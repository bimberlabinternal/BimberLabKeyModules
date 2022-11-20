package org.labkey.test.tests.labpurchasing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.LabModule;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.Collections;
import java.util.List;

@Category({External.class, LabModule.class})
public class LabPurchasingTest extends BaseWebDriverTest
{
    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @BeforeClass
    public static void setupProject()
    {
        LabPurchasingTest init = (LabPurchasingTest)getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), "Laboratory Folder");
        _containerHelper.enableModule("LabPurchasing");
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Test
    public void testLabPurchasingModule()
    {
        beginAt("/" + getProjectName() + "/labpurchasing-begin.view");
        waitForElement(Locator.tagWithText("div", "Enter New Order"));

        waitAndClickAndWait(Locator.tagWithText("div", "Populate Initial Data"));


        // Add aliases


        // Create new item, re-using
        // Create new item from new vendor
        // Ensure item added to reference

        // Create new item from new vendor, adding vendor.

        // Toggle for 'does not need receipt'

        // Make sure in grid
        // Mark received

    }

    @LogMethod(quiet = true)
    protected void deleteDataFrom(@LoggedParam String tableLabel)
    {
        
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return "LabPurchasingTest_Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Collections.singletonList("LabPurchasing");
    }
}