/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 ForgeRock AS. All Rights Reserved
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
package org.forgerock.maven.plugins;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;

/**
 *
 * @author Peter Major
 * @goal check
 */
public class LinkTester extends AbstractMojo {

    private static final Pattern pattern = Pattern.compile("<link\\s+xlink:href=[\"']([^\"']*)[\"']\\s*>", Pattern.DOTALL);
    /**
     * The base directory
     *
     * @parameter expression="${basedir}"
     * @required
     * @readonly
     */
    private File basedir;
    /**
     * Included files for search
     * @parameter
     */
    private String[] includes;
    /**
     * Excluded files from search
     * @parameter
     */
    private String[] excludes;
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project = new MavenProject();
    private MultiMap failedUrls = new MultiValueMap();
    private Set<String> tested = new HashSet<String>();

    public LinkTester() {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {

                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception ex) {
            getLog().error("Unable to initialize trustAll SSL");
        }
    }

    @Override()
    public void execute() throws MojoExecutionException, MojoFailureException {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(project.getBasedir());
        scanner.setIncludes(includes);
        scanner.setExcludes(excludes);
        scanner.scan();
        String[] files = scanner.getIncludedFiles();
        for (String path : files) {
            try {
                String content = FileUtils.fileRead(path);
                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    checkUrl(path, matcher.group(1));
                }
            } catch (IOException ioe) {
                getLog().error("IO error while reading resource file: " + path, ioe);
            }
        }
        if (!failedUrls.isEmpty()) {
            getLog().error("The following files had invalid URLs:\n" + failedUrls.toString());
        }
    }

    private void checkUrl(String path, String docUrl) {
        if (tested.contains(docUrl)) {
            if (failedUrls.containsValue(docUrl)) {
                failedUrls.put(path, docUrl);
            }
            return;
        }
        try {
            URL url = new URL(docUrl);
            URLConnection urlConn = url.openConnection();
            if (urlConn instanceof HttpURLConnection) {
                HttpURLConnection conn = (HttpURLConnection) urlConn;
                if (conn instanceof HttpsURLConnection) {
                    HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
                    httpsConn.setHostnameVerifier(new MyNameVerifier());
                }

                conn.setConnectTimeout(1000);
                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    failedUrls.put(path, docUrl);
                }
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            failedUrls.put(path, docUrl);
        }
    }

    private static class MyNameVerifier implements HostnameVerifier {

        public boolean verify(String string, SSLSession ssls) {
            return true;
        }
    }
}
