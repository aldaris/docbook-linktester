# DocBook link tester Maven plugin

This is a simple Maven plugin allowing you to do some sanity checks on your documentation links. Basically the plugin will look for &lt;link&gt; tags, and check whether they are olinks or not.

* If it's an olink and it's in the format of targetdoc#targetptr, then the plugin will look up all the xml:id attributes throughout the document, if the referred id belongs to the targetdoc, then the check was successful.
* If it's a regular link, an HttpURLConnection will be opened, and if the response code is higher or equals to 400, the link is considered invalid

## Sample Configuration

In the pom.xml file you need to add the following section to the build-plugins list:

    <plugin>
     <groupId>org.forgerock.maven.plugins</groupId>
     <artifactId>linktester-maven-plugin</artifactId>
     <version>1.0.0-SNAPSHOT</version>
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
      <includes>
       <include>**/OpenAM-*.xml</include>
      </includes>
      <excludes>
       <exclude>**/OpenAM-random.xml</exclude>
      </excludes>
      <validating>false</validating>
      <xIncludeAware>false</xIncludeAware>
      <failOnError>false</failOnError>
      <skipUrls>false</skipUrls>
      <skipOlinks>false</skipOlinks>
      <outputFile>linktester.err</outputFile>
     </configuration>
    </plugin>

This will bind the plugin invocation to the pre-site phase. About the configuration options:

* validating - The XML files will be validated against the DocBook XML Schema. NOTE: XML validation doesn't appear to like xinclude tags, so either use xIncludeAware, or exclude books when using this option.
* xIncludeAware - When enabled the XML parser will resolve the xinclude:include tags and will inline them into XML. This option can come in handy when your books refer to generated chapters, as this will make sure the internal olink database has the correct targetdoc value.
* failOnError - When enabled, any XML schema validation failure or invalid olink/url in document will result in a failed build
* skipUrls - When enabled, URLs throughout the document are not checked
* skipOlinks - When enabled, olinks throughout the document are not checked
* outputFile - The validation results will be also logged to this file. (It will override the file on subsequent runs.)
* include(s) - When xIncludeAware is used only include the books
* exclude(s) - When xIncludeAware is not used, it is recommended to exclude the books (or disable validation)

## Execution

By setting the execution in the pom.xml the plugin will be invoked during the build, but if you only want to execute the plugin only every now and then, you can use the following command:

    mvn org.forgerock.maven.plugins:linktester-maven-plugin:1.0.0-SNAPSHOT:check

## License

Everything in this repo is licensed under the ForgeRock CDDL license: http://forgerock.org/license/CDDLv1.0.html
