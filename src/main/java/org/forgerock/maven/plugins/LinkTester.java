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
package org.forgerock.maven.plugins;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.forgerock.maven.plugins.model.DocSource;
import org.forgerock.maven.plugins.utils.LoggingErrorHandler;
import org.forgerock.maven.plugins.utils.TrustAllHostnameVerifier;
import org.forgerock.maven.plugins.utils.XmlNamespaceContext;
import org.forgerock.maven.plugins.utils.TrustAllCertsX509TrustManager;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This goal will check the selected DocBook documents for XML validity and will also verify the validity of external
 * links and inter-document olinks that can be found throughout the project.
 */
@Mojo(name = "check")
public class LinkTester extends AbstractMojo {

    private static final String DOCBOOK_XSD = "http://docbook.org/xml/5.0/xsd/docbook.xsd";
    private static final String DOCBOOK_NS = "http://docbook.org/ns/docbook";
    private static final String OLINK_ROLE = "http://docbook.org/xlink/role/olink";
    private static final SSLSocketFactory TRUST_ALL_SOCKET_FACTORY;

    /**
     * The list of {@link DocSource} elements that will be used to locate all the documentation files.
     */
    @Parameter
    private List<DocSource> docSources;
    /**
     * Access to the Maven Project settings.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    protected MavenProject project = new MavenProject();
    /**
     * Whether to validate the XML against the DocBook XML Schema.
     */
    @Parameter(defaultValue = "false")
    private boolean validating;
    /**
     * Whether to resolve xinclude:include tags and inline the referred documents into the processed XML content.
     */
    @Parameter(defaultValue = "false")
    private boolean xIncludeAware;
    /**
     * Whether to skip validation of olinks in the DocBook document.
     */
    @Parameter(defaultValue = "false")
    private boolean skipOlinks;
    /**
     * Whether to skip validation of external URLs in the DocBook document.
     */
    @Parameter(defaultValue = "false")
    private boolean skipUrls;
    /**
     * A list of regular expressions (in valid {@link java.util.regex.Pattern Pattern} format). URLs matching any of
     * these regular expressions will not be validated.
     */
    @Parameter
    private String[] skipUrlPatterns;
    /**
     * Whether to fail the current build if there was any kind of validation error.
     */
    @Parameter(defaultValue = "false")
    private boolean failOnError;
    /**
     * The location of the file where the plugin report will be written.
     */
    @Parameter
    private File outputFile;
    private final List<Pattern> patterns = new ArrayList<Pattern>();
    private final Multimap<String, String> failedUrls = ArrayListMultimap.create();
    private final Multimap<String, String> timedOutUrls = ArrayListMultimap.create();
    private final Multimap<String, String> xmlIds = ArrayListMultimap.create();
    private final Multimap<String, String> olinks = ArrayListMultimap.create();
    private final Set<String> tested = new HashSet<String>();
    private FileWriter fileWriter;
    private boolean failure;
    private String currentPath;

