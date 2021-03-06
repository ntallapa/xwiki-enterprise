/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.test.po;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.xwiki.test.po.platform.ViewPage;
import org.xwiki.test.po.platform.editor.ClassEditPage;
import org.xwiki.test.po.platform.editor.ObjectEditPage;

/**
 * Helper methods for testing, not related to a specific Page Object. Also made available to tests classes.
 * 
 * @version $Id$
 * @since 3.2M3
 */
public class TestUtils
{
    private static final String SCREENSHOT_DIR = System.getProperty("screenshotDirectory");

    private static PersistentTestContext context;

    /**
     * How long to wait before failing a test because an element cannot be found. Can be overridden with setTimeout.
     */
    private int timeout = 10;

    /** Cached secret token. TODO cache for each user. */
    private String secretToken = null;

    /** Used so that AllTests can set the persistent test context. */
    public static void setContext(PersistentTestContext context)
    {
        TestUtils.context = context;
    }

    protected XWikiWrappingDriver getDriver()
    {
        return context.getDriver();
    }

    private final String baseURL = "http://localhost:8080/xwiki/bin/";

    public Session getSession()
    {
        return this.new Session(getDriver().manage().getCookies(), getSecretToken());
    }

    public void setSession(Session session)
    {
        WebDriver.Options options = getDriver().manage();
        options.deleteAllCookies();
        if (session != null) {
            for (Cookie cookie : session.getCookies()) {
                options.addCookie(cookie);
            }
        }
        if (session != null && !StringUtils.isEmpty(session.getSecretToken())) {
            this.secretToken = session.getSecretToken();
        } else {
            recacheSecretToken();
        }
    }

    /**
     * Consider using setSession(null) because it will drop the cookies which is faster than invoking a logout action.
     */
    public String getURLToLogout()
    {
        return getURL("XWiki", "XWikiLogin", "logout");
    }

    public String getURLToLoginAsAdmin()
    {
        return getURLToLoginAs("Admin", "admin");
    }

    public String getURLToLoginAs(final String username, final String password)
    {
        return getURLToLoginAndGotoPage(username, password, null);
    }

    /**
     * @param pageURL the URL of the page to go to after logging in.
     * @return URL to accomplish login and goto.
     */
    public String getURLToLoginAsAdminAndGotoPage(final String pageURL)
    {
        return getURLToLoginAndGotoPage("Admin", "admin", pageURL);
    }

    /**
     * @param username the name of the user to log in as.
     * @param password the password for the user to log in.
     * @param pageURL the URL of the page to go to after logging in.
     * @return URL to accomplish login and goto.
     */
    public String getURLToLoginAndGotoPage(final String username, final String password, final String pageURL)
    {
        Map<String, String> parameters = new HashMap<String, String>()
        {
            {
                put("j_username", username);
                put("j_password", password);
                if (pageURL != null && pageURL.length() > 0) {
                    put("xredirect", pageURL);
                }
            }
        };
        return getURL("XWiki", "XWikiLogin", "loginsubmit", parameters);
    }

    /**
     * @return URL to a non existent page that loads very fast (we are using plain mode so that we don't even have to
     * display the skin ;))
     */
    public String getURLToNonExistentPage()
    {
        return getURL("NonExistentSpace", "NonExistentPage", "view", "xpage=plain");
    }

    /**
     * After successful completion of this function, you are guaranteed to be logged in as the given user and on the
     * page passed in pageURL.
     * 
     * @param pageURL
     */
    public void assertOnPage(final String pageURL)
    {
        final String pageURI = pageURL.replaceAll("\\?.*", "");
        waitUntilCondition(new ExpectedCondition<Boolean>()
        {
            public Boolean apply(WebDriver driver)
            {
                return getDriver().getCurrentUrl().contains(pageURI);
            }
        });
    }

    public <T> void waitUntilCondition(ExpectedCondition<T> condition)
    {
        // Temporarily remove the implicit wait on the driver since we're doing our own waits...
        WebDriver driver = getDriver().getWrappedDriver();
        driver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);

