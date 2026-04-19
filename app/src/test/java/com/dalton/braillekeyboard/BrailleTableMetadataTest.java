package com.dalton.braillekeyboard;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BrailleTableMetadataTest {

    @Test
    public void arraysXmlContainsAllDeclaredTableIds() throws Exception {
        List<String> arraysIds = readArrayItems(resolveFile(
                "res/values/arrays.xml",
                "../res/values/arrays.xml"));
        List<TableEntry> tableEntries = readTableEntries(resolveFile(
                "src/main/res/xml/tablelist.xml",
                "app/src/main/res/xml/tablelist.xml"));

        Set<String> arrayIdSet = new LinkedHashSet<String>(arraysIds);
        Set<String> tableIdSet = new LinkedHashSet<String>();
        for (TableEntry entry : tableEntries) {
            tableIdSet.add(entry.id);
        }

        assertEquals("Duplicate IDs found in arrays.xml", arraysIds.size(),
                arrayIdSet.size());
        assertEquals("Duplicate IDs found in tablelist.xml", tableEntries.size(),
                tableIdSet.size());
        assertEquals("arrays.xml and tablelist.xml should expose the same table IDs",
                tableIdSet, arrayIdSet);
    }

    @Test
    public void everyTableFileExistsInTranslationArchive() throws Exception {
        List<TableEntry> tableEntries = readTableEntries(resolveFile(
                "src/main/res/xml/tablelist.xml",
                "app/src/main/res/xml/tablelist.xml"));
        File zipFile = resolveFile(
                "src/main/res/raw/translationtables.zip",
                "app/src/main/res/raw/translationtables.zip");

        List<String> missingFiles = new ArrayList<String>();
        ZipFile zip = new ZipFile(zipFile);
        try {
            for (TableEntry entry : tableEntries) {
                String archivePath = "liblouis/tables/" + entry.fileName;
                if (zip.getEntry(archivePath) == null) {
                    missingFiles.add(entry.id + " -> " + archivePath);
                }
            }
        } finally {
            zip.close();
        }

        assertTrue("Missing table files in translationtables.zip: " + missingFiles,
                missingFiles.isEmpty());
    }

    private static List<String> readArrayItems(File arraysXml) throws Exception {
        Document document = parseXml(arraysXml);
        NodeList arrays = document.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element element = (Element) arrays.item(i);
            if (!"braille_tables".equals(element.getAttribute("name"))) {
                continue;
            }
            List<String> items = new ArrayList<String>();
            NodeList children = element.getElementsByTagName("item");
            for (int j = 0; j < children.getLength(); j++) {
                String value = children.item(j).getTextContent();
                if (value != null) {
                    value = value.trim();
                }
                if (value != null && !value.isEmpty()) {
                    items.add(value);
                }
            }
            return items;
        }
        throw new IllegalStateException("string-array braille_tables not found");
    }

    private static List<TableEntry> readTableEntries(File tableListXml)
            throws Exception {
        Document document = parseXml(tableListXml);
        NodeList tables = document.getElementsByTagName("table");
        List<TableEntry> entries = new ArrayList<TableEntry>(tables.getLength());
        for (int i = 0; i < tables.getLength(); i++) {
            Node node = tables.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;
            String id = element.getAttribute("id").trim();
            String fileName = element.getAttribute("fileName").trim();
            if (id.isEmpty() || fileName.isEmpty()) {
                continue;
            }
            entries.add(new TableEntry(id, fileName));
        }
        return entries;
    }

    private static Document parseXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(file);
    }

    private static File resolveFile(String... candidates) {
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.exists()) {
                return file;
            }
        }
        throw new IllegalStateException("Unable to resolve file: "
                + java.util.Arrays.toString(candidates));
    }

    private static final class TableEntry {
        final String id;
        final String fileName;

        TableEntry(String id, String fileName) {
            this.id = id;
            this.fileName = fileName;
        }
    }
}
