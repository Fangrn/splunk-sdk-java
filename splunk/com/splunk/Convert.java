/*
 * Copyright 2011 Splunk, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"): you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.splunk;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import java.lang.RuntimeException;

public class Convert {

    private boolean isLeaf(Node node) {
        if (!node.hasChildNodes()) return true;
        Node child = node.getFirstChild();

        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) return false;
            child = child.getNextSibling();
        }

        return true;
    }

    // debug stuff
    private void fill(int level, String text) {
        for (int i = 0; i < level; i++) System.out.print(text);
    }

    private void dumpNode(Node node, int level) {

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            fill(level, "---");
            System.out.println("NAME: '" + node.getNodeName() + "'");

            if (isLeaf(node)) {
                if (node.getTextContent().trim().length() > 0) {
                    fill(level, "   ");
                    System.out.println("TEXT: '" + node.getTextContent().trim() + "'");
                }
            }

            org.w3c.dom.NamedNodeMap foo = node.getAttributes();
            if (foo != null) {
                int count = foo.getLength();
                if (count > 0) {
                    fill(level, "   ");
                    System.out.print("ATTRS: ");
                    for (int idx=0; idx<count; idx++) {
                        System.out.print(foo.item(idx).getNodeName() + "->'" + foo.item(idx).getNodeValue() + "', ");
                    }
                    System.out.println("");
                }
            }

            Node child = node.getFirstChild();

            while (child != null) {
                dumpNode(child, level+1);
                child = child.getNextSibling();
            }
        }
    }
    // end debug stuff


    private Entity convert(Document dom) {
        Node node;
        Entity entity = new Entity();

        // parse XML
        try {
            Element root = dom.getDocumentElement();

            // root is the feed, and everything else is its children
            node = root.getFirstChild();

            // debug print
            //while (node != null) {
            //    dumpNode(node, 1);
            //    node = node.getNextSibling();
            //}

            // parse: get first level header information
            List<String> firstLevel = Arrays.asList("generator", "id", "title", "updated",
                    "author", "itemsPerPage", "link", "messages", "startIndex",
                    "totalResults");
            while (node != null) {
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    String name = node.getNodeName();
                    // remove prefix through to colon, if one exists -- to behave like python SDK
                    if (name.contains(":")) {
                        name = name.split(":")[1];
                    }

                    if (firstLevel.contains(name)) {
                        HashMap<String,String> attributes = new HashMap<String, String>();
                        String value = node.getTextContent().trim();
                        org.w3c.dom.NamedNodeMap attrs = node.getAttributes();

                        if (isLeaf(node)) {
                            // generator and link require getting attributes from the node
                            if (name.equals("generator") || name.equals("link")) {
                                if (attrs != null) {
                                    int count = attrs.getLength();
                                    if (count > 0) {
                                        for (int idx=0; idx<count; idx++) {
                                            attributes.put(attrs.item(idx).getNodeName(), attrs.item(idx).getNodeValue());
                                        }
                                    }
                                }
                            }
                            if (name.equals("generator")) {
                                entity.header.generator = attributes;
                            } else if (name.equals("id")) {
                                entity.header.id = value;
                            } else if (name.equals("title")) {
                                entity.header.title = value;
                            } else if (name.equals("updated")) {
                                entity.header.updated = value;
                            } else if (name.equals("link")) {
                                entity.header.link.add(attributes);
                            } else if (name.equals("itemsPerPage")) {
                                entity.header.itemsPerPage = Integer.parseInt(value);
                            } else if (name.equals("messages")) {
                                entity.header.messages = value;
                            } else if (name.equals("startIndex")) {
                                entity.header.startIndex = Integer.parseInt(value);
                            } else if (name.equals("totalResults")) {
                                entity.header.totalResults = Integer.parseInt(value);
                            } else {
                                System.out.println("did not find   '" + name + "'");
                            }
                        } else {
                            // only non-leaf at first level is author.

                            if (name.equals("author")) {
                                Node child = node.getFirstChild();
                                while (child != null) {
                                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                                        String cname = child.getNodeName();
                                        String cvalue = child.getTextContent().trim();
                                        entity.header.author.put(cname, cvalue);
                                    }
                                    child = child.getNextSibling();
                                }
                            }
                        }
                    } else {
                        if (name.equals("entry")) {
                            entity.entry.add(entity.parseEntry(node));
                        } else {
                            System.out.println("[1] NOT IN LIST: " + name);
                        }
                    }
                }
                node = node.getNextSibling();
            }
        } catch (Exception e) {
            throw new RuntimeException("XML parse failed: " + e.getMessage());
        }

        return entity;
    }

    // convert an xml input stream into a an Entity object
    public Entity convertXMLData(InputStream inStream) {
        try {
            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
            DocumentBuilder db = factory.newDocumentBuilder();
            Document dom = db.parse(inStream);

            return convert(dom);

        } catch (ParserConfigurationException e) {
            throw new RuntimeException("XML parse failed: " + e.getMessage());
        } catch (SAXException e) {
            throw new RuntimeException("XML parse failed: " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException("XML parse failed (IO): " + e.getMessage());
        }
    }

    // convert an xml string into a an Entity object
    public Entity convertXMLData(String xml) {

        // parse XML
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            InputSource inStream = new InputSource();
            inStream.setCharacterStream(new java.io.StringReader(xml));
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.parse(inStream);

            return convert(dom);

        } catch (ParserConfigurationException e) {
            throw new RuntimeException("XML parse failed: " + e.getMessage());
        } catch (SAXException e) {
            throw new RuntimeException("XML parse failed: " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException("XML parse failed (IO): " + e.getMessage());
        }
    }
}
