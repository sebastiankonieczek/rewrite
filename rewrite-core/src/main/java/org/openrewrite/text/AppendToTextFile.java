/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.text;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = true)
public class AppendToTextFile extends ScanningRecipe<AtomicBoolean> {
    @Option(displayName = "Relative File Name",
            description = "File name, using a relative path. If a non-plaintext file already exists at this location, then this recipe will do nothing.",
            example = "foo/bar/baz.txt")
    String relativeFileName;

    @Option(displayName = "Content",
            description = "Multiline text content to be appended to the file.",
            example = "Some text.")
    String content;

    @Option(displayName = "Preamble",
            description = "If a new file is created, this content will be included at the beginning.",
            example = "# File generated by OpenRewrite #",
            required = false)
    @Nullable String preamble;

    @Option(displayName = "Append newline",
            description = "Print a newline automatically after the content (and preamble). Default true.",
            required = false)
    @Nullable Boolean appendNewline;

    @Option(displayName = "Existing file strategy",
            description = "Determines behavior if a file exists at this location prior to Rewrite execution.\n\n"
                          + "- `continue`: append new content to existing file contents. If existing file is not plaintext, recipe does nothing.\n"
                          + "- `replace`: remove existing content from file.\n"
                          + "- `leave`: *(default)* do nothing. Existing file is fully preserved.\n\n"
                          + "Note: this only affects the first interaction with the specified file per Rewrite execution.\n"
                          + "Subsequent instances of this recipe in the same Rewrite execution will always append.",
            valid = {"continue", "replace", "leave"},
            required = false)
    @Nullable Strategy existingFileStrategy;
    public enum Strategy { CONTINUE, REPLACE, LEAVE }

    @Override
    public String getDisplayName() {
        return "Append to text file";
    }

    @Override
    public String getDescription() {
        return "Appends or replaces content of an existing plain text file, or creates a new one if it doesn't already exist.";
    }

    @Override
    public int maxCycles() {
        return 1;
    }

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean fileExists) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                SourceFile sourceFile = (SourceFile) requireNonNull(tree);
                if (!fileExists.get() && sourceFile.getSourcePath().toString().equals(Paths.get(relativeFileName).toString())) {
                    fileExists.set(true);
                }
                return sourceFile;
            }
        };
    }

    @Override
    public Collection<PlainText> generate(AtomicBoolean fileExists, Collection<SourceFile> generatedInThisCycle, ExecutionContext ctx) {
        String maybeNewline = !Boolean.FALSE.equals(appendNewline) ? "\n" : "";
        String content = this.content + maybeNewline;
        String preamble = this.preamble != null ? this.preamble + maybeNewline : "";

        boolean exists = fileExists.get();
        if(!exists) {
            for (SourceFile generated : generatedInThisCycle) {
                if(generated.getSourcePath().toString().equals(Paths.get(relativeFileName).toString())) {
                    exists = true;
                    break;
                }
            }
        }

        return exists ?
                Collections.emptyList() :
                Collections.singletonList(PlainText.builder()
                        .text(preamble + content)
                        .sourcePath(Paths.get(relativeFileName))
                        .build());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean fileExists) {
        return Preconditions.check(fileExists.get(), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                SourceFile sourceFile = (SourceFile) requireNonNull(tree);
                if (sourceFile.getSourcePath().toString().equals(Paths.get(relativeFileName).toString())) {
                    String maybeNewline = !Boolean.FALSE.equals(appendNewline) ? "\n" : "";
                    String content = AppendToTextFile.this.content + maybeNewline;
                    String preamble = AppendToTextFile.this.preamble != null ? AppendToTextFile.this.preamble + maybeNewline : "";

                    PlainText existingPlainText = (PlainText) sourceFile;
                    switch (existingFileStrategy != null ? existingFileStrategy : Strategy.LEAVE) {
                        case CONTINUE:
                            if(!maybeNewline.isEmpty() && !existingPlainText.getText().endsWith(maybeNewline)) {
                                content = maybeNewline + content;
                            }
                            return existingPlainText.withText(existingPlainText.getText() + content);
                        case REPLACE:
                            return existingPlainText.withText(preamble + content);
                    }
                }
                return sourceFile;
            }
        });
    }

}
