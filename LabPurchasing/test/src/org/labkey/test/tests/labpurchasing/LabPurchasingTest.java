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
        beginAt("/" + getProjectName() + "/labpurchasing-populateData.view");

        waitAndClick(Ext4Helper.Locators.ext4Button("Delete All"));
        waitForElement(Locator.tagWithText("div", "Delete Complete"));

        waitAndClick(Ext4Helper.Locators.ext4Button("Populate Reference Vendors"));
        waitForElement(Locator.tagWithText("div", "Populating vendors..."));
        waitForElement(Locator.tagWithText("div", "Populate Complete"));

        waitAndClick(Ext4Helper.Locators.ext4Button("Populate Units"));
        waitForElement(Locator.tagWithText("div", "Populating purchasingUnits..."));
        waitForElement(Locator.tagWithText("div", "Populate Complete"));

        waitAndClick(Ext4Helper.Locators.ext4Button("Populate Reference Items"));
        waitForElement(Locator.tagWithText("div", "Populating referenceItems..."));
        waitForElement(Locator.tagWithText("div", "Populate Complete"), WAIT_FOR_PAGE);
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
        Ext4FieldRef.getForLabel(this, "Phone").setValue("555-555-5555");
        sleep(200); //let formbind work
        waitAndClick(Ext4Helper.Locators.ext4Button("Add Vendor"));
        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        click(Ext4Helper.Locators.ext4Button("OK"));

        // Adding the new vendor should have updated the combo:
        Ext4ComboRef.getForLabel(this, "Vendor").setComboByDisplayValue("New Vendor 1");

        Ext4FieldRef.getForLabel(this, "Item Name").setValue("Item1");
        Ext4FieldRef.getForLabel(this, "Quantity").setValue(2);
        Ext4FieldRef.getForLabel(this, "Unit Cost").setValue(10);
        Assert.assertEquals(20, Ext4FieldRef.getForLabel(this, "Total Cost").getDoubleValue().intValue());
        waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));
        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        clickAndWait(Ext4Helper.Locators.ext4Button("OK"));

        // Create new item, re-using previous:
        DataRegionTable dr = DataRegionTable.DataRegion(getDriver()).withName("query").waitFor();
        dr.clickHeaderButtonAndWait(DataRegionTable.getInsertNewButtonText());
        Ext4FieldRef.waitForField(this, "Vendor");
        waitAndClick(Ext4Helper.Locators.ext4Button("Re-order Previous Item"));
        new Window.WindowFinder(getDriver()).withTitle("Re-order Previous Item").waitFor();

        // NOTE: this store initially loads with the full list. If we set the vendor combo too quickly that filter event will happen before the original store load
        Ext4ComboRef.waitForField(this, "Item");
        Ext4ComboRef itemField = Ext4ComboRef.getForLabel(this, "Item");
        itemField.waitForStoreLoad();

        Ext4ComboRef vendorField = Ext4ComboRef.getForLabel(this, "Vendor (optional)");
        vendorField.setComboByDisplayValue("AbCam");
        sleep(200);
        itemField.waitForStoreLoad();
        Assert.assertEquals(25L, itemField.getFnEval("return this.store.getCount()"));

        itemField.setComboByDisplayValue("Streptavidin Conjugation Kit (300ug)");
        waitAndClick(Ext4Helper.Locators.ext4Button("Re-order Item"));


        Assert.assertEquals("AbCam", Ext4ComboRef.getForLabel(this, "Vendor").getDisplayValue());
        Assert.assertEquals("Streptavidin Conjugation Kit (300ug)", Ext4FieldRef.getForLabel(this, "Item Name").getValue());
        Assert.assertEquals("ab102921", Ext4FieldRef.getForLabel(this, "Product/Catalog #").getValue());
        Assert.assertEquals("Kit", Ext4FieldRef.getForLabel(this, "Units").getValue());
        Assert.assertEquals(419, Ext4FieldRef.getForLabel(this, "Unit Cost").getValue());

        Ext4FieldRef.getForLabel(this, "Quantity").setValue(2);
        Assert.assertEquals(838, Ext4FieldRef.getForLabel(this, "Total Cost").getValue());

        waitAndClick(Ext4Helper.Locators.ext4Button("Submit"));
        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
        clickAndWait(Ext4Helper.Locators.ext4Button("OK"));

        dr = DataRegionTable.DataRegion(getDriver()).withName("query").waitFor();
        dr.checkCheckbox(1);
        dr.clickHeaderMenu("More Actions", false, "Order Items");
        new Window.WindowFinder(getDriver()).withTitle("Order Items").waitFor();
        Ext4FieldRef.waitForField(this, "Order Number");
        Ext4FieldRef.getForLabel(this, "Order Number").setValue("OrderXXXX");
        clickAndWait(Ext4Helper.Locators.ext4Button("Submit"));

        dr = DataRegionTable.DataRegion(getDriver()).withName("query").waitFor();
        dr.checkCheckbox(1);
        dr.clickHeaderMenu("More Actions", false, "Mark Received");
        new Window.WindowFinder(getDriver()).withTitle("Mark Received").waitFor();
        Ext4FieldRef.waitForField(this, "Item Location");
        Ext4FieldRef.getForLabel(this, "Item Location").setValue("-20 Freezer");
        clickAndWait(Ext4Helper.Locators.ext4Button("Submit"));
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