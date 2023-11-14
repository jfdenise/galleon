/*
 * Copyright 2016-2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.galleon.diff;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.galleon.BaseErrors;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.util.CollectionUtils;
import org.jboss.galleon.util.HashUtils;

/**
 * Represents a filesystem entry, i.e. a file or directory.
 *
 * @author Alexey Loubyansky
 */
public class FsEntry {

    private static final char NONE = ' ';
    private static final char ADDED = '+';
    private static final char REMOVED = '-';
    private static final char MODIFIED = 'C';
    private static final char SUPPRESSED = 'S';

    final int depth;
    final FsEntry parent;
    final Path p;
    final String name;
    final boolean dir;
    private byte[] hash;
    private String relativePath;
    private char diffStatus = NONE;

    private Map<String, FsEntry> children = Collections.emptyMap();

    public FsEntry(FsEntry parent, Path p) {
        this.parent = parent;
        this.p = p;
        this.name = p.getFileName().toString();
        this.dir = Files.isDirectory(p);
        if(parent != null) {
            depth = parent.depth + 1;
            parent.addChild(this);
        } else {
            depth = 0;
        }
    }

    public FsEntry(FsEntry parent, String name, byte[] hash) {
        this.parent = parent;
        this.name = name;
        this.hash = hash;
        this.dir = false;
        this.p = null;
        if(parent != null) {
            depth = parent.depth + 1;
            parent.addChild(this);
        } else {
            depth = 0;
        }
    }

    private void addChild(FsEntry child) {
        children = CollectionUtils.put(children, child.name, child);
    }

    public String getName() {
        return name;
    }

    public String getRelativePath() {
        if (relativePath == null) {
            final StringBuilder buf = new StringBuilder();
            collectPath(this, buf);
            if(dir) {
                buf.append('/');
            }
            relativePath = buf.toString();
        }
        return relativePath;
    }

    public Path getPath() {
        return p;
    }

    private static void collectPath(FsEntry entry, StringBuilder buf) {
        if(entry.parent == null) {
            return;
        }
        collectPath(entry.parent, buf);
        if(buf.length() > 0) {
            buf.append('/');
        }
        buf.append(entry.getName());
    }

    public boolean isDir() {
        return dir;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public Collection<FsEntry> getChildren() {
        return children.values();
    }

    public Map<String, FsEntry> cloneChildren() {
        return children.isEmpty() ? Collections.emptyMap() : new HashMap<>(children);
    }

    public FsEntry getChild(String name) {
        return children.get(name);
    }

    public boolean hasChild(String name) {
        return children.containsKey(name);
    }

    public boolean includesPath(String relativePath) {
        if(relativePath.charAt(0) == '/') {
            relativePath = relativePath.substring(1);
        }
        return includesPath(relativePath.split("/"));
    }

    public boolean includesPath(String... pathElements) {
        if(children.isEmpty()) {
            return false;
        }
        FsEntry e = this;
        for(int i = 0; i < pathElements.length; ++i) {
            e = e.children.get(pathElements[i]);
            if(e == null) {
                return false;
            }
        }
        return true;
    }

    public byte[] getHash() throws ProvisioningException {
        if(hash == null) {
            try {
                hash = HashUtils.hashPath(p);
            } catch (IOException e) {
                throw new ProvisioningException(BaseErrors.hashCalculation(p));
            }
        }
        return hash;
    }

    public boolean isDiffStatusAdded() {
        return diffStatus == ADDED;
    }

    public boolean isDiffStatusRemoved() {
        return diffStatus == REMOVED;
    }

    public boolean isDiffStatusModified() {
        return diffStatus == MODIFIED;
    }

    boolean isDiffStatusSuppressed() {
        return diffStatus == SUPPRESSED;
    }

    void diffAdded() {
        diffStatus = ADDED;
        if(!children.isEmpty()) {
            for(FsEntry child : children.values()) {
                child.diffAdded();
            }
        }
    }

    void diffModified() {
        diffStatus = MODIFIED;
    }

    void diffRemoved() {
        diffStatus = REMOVED;
        if(!children.isEmpty()) {
            for(FsEntry child : children.values()) {
                child.diffRemoved();
            }
        }
    }

    void diffSuppress() {
        diffStatus = SUPPRESSED;
        if(!children.isEmpty()) {
            for(FsEntry child : children.values()) {
                child.diffSuppress();
            }
        }
    }

    public void dumpAsTree(PrintStream out) throws IOException {
        dumpAsTree(children.isEmpty() ? Collections.emptyList() : new ArrayList<>(), out);
    }

    private void dumpAsTree(List<Boolean> dirs, PrintStream out) throws IOException {
        if(!dirs.isEmpty()) {
            Boolean dir;
            int i = 0;
            while(i < dirs.size() - 1) {
                dir = dirs.get(i++);
                out.print(dir ? "| " : "  ");
            }
            dir = dirs.get(i);
            out.print(dir ? "|-" : "`-");
        }
        out.print(name);
        if(dir) {
            out.print('/');
        }
        if(diffStatus != NONE) {
            out.print('(');
            out.print(diffStatus);
            out.print(')');
        }
        out.println();

        if(!children.isEmpty()) {
            final String[] names = children.keySet().toArray(new String[children.size()]);
            Arrays.sort(names);
            final int dirsI = dirs.size();
            dirs.add(false);
            int i = 0;
            while(i < names.length) {
                final String name = names[i++];
                dirs.set(dirsI, i < names.length);
                children.get(name).dumpAsTree(dirs, out);
            }
            dirs.remove(dirsI);
        }
    }

    @Override
    public String toString() {
        if(diffStatus == NONE) {
            return getRelativePath();
        }
        return new StringBuilder().append(getRelativePath()).append('(').append(diffStatus).append(')').toString();
    }
}
