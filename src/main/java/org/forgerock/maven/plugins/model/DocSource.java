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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.maven.plugins.model;

import java.io.File;
import java.util.Arrays;

/**
 * A model object to represent a selection of documentation files.
 *
 * @since 1.3.0
 */
public class DocSource {

    /**
     * The absolute path to the directory where DocBook XML files should be searched for.
     */
    private File directory;
    /**
     * Include patterns for doc files.
     */
    private String[] includes;
    /**
     * Exclude patterns for doc files.
     */
    private String[] excludes;

    public File getDirectory() {
        return directory;
    }

    public String[] getIncludes() {
        return includes;
    }

    public String[] getExcludes() {
        return excludes;
    }

    @Override
    public String toString() {
        return "DocSource{" + "directory=" + directory + ", includes=" + Arrays.toString(includes)
                + ", excludes=" + Arrays.toString(excludes) + '}';
    }
}
