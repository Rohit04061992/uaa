package org.cloudfoundry.identity.uaa.integration.pageObjects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;

public class HomePage extends Page {
    static final protected String urlPath = "/";

    public HomePage(WebDriver driver) {
        super(driver);
        validateUrl(driver, endsWith(urlPath));
        validatePageSource(driver, containsString("Where to?"));
    }

    public boolean hasLastLoginTime() {
        WebElement lastLoginTime = driver.findElement(By.id("last_login_time"));
        String loginTime = lastLoginTime.getText();
        return loginTime != null && ! loginTime.isBlank();
    }
}