        Wait<WebDriver> wait = new WebDriverWait(driver, getTimeout());
        try {
            wait.until(condition);
        } catch (TimeoutException e) {
            takeScreenshot();
            System.out.println("Page source = [" + getDriver().getPageSource() + "]");
            throw e;
        } finally {
            // Reset timeout
            setDriverImplicitWait(driver);
        }
    }

    public String getLoggedInUserName()
    {
        String loggedInUserName = null;
        List<WebElement> elements = findElementsWithoutWaiting(getDriver(), By.xpath("//div[@id='tmUser']/span/a"));
        if (!elements.isEmpty()) {
            String href = elements.get(0).getAttribute("href");
            loggedInUserName = href.substring(href.lastIndexOf("/") + 1);
        }
        return loggedInUserName;
    }

    public void registerLoginAndGotoPage(final String username, final String password, final String pageURL)
    {
        String registerURL = getURL("XWiki", "Register", "register", new HashMap<String, String>()
        {
            {
                put("register", "1");
                put("xwikiname", username);
                put("register_password", password);
                put("register2_password", password);
                put("register_email", "");
                put("xredirect", getURLToLoginAndGotoPage(username, password, getURLToNonExistentPage()));
                put("form_token", getSecretToken());
            }
        });
        getDriver().get(registerURL);
        recacheSecretToken();
        getDriver().get(pageURL);
    }

    public ViewPage gotoPage(String space, String page)
    {
        gotoPage(space, page, "view");
        return new ViewPage();
    }

    public void gotoPage(String space, String page, String action)
    {
        gotoPage(space, page, action, "");
    }

    public void gotoPage(String space, String page, String action, Map<String, ? > queryParameters)
    {
        getDriver().get(getURL(space, page, action, queryParameters));
    }

    public void gotoPage(String space, String page, String action, String queryString)
    {
        // Only navigate if the current URL is different from the one to go to, in order to improve performances.
        String url = getURL(space, page, action, queryString);
        if (!getDriver().getCurrentUrl().equals(url)) {
            getDriver().get(url);
        }
    }

    public String getURLToDeletePage(String space, String page)
    {
        return getURL(space, page, "delete", "confirm=1");
    }

    public ViewPage createPage(String space, String page, String content, String title)
    {
        return createPage(space, page, content, title, null);
    }

    public ViewPage createPage(String space, String page, String content, String title, String syntaxId)
    {
        return createPage(space, page, content, title, syntaxId, null);
    }

    public ViewPage createPage(String space, String page, String content, String title, String syntaxId,
        String parentFullPageName)
    {
        Map<String, String> queryMap = new HashMap<String, String>();
        if (content != null) {
            queryMap.put("content", content);
        }
        if (title != null) {
            queryMap.put("title", title);
        }
        if (syntaxId != null) {
            queryMap.put("syntaxId", syntaxId);
        }
        if (parentFullPageName != null) {
            queryMap.put("parent", parentFullPageName);
        }
        gotoPage(space, page, "save", queryMap);
        return new ViewPage();
    }

    public void deletePage(String space, String page)
    {
        getDriver().get(getURLToDeletePage(space, page));
    }

    /**
     * Get the URL to view a page.
     * 
     * @param space the space in which the page resides.
     * @param page the name of the page.
     */
    public String getURL(String space, String page)
    {
        return getURL(space, page, "view");
    }

    /**
     * Get the URL of an action on a page.
     * 
     * @param space the space in which the page resides.
     * @param page the name of the page.
     * @param action the action to do on the page.
     */
    public String getURL(String space, String page, String action)
    {
        return getURL(space, page, action, "");
    }

    /**
     * Get the URL of an action on a page with a specified query string.
     * 
     * @param space the space in which the page resides.
     * @param page the name of the page.
     * @param action the action to do on the page.
     * @param queryString the query string to pass in the URL.
     */
    public String getURL(String space, String page, String action, String queryString)
    {
        StringBuilder builder = new StringBuilder(this.baseURL);

        builder.append(action);
        builder.append('/');
        builder.append(escapeURL(space));
        builder.append('/');
        builder.append(escapeURL(page));

        boolean needToAddSecretToken = !("view".equals(action) || "register".equals(action));
        if (needToAddSecretToken || !StringUtils.isEmpty(queryString)) {
            builder.append('?');
        }
        if (needToAddSecretToken) {
            addQueryStringEntry(builder, "form_token", getSecretToken());
            builder.append('&');
        }
        if (!StringUtils.isEmpty(queryString)) {
            builder.append(queryString);
        }

        return builder.toString();
    }

    /**
     * Get the URL of an action on a page with specified parameters. If you need to pass multiple parameters with the
     * same key, this function will not work.
     * 
     * @param space the space in which the page resides.
     * @param page the name of the page.
     * @param action the action to do on the page.
     * @param queryParameters the parameters to pass in the URL, these will be automatically URL encoded.
     */
    public String getURL(String space, String page, String action, Map<String, ? > queryParameters)
    {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, ? > entry : queryParameters.entrySet()) {
            addQueryStringEntry(builder, entry.getKey(), entry.getValue());
            builder.append('&');
        }

        return getURL(space, page, action, builder.toString());
    }

    /**
     * @sice 3.2M1
     */
    public void addQueryStringEntry(StringBuilder builder, String key, Object value)
    {
        if (value != null) {
            if (value instanceof Iterable) {
                for (Object element : (Iterable< ? >) value) {
                    addQueryStringEntry(builder, key, element.toString());
                }
            } else {
                addQueryStringEntry(builder, key, value.toString());
            }
        } else {
            addQueryStringEntry(builder, key, (String) null);
        }
    }

    /**
     * @sice 3.2M1
     */
    public void addQueryStringEntry(StringBuilder builder, String key, String value)
    {
        builder.append(escapeURL(key));
        if (value != null) {
            builder.append('=');
            builder.append(escapeURL(value));
        }
    }

    /**
     * (Re)-cache the secret token used for CSRF protection. A user with edit rights on Main.WebHome must be logged in.
     * This method must be called before {@link #getSecretToken()} is called and after each re-login.
     * 
     * @see #getSecretToken()
     */
    public void recacheSecretToken()
    {
        // the registration form uses secret token
        getDriver().get(getURL("XWiki", "Register", "register"));
        try {
            WebElement tokenInput = getDriver().findElement(By.xpath("//input[@name='form_token']"));
            this.secretToken = tokenInput.getAttribute("value");
        } catch (NoSuchElementException exception) {
            // something is really wrong if this happens
            System.out.println("Warning: Failed to cache anti-CSRF secret token, some tests might fail!");
            exception.printStackTrace();
        }
        // return to the previous page
        getDriver().navigate().back();
    }

    /**
     * Get the secret token used for CSRF protection. Remember to call {@link #recacheSecretToken()} first.
     * 
     * @return anti-CSRF secret token, or empty string if the token was not cached
     * @see #recacheSecretToken()
     */
    public String getSecretToken()
    {
        if (this.secretToken == null) {
            System.out.println("Warning: No cached anti-CSRF token found. "
                + "Make sure to call recacheSecretToken() before getSecretToken(), otherwise this test might fail.");
            return "";
        }
        return this.secretToken;
    }

    /**
     * Encodes a given string so that it may be used as a URL component. Compatable with javascript decodeURIComponent,
     * though more strict than encodeURIComponent: all characters except [a-zA-Z0-9], '.', '-', '*', '_' are converted
     * to hexadecimal, and spaces are substituted by '+'.
     * 
     * @param s
     */
    public String escapeURL(String s)
    {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // should not happen
            throw new RuntimeException(e);
        }
    }

    /**
     * This class represents all cookies stored in the browser. Use with getSession() and setSession()
     */
    public class Session
    {
        private final Set<Cookie> cookies;

        private final String secretToken;

        private Session(final Set<Cookie> cookies, final String secretToken)
        {
            this.cookies = Collections.unmodifiableSet(new HashSet<Cookie>()
            {
                {
                    addAll(cookies);
                }
            });
            this.secretToken = secretToken;
        }

        private Set<Cookie> getCookies()
        {
            return this.cookies;
        }

        private String getSecretToken()
        {
            return this.secretToken;
        }
    }

    public int getTimeout()
    {
        return this.timeout;
    }

    /**
     * @param timeout the number of seconds after which we consider the action to have failed
     */
    public void setTimeout(int timeout)
    {
        this.timeout = timeout;
    }

    /**
     * Forces the passed driver to wait for a {@link #getTimeout()} number of seconds when looking up page elements
     * before declaring that it cannot find them.
     */
    public void setDriverImplicitWait(WebDriver driver)
    {
        driver.manage().timeouts().implicitlyWait(getTimeout(), TimeUnit.SECONDS);
    }

    public boolean isInWYSIWYGEditMode()
    {
        return getDriver()
            .findElements(By.xpath("//div[@id='tmCurrentEditor']//a/strong[contains(text(), 'WYSIWYG')]")).size() > 0;
    }

    public boolean isInWikiEditMode()
    {
        return getDriver().findElements(By.xpath("//div[@id='tmCurrentEditor']//a/strong[contains(text(), 'Wiki')]"))
            .size() > 0;
    }

    public boolean isInViewMode()
    {
        return getDriver().findElements(By.xpath("//div[@id='tmEdit']")).size() > 0;
    }

    public boolean isInInlineEditMode()
    {
        return getDriver().getCurrentUrl().contains("/inline/");
    }

    public boolean isInRightsEditMode()
    {
        return getDriver().getCurrentUrl().contains("editor=rights");
    }

    public boolean isInObjectEditMode()
    {
        return getDriver().getCurrentUrl().contains("editor=object");
    }

    public boolean isInClassEditMode()
    {
        return getDriver().getCurrentUrl().contains("editor=class");
    }

    public boolean isInDeleteMode()
    {
        return getDriver().getCurrentUrl().contains("/delete/");
    }

    public boolean isInRenameMode()
    {
        return getDriver().getCurrentUrl().contains("xpage=rename");
    }

    public boolean isInCreateMode()
    {
        return getDriver().getCurrentUrl().contains("/create/");
    }

    /**
     * Takes a screenshot and puts the generated image in the temporary directory.
     */
    public void takeScreenshot()
    {
        if (!(getDriver().getWrappedDriver() instanceof TakesScreenshot)) {
            return;
        }

        try {
            File scrFile = ((TakesScreenshot) getDriver().getWrappedDriver()).getScreenshotAs(OutputType.FILE);
            File screenshotFile;
            if (SCREENSHOT_DIR != null) {
                File screenshotDir = new File(SCREENSHOT_DIR);
                screenshotDir.mkdirs();
                screenshotFile = new File(screenshotDir, context.getCurrentTestName() + ".png");
            } else {
                screenshotFile =
                    new File(new File(System.getProperty("java.io.tmpdir")), context.getCurrentTestName() + ".png");
            }
            FileUtils.copyFile(scrFile, screenshotFile);
            try {
                throw new Exception("Screenshot for failing test [" + context.getCurrentTestName() + "] saved at ["
                    + screenshotFile.getAbsolutePath() + "]");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.out.println("Failed to take screenshot for failing test [" + context.getCurrentTestName() + "]");
            e.printStackTrace();
        }
    }

    /**
     * Forces the current user to be the Guest user by clearing all coookies.
     */
    public void forceGuestUser()
    {
        setSession(null);
    }

    public void addObject(String space, String page, String className, Object... properties)
    {
        gotoPage(space, page, "objectadd", toQueryParameters(className, null, properties));
    }

    public void addObject(String space, String page, String className, Map<String, ? > properties)
    {
        gotoPage(space, page, "objectadd", toQueryParameters(className, null, properties));
    }

    public void deleteObject(String space, String page, String className, int objectNumber)
    {
        StringBuilder queryString = new StringBuilder();

        queryString.append("classname=");
        queryString.append(escapeURL(className));
        queryString.append('&');
        queryString.append("classid=");
        queryString.append(objectNumber);

        gotoPage(space, page, "objectremove", queryString.toString());
    }

    public void updateObject(String space, String page, String className, int objectNumber, Map<String, ? > properties)
    {
        gotoPage(space, page, "save", toQueryParameters(className, objectNumber, properties));
    }

    public void updateObject(String space, String page, String className, int objectNumber, Object... properties)
    {
        // TODO: would be even quicker using REST
        gotoPage(space, page, "save", toQueryParameters(className, objectNumber, properties));
    }

    public ClassEditPage addClassProperty(String space, String page, String propertyName, String propertyType)
    {
        Map<String, String> props = new HashMap<String, String>();
        props.put("propname", propertyName);
        props.put("proptype", propertyType);

        gotoPage(space, page, "propadd", props);
        return new ClassEditPage();
    }

    public Map<String, ? > toQueryParameters(String className, Integer objectNumber, Object... properties)
    {
        Map<String, Object> queryParameters = new HashMap<String, Object>();

        queryParameters.put("classname", className);

        for (int i = 0; i < properties.length; i += 2) {
            int nextIndex = i + 1;
            queryParameters.put(toQueryParameterKey(className, objectNumber, (String) properties[i]),
                nextIndex < properties.length ? properties[nextIndex] : null);
        }

        return queryParameters;
    }

    public Map<String, ? > toQueryParameters(String className, Integer objectNumber, Map<String, ? > properties)
    {
        Map<String, Object> queryParameters = new HashMap<String, Object>();

        queryParameters.put("classname", className);

        for (Map.Entry<String, ? > entry : properties.entrySet()) {
            queryParameters.put(toQueryParameterKey(className, objectNumber, entry.getKey()), entry.getValue());
        }

        return queryParameters;
    }

    public String toQueryParameterKey(String className, Integer objectNumber, String key)
    {
        StringBuilder keyBuilder = new StringBuilder(className);
        keyBuilder.append('_');
        if (objectNumber != null) {
            keyBuilder.append(objectNumber);
            keyBuilder.append('_');
        }
        keyBuilder.append(key);

        return keyBuilder.toString();
    }

    public WebElement findElementWithoutWaiting(WebDriver driver, By by)
    {
        // Temporarily remove the implicit wait on the driver since we're doing our own waits...
        driver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
        try {
            return driver.findElement(by);
        } finally {
            setDriverImplicitWait(driver);
        }
    }

    public List<WebElement> findElementsWithoutWaiting(WebDriver driver, By by)
    {
        // Temporarily remove the implicit wait on the driver since we're doing our own waits...
        driver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
        try {
            return driver.findElements(by);
        } finally {
            setDriverImplicitWait(driver);
        }
    }

    public WebElement findElementWithoutWaiting(WebDriver driver, WebElement element, By by)
    {
        // Temporarily remove the implicit wait on the driver since we're doing our own waits...
        driver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
        try {
            return element.findElement(by);
        } finally {
            setDriverImplicitWait(driver);
        }
    }

    public List<WebElement> findElementsWithoutWaiting(WebDriver driver, WebElement element, By by)
    {
        // Temporarily remove the implicit wait on the driver since we're doing our own waits...
        driver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS);
        try {
            return element.findElements(by);
        } finally {
            setDriverImplicitWait(driver);
        }
    }

    /**
     * Should be used when the result is supposed to be true (otherwise you'll incur the timeout).
     */
    public boolean hasElement(By by)
    {
        try {
            // Note: make sure to use the original driver since we don't want to generate logs/take screenshots when
            // there's an exception.
            getDriver().getWrappedDriver().findElement(by);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Should be used when the result is supposed to be true (otherwise you'll incur the timeout).
     */
    public boolean hasElement(WebElement element, By by)
    {
        try {
            element.findElement(by);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    public ObjectEditPage editObjects(String space, String page)
    {
        gotoPage(space, page, "edit", "editor=object");
        return new ObjectEditPage();
    }

    public ClassEditPage editClass(String space, String page)
    {
        gotoPage(space, page, "edit", "editor=class");
        return new ClassEditPage();
    }
}
