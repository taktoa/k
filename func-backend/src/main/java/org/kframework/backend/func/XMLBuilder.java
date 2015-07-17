// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import org.kframework.utils.file.FileUtil;
import static org.kframework.backend.func.FuncUtil.*;

/**
 * Create XML and render it as an s-expression
 *
 * @author Remy Goldschmidt
 */
public class XMLBuilder {
    private final StringBuilder sb = new StringBuilder();

    public XMLBuilder append(String xml) {
        sb.append(xml);
        return this;
    }

    public XMLBuilder append(XMLBuilder xml) {
        sb.append(xml.toString());
        return this;
    }

    public XMLBuilder addXML(String tag, XMLAttr... attrs) {
        return addXMLSingleton(tag, attrs);
    }

    public XMLBuilder addXML(String tag, String contents, XMLAttr... attrs) {
        beginXML(tag, attrs);
        addXMLContents(contents);
        endXML(tag);
        return this;
    }

    public XMLBuilder addXMLContents(Object contents) {
        return xmlOut("%s", escapeXML(contents.toString()));
    }

    public XMLBuilder addXMLSingleton(String tagName, XMLAttr... attrs) {
        return xmlOut("<%s%s />", tagName, renderXMLAttrs(attrs));
    }

    public XMLBuilder beginXML(String tagName, XMLAttr... attrs) {
        return xmlOut("<%s%s>", tagName, renderXMLAttrs(attrs));
    }

    public XMLBuilder endXML(String tagName) {
        return xmlOut("</%s>", tagName);
    }

    public String renderSExpr(FileUtil files) {
        return xmlToSExpr(files, sb.toString());
    }

    private XMLBuilder xmlOut(String format, Object... objs) {
        sb.append(String.format(format, objs));
        return this;
    }

    private String renderXMLAttrs(XMLAttr... attrs) {
        return asList(attrs).stream()
            .map(x -> " " + x.render())
            .collect(joining());
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    public static class XMLAttr {
        private final String name, value;
        public XMLAttr(String name, String value) {
            this.name  = name;
            this.value = value;
        }
        public String render() {
            return String.format("%s=\"%s\"", name, value);
        }
    }
}
