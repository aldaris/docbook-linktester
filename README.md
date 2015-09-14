# DocBook link tester Maven plugin

This is a simple Maven plugin allowing you to validate external (and internal) links within your DocBook based documentation. The plugin will look for &lt;link&gt; and &lt;olink&gt; elements and will handle them accordingly:

* For &lt;olink&gt; elements or &lt;link&gt; elements with olink `role` (in the format of targetdoc#targetptr), the plugin will first look up all the xml:id attributes throughout the document. If the targetdoc exists and has the references targetptr, then the olink was valid.
* If it's a regular link, an HttpURLConnection will be opened, and if the response code is less than 400, the link is considered valid.

Since version 2.0.0 the plugin also validates olinks stored in &lt;olink&gt; XML elements. The targetdoc and targetptr attributes must be present for such elements for the validation to work correctly.

## Sample Configuration

In the pom.xml file you need to add the following section to the build-plugins list:

```
<plugin>
 <groupId>org.forgerock.maven.plugins</groupId>
 <artifactId>linktester-maven-plugin</artifactId>
 <version>2.0.0-SNAPSHOT</version>
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
  <docSources>
   <docSource>
    <directory>${basedir}/src/main/docbkx</directory>
    <includes>
     <include>**/index.xml</include>
    </includes>
    <excludes>
     <exclude>**/not-this-one.xml</exclude>
    </excludes>
   </docSource>
  </docSources>
  <validating>false</validating>
  <xIncludeAware>true</xIncludeAware>
  <failOnError>false</failOnError>
  <skipUrls>false</skipUrls>
  <skipOlinks>false</skipOlinks>
  <skipUrlPatterns>
   <skipUrlPattern>^https://bugster.forgerock.org/jira/browse/OPEN(AM|ICF|IDM|IG|DJ)-[0-9]{1,4}$</skipUrlPattern>
  </skipUrlPatterns>
  <outputFile>linktester.err</outputFile>
 </configuration>
</plugin>
```

This will bind the plugin invocation to the pre-site phase. About the configuration options:

* docSource(s) - A set of files determined by a base directory and a set of include and exclude patterns.
 * directory - Directory where the DocBook XML files can be found. Default directory is the project's `${basedir}`.
 * include(s) - The include patterns for doc files under `directory`. When `xIncludeAware` is used only include the books.
 * exclude(s) - The exclude patterns for doc files under `directory`. When `xIncludeAware` is not used, it is recommended to exclude the books (and/or disable validation).
* validating - The XML files will be validated against the DocBook XML Schema. NOTE: XML validation doesn't appear to like xinclude tags, so either use xIncludeAware, or exclude books when using this option (in case you have a book XML that xincludes chapters).
* xIncludeAware - When enabled the XML parser will resolve the xinclude:include tags and will inline them into XML. This option can come in handy when your books refer to generated chapters, as this will make sure the internal olink database has the correct targetdoc value. If this is enabled, then most likely you only want to include the book XMLs in `docSources` (i.e. exclude the individual chapters).
* failOnError - When enabled, any XML schema validation failure or invalid olink/url in document will result in a failed build.
* skipUrls - When enabled, URLs throughout the document are not checked.
* skipOlinks - When enabled, olinks throughout the document are not checked.
* skipUrlPatterns - This list expects a set of valid [Patterns](http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html). URLs matching any of the defined patterns will be excluded from validation. Useful for release notes, with bunch of similarly structured URLs (especially if it's generated).
* outputFile - The validation results will be also logged to this file. (It will override the file on subsequent runs.)

## Execution

By setting the execution in the pom.xml the plugin will be invoked during the build, but if you only want to execute the plugin every now and then, you can use the following command:

```
mvn org.forgerock.maven.plugins:linktester-maven-plugin:2.0.0-SNAPSHOT:check
```

To check out the plugin's help, run:

```
mvn org.forgerock.maven.plugins:linktester-maven-plugin:2.0.0-SNAPSHOT:help
```

## License

Everything in this repo is licensed under the ForgeRock CDDL license: http://forgerock.org/license/CDDLv1.0.html
