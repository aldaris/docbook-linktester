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
     </configuration>
    </plugin>

This will bind the plugin invocation to the pre-site phase.

## Execution

By setting the execution in the pom.xml the plugin will be invoked during the build, but if you only want to execute the plugin only every now and then, you can use the following command:

    mvn org.forgerock.maven.plugins:linktester-maven-plugin:1.0.0-SNAPSHOT:check

## License

Everything in this repo is licensed under the ForgeRock CDDL license: http://forgerock.org/license/CDDLv1.0.html