    static {
        TrustManager[] trustAllCerts = new TrustManager[]{new TrustAllCertsX509TrustManager()};

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            TRUST_ALL_SOCKET_FACTORY = sc.getSocketFactory();
        } catch (GeneralSecurityException gse) {
            throw new IllegalStateException("Unable to initialize trustAllCerts SSLSocketFactory", gse);
        }
    }

    private void initializeSkipUrlPatterns() {
        if (skipUrlPatterns != null) {
            for (String pattern : skipUrlPatterns) {
                patterns.add(Pattern.compile(pattern));
            }
        }
    }

    @Override()
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (outputFile != null) {
            if (!outputFile.isAbsolute()) {
                outputFile = new File(project.getBasedir(), outputFile.getPath());
            }
            if (outputFile.exists()) {
                debug("Deleting existing outputFile: " + outputFile);
                outputFile.delete();
            }
            try {
                outputFile.getParentFile().mkdirs();
                outputFile.createNewFile();
                fileWriter = new FileWriter(outputFile);
            } catch (IOException ioe) {
                error("Error while creating output file", ioe);
            }
        }
        initializeSkipUrlPatterns();

        //Initialize XML parsers and XPath expressions
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setExpandEntityReferences(false);
        dbf.setXIncludeAware(xIncludeAware);

        if (validating) {
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                Schema schema = sf.newSchema(new URL(DOCBOOK_XSD));
                dbf.setSchema(schema);
            } catch (MalformedURLException murle) {
                error("Invalid URL provided as schema source", murle);
            } catch (SAXException saxe) {
                error("Parsing error occurred while constructing schema for validation", saxe);
            }
        }
        DocumentBuilder db;
        try {
            db = dbf.newDocumentBuilder();
            db.setErrorHandler(new LoggingErrorHandler(this));
        } catch (ParserConfigurationException pce) {
            throw new MojoExecutionException("Unable to create new DocumentBuilder", pce);
        }

        XPathFactory xpf = XPathFactory.newInstance();
        XPath xpath = xpf.newXPath();
        xpath.setNamespaceContext(new XmlNamespaceContext());
        XPathExpression expr;
        try {
            expr = xpath.compile("//@xml:id");
        } catch (XPathExpressionException xpee) {
            throw new MojoExecutionException("Unable to compile Xpath expression", xpee);
        }

        if (docSources != null) {
            for (DocSource docSource : docSources) {
                processDocSource(docSource, db, expr);
            }
        }

        if (!skipOlinks) {
            //we can only check olinks after going through all the documents, otherwise we would see false
            //positives, because of the not yet processed files
            for (Map.Entry<String, String> entry : olinks.entries()) {
                checkOlink(entry.getKey(), entry.getValue());
            }
        }

        try {
            if (!failedUrls.isEmpty()) {
                error("The following files had invalid URLs:\n" + failedUrls.toString());
            }
            if (!timedOutUrls.isEmpty()) {
                warn("The following files had unavailable URLs (connection or read timed out):\n"
                        + timedOutUrls.toString());
            }
            if (failedUrls.isEmpty() && timedOutUrls.isEmpty() && !failure) {
                //there are no failed URLs and the parser didn't encounter any errors either
                info("DocBook links successfully tested, no errors reported.");
            }
        } finally {
            flushReport();
        }
        if (failOnError) {
            if (failure || !failedUrls.isEmpty()) {
                throw new MojoFailureException("One or more error occurred during plugin execution");
            }
        }
    }

    private void processDocSource(DocSource docSource, DocumentBuilder db, XPathExpression expr) {
        DirectoryScanner scanner = new DirectoryScanner();

        File baseDir = docSource.getDirectory();
        if (baseDir == null) {
            baseDir = project.getBasedir();
        }
        scanner.setBasedir(baseDir);
        scanner.setIncludes(docSource.getIncludes());
        scanner.setExcludes(docSource.getExcludes());
        scanner.scan();

        String[] files = scanner.getIncludedFiles();
        if (getLog().isDebugEnabled()) {
            debug("Scanning " + docSource + " revealed the following doc files:\n" + Arrays.toString(files));
        }
        for (String relativePath : files) {
            currentPath = relativePath;
            try {
                Document doc = db.parse(new File(baseDir, relativePath));
                if (!skipOlinks) {
                    extractXmlIds(expr, doc, relativePath);
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
                            olinks.put(relativePath, url);
                        } else if (!isOlink && !skipUrls) {
                            checkUrl(relativePath, url);
                        }
                    }
                }
            } catch (Exception ex) {
                error("Error while processing file: " + relativePath + ". Error: " + ex.getMessage(), ex);
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
        if (shouldSkipUrl(docUrl)) {
            debug("Skipping " + docUrl + " since it matches a skipUrlPattern");
            return;
        }
        if (tested.contains(docUrl)) {
            if (failedUrls.containsValue(docUrl)) {
                failedUrls.put(path, docUrl);
            }
            return;
        }
        debug("Checking " + docUrl + " from file: " + path);
        try {
            URL url = new URL(docUrl);
            URLConnection urlConn = url.openConnection();
            if (urlConn instanceof HttpURLConnection) {
                HttpURLConnection conn = (HttpURLConnection) urlConn;
                if (conn instanceof HttpsURLConnection) {
                    HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
                    httpsConn.setHostnameVerifier(new TrustAllHostnameVerifier());
                    httpsConn.setSSLSocketFactory(TRUST_ALL_SOCKET_FACTORY);
                }

                conn.setConnectTimeout(1000);
                //if we don't get anything back within 15 seconds it is safe to assume that something is really wrong
                //with that site..
                conn.setReadTimeout(15000);
                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    warn(docUrl + ": received unexpected response code: " + responseCode);
                    failedUrls.put(path, docUrl);
                }
            }
        } catch (SocketTimeoutException ste) {
            warn(docUrl + ": " + ste.toString());
            timedOutUrls.put(path, docUrl);
        } catch (Exception ex) {
            warn(docUrl + ": " + ex.toString());
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

        if (!xmlIds.containsEntry(parts[0], parts[1])) {
            failedUrls.put(path, olink);
        }
    }

    private boolean shouldSkipUrl(String docUrl) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(docUrl).matches()) {
                return true;
            }
        }
        //if the skipUrlPattern list is empty or there was no match, then we should check the URL
        return false;
    }

    /**
     * Returns the path of the currently parsed XML file.
     *
     * @return The relative path for the currently parsed XML file.
     */
    public String getCurrentPath() {
        return currentPath;
    }

    /**
     * Sets the failure status and logs the reported error message.
     *
     * @param errorMessage The error message to log for this failure.
     */
    public final void fail(String errorMessage) {
        failure = true;
        error(errorMessage);
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

    public final void info(String line) {
        getLog().info(line);
        report("[INFO] " + line);
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
