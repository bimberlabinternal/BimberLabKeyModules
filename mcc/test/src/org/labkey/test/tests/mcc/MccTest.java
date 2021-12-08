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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.LabModule;
import org.labkey.test.Locator;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.Keys;

import java.util.Collections;
import java.util.List;

@Category({External.class, LabModule.class})
public class MccTest extends BaseWebDriverTest
{
    @Test
    public void testMccModule() throws Exception
    {
        _containerHelper.enableModule("Mcc");

        doRequestFormTest();
    }

    private void doRequestFormTest() throws Exception
    {
        beginAt("/home/mcc-animalRequest.view");

        Locator investigatorLastName = Locator.tagWithId("input", "investigator-last-name");
        waitForElement(investigatorLastName);
        WebElement iLastNameElement = investigatorLastName.findElement(getDriver());
        iLastNameElement.sendKeys("last name");

        Locator investigatorFirstName = Locator.tagWithId("input", "investigator-first-name");
        waitForElement(investigatorFirstName);
        WebElement investigatorFirstNameElement = investigatorFirstName.findElement(getDriver());
        investigatorFirstNameElement.sendKeys("first name");

        Locator investigatorMiddleInitial = Locator.tagWithId("input", "investigator-middle-initial");
        waitForElement(investigatorMiddleInitial);
        WebElement investigatorMiddleInitialElement = investigatorMiddleInitial.findElement(getDriver());
        investigatorMiddleInitialElement.sendKeys("middle initial");

        Locator isPrincipalInvestigatorYes = Locator.tagWithId("input", "is-principal-investigator-yes");
        waitForElement(isPrincipalInvestigatorYes);
        waitAndClick(isPrincipalInvestigatorYes);

        Locator institutionName = Locator.tagWithId("input", "institution-name");
        waitForElement(institutionName);
        WebElement institutionNameElement = institutionName.findElement(getDriver());
        institutionNameElement.sendKeys("institution name");

        Locator institutionCity = Locator.tagWithId("input", "institution-city");
        waitForElement(institutionCity);
        WebElement institutionCityElement = institutionCity.findElement(getDriver());
        institutionCityElement.sendKeys("institution city");

        Locator institutionState = Locator.tagWithId("input", "institution-state");
        waitForElement(institutionState);
        WebElement institutionStateElement = institutionState.findElement(getDriver());
        institutionStateElement.sendKeys("institution state");

        Locator institutionCountry = Locator.tagWithId("input", "institution-country");
        waitForElement(institutionCountry);
        WebElement institutionCountryElement = institutionCountry.findElement(getDriver());
        institutionCountryElement.sendKeys("institution country");

        Locator institutionType = Locator.tagWithId("select", "institution-type");
        waitForElement(institutionType);
        waitAndClick(institutionType);
        WebElement institutionTypeElement = institutionType.findElement(getDriver());
        institutionTypeElement.sendKeys(Keys.ARROW_DOWN);

        Locator officialLastName = Locator.tagWithId("input", "official-last-name");
        waitForElement(officialLastName);
        WebElement officialLastNameElement = officialLastName.findElement(getDriver());
        officialLastNameElement.sendKeys("official last name");

        Locator officialFirstName = Locator.tagWithId("input", "official-first-name");
        waitForElement(officialFirstName);
        WebElement officialFirstNameElement = officialFirstName.findElement(getDriver());
        officialFirstNameElement.sendKeys("official first name");

        Locator officialEmail = Locator.tagWithId("input", "official-email");
        waitForElement(officialEmail);
        WebElement officialEmailElement = officialEmail.findElement(getDriver());
        officialEmailElement.sendKeys("official email");

        Locator fundingSource = Locator.tagWithId("select", "funding-source");
        waitForElement(fundingSource);
        waitAndClick(fundingSource);
        WebElement fundingSourceElement = fundingSource.findElement(getDriver());
        fundingSourceElement.sendKeys(Keys.ARROW_DOWN);

        Locator grantNumber = Locator.tagWithId("input", "funding-grant-number");
        waitForElement(grantNumber);
        WebElement grantNumberElement = grantNumber.findElement(getDriver());
        grantNumberElement.sendKeys("grant number");

        Locator experimentalRationale = Locator.tagWithId("textarea", "experiment-rationale");
        waitForElement(experimentalRationale);
        WebElement experimentalRationaleElement = experimentalRationale.findElement(getDriver());
        experimentalRationaleElement.sendKeys("rationale");

        Locator numberOfAnimals = Locator.tagWithId("input", "animal-cohorts-0-numberofanimals");
        waitForElement(numberOfAnimals);
        WebElement numberOfAnimalsElement = numberOfAnimals.findElement(getDriver());
        numberOfAnimalsElement.sendKeys("1");

        Locator cohortSex = Locator.tagWithId("select", "animal-cohorts-0-sex");
        waitForElement(cohortSex);
        waitAndClick(cohortSex);
        WebElement cohortSexElement = cohortSex.findElement(getDriver());
        cohortSexElement.sendKeys(Keys.ARROW_DOWN);

        Locator otherCharacteristics = Locator.tagWithId("textarea", "animal-cohorts-0-othercharacteristics");
        waitForElement(otherCharacteristics);
        WebElement otherCharacteristicsElement = otherCharacteristics.findElement(getDriver());
        otherCharacteristicsElement.sendKeys("characteristics");

        Locator methodsProposed = Locator.tagWithId("textarea", "methods-proposed");
        waitForElement(methodsProposed);
        WebElement methodsProposedElement = methodsProposed.findElement(getDriver());
        methodsProposedElement.sendKeys("methods");

        Locator collaborations = Locator.tagWithId("textarea", "collaborations");
        waitForElement(collaborations);
        WebElement collaborationsElement = collaborations.findElement(getDriver());
        collaborationsElement.sendKeys("collaborations");

        Locator isBreedingAnimalsNo = Locator.tagWithId("input", "animal-breeding-is-planning-to-breed-animals-no");
        waitForElement(isBreedingAnimalsNo);
        waitAndClick(isBreedingAnimalsNo);

        Locator researchArea = Locator.tagWithId("select", "research-area");
        waitForElement(researchArea);
        waitAndClick(researchArea);
        WebElement researchAreaElement = researchArea.findElement(getDriver());
        researchAreaElement.sendKeys(Keys.ARROW_DOWN);

        Locator existingMarmosetColony = Locator.tagWithId("select", "existing-marmoset-colony");
        waitForElement(existingMarmosetColony);
        waitAndClick(existingMarmosetColony);
        WebElement existingMarmosetColonyElement = existingMarmosetColony.findElement(getDriver());
        existingMarmosetColonyElement.sendKeys(Keys.ARROW_DOWN);

        Locator existingNHPFacilities = Locator.tagWithId("select", "existing-nhp-facilities");
        waitForElement(existingNHPFacilities);
        waitAndClick(existingNHPFacilities);
        WebElement existingNHPFacilitiesElement = existingNHPFacilities.findElement(getDriver());
        existingNHPFacilitiesElement.sendKeys(Keys.ARROW_DOWN);

        Locator animalWelfare = Locator.tagWithId("textarea", "animal-welfare");
        waitForElement(animalWelfare);
        WebElement animalWelfareElement = animalWelfare.findElement(getDriver());
        animalWelfareElement.sendKeys("welfare");

        Locator certifyCheckbox = Locator.tagWithName("input", "certify");
        waitForElement(certifyCheckbox);
        waitAndClick(certifyCheckbox);

        Locator vetLastName = Locator.tagWithId("input", "vet-last-name");
        waitForElement(vetLastName);
        WebElement vetLastNameElement = vetLastName.findElement(getDriver());
        vetLastNameElement.sendKeys("vet last name");

        Locator vetFirstName = Locator.tagWithId("input", "vet-first-name");
        waitForElement(vetFirstName);
        WebElement vetFirstNameElement = vetFirstName.findElement(getDriver());
        vetFirstNameElement.sendKeys("vet first name");

        Locator vetEmail = Locator.tagWithId("input", "vet-email");
        waitForElement(vetEmail);
        WebElement vetEmailElement = vetEmail.findElement(getDriver());
        vetEmailElement.sendKeys("vet email");

        Locator iacucApproval = Locator.tagWithId("select", "iacuc-approval");
        waitForElement(iacucApproval);
        waitAndClick(iacucApproval);
        WebElement iacucApprovalElement = iacucApproval.findElement(getDriver());
        iacucApprovalElement.sendKeys(Keys.ARROW_DOWN);
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
        _containerHelper.createProject(getProjectName());
        _containerHelper.enableModule("MCC");
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Override
    protected String getProjectName()
    {
        return "MccTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Collections.singletonList("Mcc");
    }
}