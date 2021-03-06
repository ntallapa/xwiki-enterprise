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
package org.xwiki.test.po.invitation;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.xwiki.test.po.platform.BasePage;

/**
 * Represents the actions possible by guests on the invitation application.
 *
 * @version $Id$
 * @since 3.2M3
 */
public class InvitationGuestActionsPage extends BasePage
{
    public static enum Action
    {
        // Accept is null because the user is forwarded to RegistrationPage, decline is null because it includes the name
        // of the sender.
        ACCEPT(null),
        DECLINE(null),
        REPORT("Note to the administrator who investigates this report (optional)");

        public final String label;

        private Action(String label)
        {
            this.label = label;
        }
    }

    private final InvitationActionConfirmationElement confirm;

    public InvitationGuestActionsPage(String messageContent, Action action)
    {
        confirm = new InvitationActionConfirmationElement();
        int start = messageContent.indexOf(getUtil().getURL("Invitation",
                                                            "InvitationGuestActions",
                                                            "view",
                                                            "doAction_" + action.toString().toLowerCase()));
        int end = messageContent.indexOf("\"", start);
        getDriver().get(messageContent.substring(start, end).replaceAll("&amp;", "&"));
        // Make sure the right messages is displayed otherwise we can't continue.
        if(action.label != null && !confirm.getLabel().equalsIgnoreCase(action.label)) {
            throw new WebDriverException("Not on correct page, expecting memo label to say \"" + action.label + "\"");
        }
    }

    // Constructor for when the page is manually accessed.
    public InvitationGuestActionsPage()
    {
        confirm = new InvitationActionConfirmationElement();
    }

    /** This will fail if the action is accept because the user is redirected to a RegistrationPage. */
    public void setMemo(String memo)
    {
        confirm.setMemo(memo);
    }

    public String getMessage()
    {
        List<WebElement> elements =
            getUtil().findElementsWithoutWaiting(getDriver(), By.id("invitation-action-message"));
        if (elements.size() > 0) {
            return elements.get(0).getText();
        }
        // Returning null would lead to NPE when calling equals and we don't get the friendly test failure message.
        return "";
    }

    /** This will fail if the action is accept because the user is redirected to a RegistrationPage. */
    /** Outputs the message given after clicking the confirm button. */
    public String confirm()
    {
        confirm.confirm();
        return getDriver().findElement(By.id("invitation-action-message")).getText();
    }
}
