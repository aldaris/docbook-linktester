/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 ForgeRock, Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 */
package org.forgerock.maven.plugins.utils;

import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

/**
 * In order to prevent HTTPS trust issues this {@link X509TrustManager} will make sure that there is no certificate
 * validation at all when making outgoing HTTPS connections.
 *
 * @author Peter Major
 */
public class TrustAllCertsX509TrustManager implements X509TrustManager {

    /**
     * Since there is no certificate check at all, this returns an empty array.
     *
     * @return An empty array of certs.
     */
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    /**
     * An empty implementation of client cert check.
     *
     * @param certs Peer certificate chain.
     * @param authType Authentication type based on the client certificate.
     */
    public void checkClientTrusted(X509Certificate[] certs, String authType) {
    }

    /**
     * An empty implementation of server cert check.
     *
     * @param certs Peer certificate chain.
     * @param authType The key exhange algorithm used.
     */
    public void checkServerTrusted(X509Certificate[] certs, String authType) {
    }
}