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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
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
    private MultiValueMap failedUrls = new MultiValueMap();
    private MultiValueMap xmlIds = new MultiValueMap();
    private MultiValueMap olinks = new MultiValueMap();
    private Set<String> tested = new HashSet<String>();

    public LinkTester() {
        TrustManager[] trustAllCerts = new TrustManager[]{new MyX509TrustManager()};

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception ex) {
            getLog().error("Unable to initialize trustAll SSL", ex);
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
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setXIncludeAware(true);

        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            Schema schema = sf.newSchema(new URL("http://docbook.org/xml/5.0/xsd/docbook.xsd"));
            dbf.setSchema(schema);
        } catch (Exception ex) {
            getLog().error("Error while constructing schema for validation", ex);
        }
        XPathFactory xpf = XPathFactory.newInstance();
        XPath xpath = xpf.newXPath();
        xpath.setNamespaceContext(new MyNamespaceContext());
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            XPathExpression expr = xpath.compile("//@xml:id");
            for (String path : files) {
                try {
                    Document doc = db.parse(new File(path));
                    extractXmlIds(expr, doc, path);
                    NodeList nodes = doc.getElementsByTagNameNS("http://docbook.org/ns/docbook", "link");
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
                                    && attr.getNodeValue().equalsIgnoreCase("http://docbook.org/xlink/role/olink")) {
                                isOlink = true;
                            }
                        }
                        if (url != null) {
                            if (isOlink) {
                                olinks.put(path, url);
                            } else {
                                checkUrl(path, url);
                            }
                        }
                    }
                } catch (Exception ex) {
                    getLog().error("Error while processing file: " + path + ". Error: " + ex.getMessage());
                }
            }
            //we can only check olinks after going through all the documents,
            //otherwise we would see false positives, because of the not yet
            //processed files
            for (Map.Entry<String, Collection<String>> entry : (Set<Map.Entry<String, Collection<String>>>) olinks.entrySet()) {
                for (String val : entry.getValue()) {
                    checkOlink(entry.getKey(), val);
                }
            }
            if (!failedUrls.isEmpty()) {
                getLog().error("The following files had invalid URLs:\n" + failedUrls.toString());
            }
        } catch (Exception ex) {
            throw new MojoFailureException("Unexpected error while tesing links", ex);
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
            getLog().warn(docUrl + ": " + ex.getClass().getName() + " " + ex.getMessage());
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
}
