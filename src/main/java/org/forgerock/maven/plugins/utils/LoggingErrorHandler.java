/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2011-2015 ForgeRock AS.
 */
package org.forgerock.maven.plugins.utils;

import org.forgerock.maven.plugins.LinkTester;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * A custom XML parser error handler that makes sure that warnings and errors are reported in the Maven output. In case
 * the parser runs into an error or a fatal error, this handler will cause the plugin to fail (if failOnError is set to
 * <code>true</code>).
 */
public class LoggingErrorHandler implements ErrorHandler {

    private final LinkTester linkTester;

    public LoggingErrorHandler(LinkTester linkTester) {
        this.linkTester = linkTester;
    }

    @Override
    public void warning(SAXParseException saxpe) throws SAXException {
        linkTester.warn("Warning while processing: " + linkTester.getCurrentPath() + "\n" + saxpe.getMessage());
    }

    @Override
    public void error(SAXParseException saxpe) throws SAXException {
        linkTester.fail("Error while processing: " + linkTester.getCurrentPath() + "\n" + saxpe.getMessage());
    }

    @Override
    public void fatalError(SAXParseException saxpe) throws SAXException {
        linkTester.fail("Fatal error while processing: " + linkTester.getCurrentPath() + "\n" + saxpe.getMessage());
    }
}
