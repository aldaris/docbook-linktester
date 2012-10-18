/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 ForgeRock Inc. All Rights Reserved
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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.forgerock.maven.plugins.utils.MyErrorHandler;
import org.forgerock.maven.plugins.utils.MyNameVerifier;
import org.forgerock.maven.plugins.utils.MyNamespaceContext;
import org.forgerock.maven.plugins.utils.MyX509TrustManager;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Peter Major
 * @goal check
 */
public class LinkTester extends AbstractMojo {

    private static final String DOCBOOK_XSD = "http://docbook.org/xml/5.0/xsd/docbook.xsd";
    private static final String DOCBOOK_NS = "http://docbook.org/ns/docbook";
    private static final String OLINK_ROLE = "http://docbook.org/xlink/role/olink";
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
    /**
     * @parameter default-value="false"
     */
    private boolean validating;
    /**
     * @parameter default-value="false"
     */
    private boolean xIncludeAware;
    /**
     * @parameter default-value="false"
     */
    private boolean skipOlinks;
    /**
     * External links are not checked if set to true
     * @parameter default-value="false"
     */
    private boolean skipUrls;
    /**
     * Build will fail upon error if set to true
     * @parameter default-value="false"
     */
    private boolean failOnError;
    /**
     * Where to write the plugin execution results
     * @parameter
     */
    private String outputFile;
    private FileWriter fileWriter;
    private MultiValueMap failedUrls = new MultiValueMap();
    private MultiValueMap xmlIds = new MultiValueMap();
    private MultiValueMap olinks = new MultiValueMap();
    private Set<String> tested = new HashSet<String>();
    private String currentPath;
    private boolean failure;

    public LinkTester() {
        TrustManager[] trustAllCerts = new TrustManager[]{new MyX509TrustManager()};

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception ex) {
            error("Unable to initialize trustAll SSL", ex);
        }
    }

    @Override()
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (outputFile != null) {
            File file = new File(outputFile);
            if (file.exists()) {
                debug("Deleting existing outputFile: " + outputFile);
                file.delete();
            }
            try {
                file.createNewFile();
                fileWriter = new FileWriter(outputFile);
            } catch (IOException ioe) {
                error("Error while creating output file", ioe);
            }
        }
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(project.getBasedir());
        scanner.setIncludes(includes);
        scanner.setExcludes(excludes);
        scanner.scan();
        String[] files = scanner.getIncludedFiles();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setXIncludeAware(xIncludeAware);

        if (validating) {
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                Schema schema = sf.newSchema(new URL(DOCBOOK_XSD));
                dbf.setSchema(schema);
            } catch (Exception ex) {
                error("Error while constructing schema for validation", ex);
            }
        }
        XPathFactory xpf = XPathFactory.newInstance();
        XPath xpath = xpf.newXPath();
        xpath.setNamespaceContext(new MyNamespaceContext());
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(new MyErrorHandler(this));
            XPathExpression expr = xpath.compile("//@xml:id");
            for (String path : files) {
                setCurrentPath(path);
                try {
                    Document doc = db.parse(new File(path));
                    if (!skipOlinks) {
                        extractXmlIds(expr, doc, path);
                    }
                    NodeList nodes = doc.getElementsByTagNameNS(DOCBOOK_NS, "link");
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Node node = nodes.item(i);
                        NamedNodeMap attrs = node.getAttributes();
                        String url = null;
                        boolean isOlink = false;
                        for (int j = 0; j < attrs.getLength(); j++) {
                            Node attr = attrs.item(j);
                            if (attr.getLocalName().equals("href")) {
                                url = attr.getNodeValue();
                            } else if (attr.getLocalName().equals("role")
                                    && attr.getNodeValue().equalsIgnoreCase(OLINK_ROLE)) {
                                isOlink = true;
                            }
                        }
                        if (url != null) {
                            if (isOlink && !skipOlinks) {
                                olinks.put(path, url);
                            } else if (!isOlink && !skipUrls) {
                                checkUrl(path, url);
                            }
                        }
                    }
                } catch (Exception ex) {
                    error("Error while processing file: " + path + ". Error: " + ex.getMessage());
                }
            }

            if (!skipOlinks) {
                //we can only check olinks after going through all the documents,
                //otherwise we would see false positives, because of the not yet
                //processed files
                for (Map.Entry<String, Collection<String>> entry : (Set<Map.Entry<String, Collection<String>>>) olinks.entrySet()) {
                    for (String val : entry.getValue()) {
                        checkOlink(entry.getKey(), val);
                    }
                }
            }
            if (!failedUrls.isEmpty()) {
                error("The following files had invalid URLs:\n" + failedUrls.toString());
            }
        } catch (Exception ex) {
            throw new MojoFailureException("Unexpected error while tesing links", ex);
        } finally {
            flushReport();
        }
        if (failOnError) {
            if (failure || !failedUrls.isEmpty()) {
                throw new MojoFailureException("One or more error occured during plugin execution");
            }
        }
    }

    private void extractXmlIds(XPathExpression expr, Document doc, String path) throws XPathExpressionException {
        NodeList ids = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        if (ids != null) {
            for (int i = 0; i < ids.getLength(); i++) {
                Node node = ids.item(i);
                File file = new File(path);
                xmlIds.put(file.getParentFile().getName(), node.getNodeValue());
            }
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
            warn(docUrl + ": " + ex.getClass().getName() + " " + ex.getMessage());
            failedUrls.put(path, docUrl);
        }
        tested.add(docUrl);
    }

    private void checkOlink(String path, String olink) {
        String[] parts = olink.split("#");
        if (parts.length != 2) {
            failedUrls.put(path, olink);
            return;
        }
        Collection coll = xmlIds.getCollection(parts[0]);
        if (coll == null || !coll.contains(parts[1])) {
            failedUrls.put(path, olink);
        }
    }

    public String getCurrentPath() {
        return currentPath;
    }

    private void setCurrentPath(String path) {
        currentPath = path;
    }

    public void setFailure() {
        failure = true;
    }

    public final void debug(String line) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(line);
            report("[DEBUG] " + line);
        }
    }

    public final void warn(String line) {
        getLog().warn(line);
        report("[WARNING] " + line);
    }

    public final void error(String line) {
        getLog().error(line);
        report("[ERROR] " + line);
    }

    public final void error(String line, Throwable throwable) {
        getLog().error(line, throwable);
        report("[ERROR] " + line, throwable);
    }

    private void report(String line) {
        try {
            if (fileWriter != null) {
                fileWriter.write(line);
                fileWriter.write("\n");
            }
        } catch (IOException ioe) {
            getLog().error("Error while writing to outputFile: " + ioe.getMessage());
        }
    }

    private void report(String line, Throwable throwable) {
        if (fileWriter != null) {
            report(line);
            throwable.printStackTrace(new PrintWriter(fileWriter));
        }
    }

    private void flushReport() {
        if (fileWriter != null) {
            try {
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException ioe) {
                getLog().error("Error while flushing report: " + ioe.getMessage());
            }
        }
    }
}
