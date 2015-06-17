package aQute.bnd.make.coverage;

import static aQute.bnd.make.coverage.Coverage.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import aQute.lib.osgi.*;
import aQute.lib.osgi.Clazz.*;
import aQute.lib.tag.*;

/**
 * Creates an XML Coverage report. This class can be used as a resource so the
 * report is created only when the JAR is written.
 * 
 */
public class CoverageResource extends WriteResource {
    Collection<Clazz> testsuite;
    Collection<Clazz> service;

    public CoverageResource(Collection<Clazz> testsuite,
            Collection<Clazz> service) {
        this.testsuite = testsuite;
        this.service = service;
    }

    @Override
    public long lastModified() {
        return 0;
    }

    @Override
    public void write(OutputStream out) throws IOException {
        try {
            Map<MethodDef, List<MethodDef>> table = getCrossRef(testsuite,
                    service);
            Tag coverage = toTag(table);
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(out,
                    "UTF-8"));
            try {
                coverage.print(0, pw);
            } finally {
                pw.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Tag toTag(Map<MethodDef, List<MethodDef>> catalog) {
        Tag coverage = new Tag("coverage");
        String currentClass = null;
        Tag classTag = null;

        for (Map.Entry<MethodDef, List<MethodDef>> m : catalog.entrySet()) {
            String className = m.getKey().clazz;
            if (!className.equals(currentClass)) {
                classTag = new Tag("class");
                classTag.addAttribute("name", className);
                classTag.addAttribute("package", Clazz.getPackage(className));
                classTag.addAttribute("short", Clazz.getShortName(className));
                coverage.addContent(classTag);
                currentClass = className;
            }
            Tag method = doMethod(new Tag("method"), m.getKey());
            classTag.addContent(method);
            for (MethodDef r : m.getValue()) {
                Tag ref = doMethod(new Tag("ref"), r);
                method.addContent(ref);
            }
        }
        return coverage;
    }

    private static Tag doMethod(Tag tag, MethodDef method) {
        tag.addAttribute("pretty", method.getPretty());
        if (Modifier.isPublic(method.access))
            tag.addAttribute("public", true);
        if (Modifier.isStatic(method.access))
            tag.addAttribute("static", true);
        if (Modifier.isProtected(method.access))
            tag.addAttribute("protected", true);
        if (Modifier.isInterface(method.access))
            tag.addAttribute("interface", true);
        tag.addAttribute("constructor", method.isConstructor());
        if (!method.isConstructor())
            tag.addAttribute("name", method.name);
        tag.addAttribute("descriptor", method.descriptor);
        return tag;
    }
}
