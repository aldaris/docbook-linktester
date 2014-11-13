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
 * Copyright 2011-2014 ForgeRock AS.
 */
package org.forgerock.maven.plugins.utils;

import java.util.Iterator;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

/**
 * A simple {@link NamespaceContext} implementation to map the "xml" prefix to the XML Namespace URI.
 */
public class XmlNamespaceContext implements NamespaceContext {

    public String getNamespaceURI(String prefix) {
        if (prefix == null) {
            throw new NullPointerException("Null prefix");
        } else if (prefix.equals("xml")) {
            return XMLConstants.XML_NS_URI;
        }

        return XMLConstants.NULL_NS_URI;
    }

    public String getPrefix(String namespaceURI) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Iterator getPrefixes(String namespaceURI) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
