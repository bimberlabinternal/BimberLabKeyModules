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
import org.labkey.test.components.ext4.Window;
import org.labkey.test.util.DataRegion;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.ext4cmp.Ext4ComboRef;
import org.labkey.test.util.ext4cmp.Ext4FieldRef;
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
        populateData();
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    private void populateData()
    {
        goToProjectHome();

        waitAndClickAndWait(Locator.linkWithText( "Populate Initial Data"));
        waitAndClick(Locator.button("Delete All"));
        waitForElement(Locator.tagWithText("div", "Delete Complete"));

        waitAndClick(Locator.button("Populate Reference Vendors"));
        waitForElement(Locator.tagWithText("div", "Populating vendors..."));
        waitForElement(Locator.tagWithText("div", "Populate Complete"));

        waitAndClick(Locator.button("Populate Units"));
        waitForElement(Locator.tagWithText("div", "Populating purchasingUnits..."));
        waitForElement(Locator.tagWithText("div", "Populate Complete"));

        waitAndClick(Locator.button("Populate Reference Items"));
        waitForElement(Locator.tagWithText("div", "Populating referenceItems..."));
        waitForElement(Locator.tagWithText("div", "Populate Complete"));
    }

    @Test
    public void testLabPurchasingModule()
    {
        beginAt("/" + getProjectName() + "/labpurchasing-begin.view");
        waitAndClickAndWait(Locator.linkWithText("Enter New Order"));

        // Add a vendor:
        waitAndClick(Ext4Helper.Locators.ext4Button("Add New Vendor"));
        Ext4FieldRef.waitForField(this, "Vendor Name");
        Ext4FieldRef.getForLabel(this, "Vendor Name").setValue("New Vendor 1");
        waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));
        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        click(Ext4Helper.Locators.ext4Button("OK"));

        // Adding the new vendor should have updated the combo:
        Ext4ComboRef.getForLabel(this, "Vendor").setValue("New Vendor 1");

        Ext4FieldRef.getForLabel(this, "Item Name").setValue("Item1");
        Ext4FieldRef.getForLabel(this, "Quantity").setValue(2);
        Ext4FieldRef.getForLabel(this, "Unit Cost").setValue(10);
        Assert.assertEquals(20, Ext4FieldRef.getForLabel(this, "Total Cost").getDoubleValue().intValue());
        waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));
        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        clickAndWait(Ext4Helper.Locators.ext4Button("OK"));

        // Create new item, re-using previous:
        DataRegionTable dr = DataRegionTable.DataRegion(getDriver()).withName("query").waitFor();
        dr.clickInsertNewRow();
        Ext4FieldRef.waitForField(this, "Vendor Name");
        waitAndClick(Ext4Helper.Locators.ext4Button("Re-order Previous Item"));
        new Window.WindowFinder(getDriver()).withTitle("Re-order Previous Item").waitFor();
        Ext4FieldRef.getForLabel(this, "Vendor (optional)").setValue("AbCam");
        Ext4ComboRef field = Ext4ComboRef.getForLabel(this, "Item");
        field.waitForStoreLoad();
        Assert.assertEquals(25, field.getFnEval("return this.store.getCount()"));

        field.setValue("Streptavidin Conjugation Kit (300ug)");

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