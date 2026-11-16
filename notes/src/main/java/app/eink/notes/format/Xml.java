package app.eink.notes.format;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/** Shared by the importers: safe XML parsing and bounded reads, because files you open are not trusted. */
final class Xml {
    static final int MAX_PART = 40 * 1024 * 1024;

    private Xml() {}

    static void parse(byte[] xml, DefaultHandler h) throws IOException {
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.setNamespaceAware(false);
            // No DTDs: they are how XML files pull in other files or blow up in memory.
            try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
            try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
            try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
            SAXParser p = f.newSAXParser();
            InputSource src = new InputSource(new ByteArrayInputStream(xml));
            p.parse(src, h);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("This file is not a valid document: " + e.getMessage());
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_PART) throw new IOException("This document is too large to open");
        }
        return out.toByteArray();
    }
}
