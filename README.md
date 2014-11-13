# DocBook link tester Maven plugin

This is a simple Maven plugin allowing you to do some sanity checks on your documentation links. Basically the plugin will look for &lt;link&gt; tags, and check whether they are olinks or not.

* If it's an olink and it's in the format of targetdoc#targetptr, then the plugin will look up all the xml:id attributes throughout the document, if the referred id belongs to the targetdoc, then the check was successful.
* If it's a regular link, an HttpURLConnection will be opened, and if the response code is higher or equals to 400, the link is considered invalid

## Sample Configuration

In the pom.xml file you need to add the following section to the build-plugins list:

    <plugin>
     <groupId>org.forgerock.maven.plugins</groupId>
     <artifactId>linktester-maven-plugin</artifactId>
     <version>1.3.0-SNAPSHOT</version>
     <inherited>false</inherited>
     <executions>
      <execution>
       <goals>
        <goal>check</goal>
       </goals>
       <phase>pre-site</phase>
      </execution>
     </executions>
     <configuration>
      <directory>${basedir}/src/main/docbkx</directory>
      <includes>
       <include>**/index.xml</include>
      </includes>
      <excludes>
       <exclude>**/not-this-one.xml</exclude>
      </excludes>
      <validating>false</validating>
      <xIncludeAware>false</xIncludeAware>
      <failOnError>false</failOnError>
      <skipUrls>false</skipUrls>
      <skipOlinks>false</skipOlinks>
      <skipUrlPatterns>
       <skipUrlPattern>^https://bugster.forgerock.org/jira/browse/OPEN(AM|ICF|IDM|IG|DJ)-[0-9]{1,4}$</skipUrlPattern>
      </skipUrlPatterns>
      <outputFile>linktester.err</outputFile>
     </configuration>
    </plugin>

This will bind the plugin invocation to the pre-site phase. About the configuration options:

* directory - Directory where the DocBook XML files are found. Default directory is the project's `${basedir}`.
* validating - The XML files will be validated against the DocBook XML Schema. NOTE: XML validation doesn't appear to like xinclude tags, so either use xIncludeAware, or exclude books when using this option (in case you have a book XML that xincludes chapters).
* xIncludeAware - When enabled the XML parser will resolve the xinclude:include tags and will inline them into XML. This option can come in handy when your books refer to generated chapters, as this will make sure the internal olink database has the correct targetdoc value. If this is enabled, then most likely you only want to include the book XMLs and not the chapters.
* failOnError - When enabled, any XML schema validation failure or invalid olink/url in document will result in a failed build
* skipUrls - When enabled, URLs throughout the document are not checked
* skipOlinks - When enabled, olinks throughout the document are not checked
* skipUrlPatterns - This list expects a set of valid [Patterns](http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html), and basically if the external URL in question is matched, then that URL won't be tested. Useful for release notes, with bunch of similarly structured URLs (especially if it's generated)
* outputFile - The validation results will be also logged to this file. (It will override the file on subsequent runs.)
* include(s) - When xIncludeAware is used only include the books
* exclude(s) - When xIncludeAware is not used, it is recommended to exclude the books (and/or disable validation)

## Execution

By setting the execution in the pom.xml the plugin will be invoked during the build, but if you only want to execute the plugin every now and then, you can use the following command:

    mvn org.forgerock.maven.plugins:linktester-maven-plugin:1.3.0-SNAPSHOT:check

## License

Everything in this repo is licensed under the ForgeRock CDDL license: http://forgerock.org/license/CDDLv1.0.html
