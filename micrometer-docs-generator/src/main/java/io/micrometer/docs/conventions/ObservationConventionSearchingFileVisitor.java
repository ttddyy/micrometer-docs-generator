/**
 * Copyright 2022 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.docs.conventions;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.ObservationConvention;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaSource;

class ObservationConventionSearchingFileVisitor extends SimpleFileVisitor<Path> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ObservationConventionSearchingFileVisitor.class);

    private final Pattern pattern;

    private final Collection<ObservationConventionEntry> observationConventionEntries;

    ObservationConventionSearchingFileVisitor(Pattern pattern, Collection<ObservationConventionEntry> observationConventionEntries) {
        this.pattern = pattern;
        this.observationConventionEntries = observationConventionEntries;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
        if (!pattern.matcher(path.toString()).matches()) {
            return FileVisitResult.CONTINUE;
        }
        else if (!path.toString().endsWith(".java")) {
            return FileVisitResult.CONTINUE;
        }

        logger.debug("Parsing [" + path + "]");
        JavaSource<?> javaSource = Roaster.parse(JavaSource.class, path.toFile());
        if (!javaSource.isClass()) {
            return FileVisitResult.CONTINUE;
        }
        JavaClassSource classSource = (JavaClassSource) javaSource;
        String classCanonicalName = classSource.getCanonicalName();
        Pattern classPattern = Pattern.compile("^.*ObservationConvention<(.*)>$");
        for (String anInterface : classSource.getInterfaces()) {
            if (isGlobalObservationConvention(anInterface)) {
                this.observationConventionEntries.add(new ObservationConventionEntry(classCanonicalName, ObservationConventionEntry.Type.GLOBAL, contextClassName(classPattern, anInterface)));
            }
            else if (isLocalObservationConvention(anInterface)) {
                this.observationConventionEntries.add(new ObservationConventionEntry(classCanonicalName, ObservationConventionEntry.Type.LOCAL, contextClassName(classPattern, anInterface)));
            }
        }
        return FileVisitResult.CONTINUE;
    }

    private String contextClassName(Pattern classPattern, String anInterface) {
        Matcher matcher = classPattern.matcher(anInterface);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        if (!anInterface.contains("<") && !anInterface.contains(">")) {
            return "n/a";
        }
        return "";
    }

    private boolean isLocalObservationConvention(String interf) {
        return interf.contains(ObservationConvention.class.getSimpleName()) || interf.contains(ObservationConvention.class.getCanonicalName());
    }

    private boolean isGlobalObservationConvention(String interf) {
        return interf.contains(GlobalObservationConvention.class.getSimpleName()) || interf.contains(GlobalObservationConvention.class.getCanonicalName());
    }

}
