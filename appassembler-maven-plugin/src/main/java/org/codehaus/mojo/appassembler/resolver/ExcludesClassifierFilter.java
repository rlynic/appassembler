/*
  This file created at 2024/10/29.

  Copyright (c) 2002-2024 crisis, Inc. All rights reserved.
 */
package org.codehaus.mojo.appassembler.resolver;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;

import java.util.Iterator;
import java.util.List;

/**
 * <code>{@link ExcludesClassifierFilter}</code>
 *
 * @author crisis
 */
public class ExcludesClassifierFilter
        implements ArtifactFilter {
    private final List patterns;

    public ExcludesClassifierFilter(List patterns) {
        this.patterns = patterns;
    }

    public boolean include(Artifact artifact) {
        boolean matched = true;
        if (null == artifact.getClassifier() || "".equals(artifact.getClassifier())) {
            return matched;
        }
        for (Iterator i = patterns.iterator(); i.hasNext() & matched; ) {
            if (artifact.getClassifier().equals(i.next())) {
                matched = false;
            }
        }
        return matched;
    }
}
