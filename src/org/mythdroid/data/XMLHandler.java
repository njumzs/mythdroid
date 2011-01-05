/*
    MythDroid: Android MythTV Remote
    Copyright (C) 2009-2010 foobum@gmail.com
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.mythdroid.data;

import java.util.ArrayList;
import java.util.HashMap;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import android.sax.EndElementListener;
import android.sax.EndTextElementListener;
import android.sax.StartElementListener;

/** Custom SAX Handler - much like android.sax
 * but allows both child elements and text listeners
 */
public class XMLHandler extends DefaultHandler {

    /** XML Element with public members name, text and elements */
    public class Element {
        /** Name of the element */
        public String name;
        /** Text contained in the element or the empty string */
        public String text = ""; //$NON-NLS-1$
        /** HashMap of name -> Element */
        public HashMap<String, Element> elements = 
            new HashMap<String, Element>(16);
        
        private StartElementListener   startListener = null;
        private EndElementListener     endListener   = null;
        private EndTextElementListener textListener  = null;

        /**
         * Constructor
         * @param name name of the element
         */
        public Element(String name) {
            this.name = name;
        }

        /**
         * Get a child element, or create one if it doesn't exist
         * @param name name of the element
         * @return an existing or new child Element
         */
        public Element getChild(String name) {
            return getOrCreateElement(name);
        }

        /**
         * Set or replace the StartElementListener on this element
         * @param l a StartElementListener
         */
        public void setStartElementListener(StartElementListener l) {
            startListener = l;
        }
        
        /**
         * Set or replace the EndElementListener on this element
         * @param l a EndElementListener
         */
        public void setEndElementListener(EndElementListener l) {
            endListener = l;
        }
        
        /**
         * Set or replace the EndTextElementListener on this element
         * @param l a EndTextlementListener
         */
        public void setTextElementListener(EndTextElementListener l) {
            textListener = l;
        }
        
        private Element getOrCreateElement(String name) {
            Element elem = elements.get(name);

            if (elem != null)
                return elem;
            
            elem = new Element(name);
            elements.put(name, elem);
            return elem;

        }

    }

    private ArrayList<Element> tree     = new ArrayList<Element>(8);
    private Element            curElem  = null;
    private Boolean            getChars = false;
    private Element            rootElem = null;

    /**
     * Constructor, call rootElement() post construction to get the 
     * root Element
     * @param rootElement the name of the root element
     */
    public XMLHandler(String rootElement) {
        curElem = new Element(null);
        rootElem = curElem.getChild(rootElement);
    }

    
    /**
     * Get the root Element
     * @return the root Element
     */
    public Element rootElement() {
        return rootElem;
    }

    @Override
    public void startElement(
        String uri, String name, String qname, Attributes attr
    ) {

        Element elem = curElem.elements.get(name);

        if (elem == null) {
            getChars = false;
            return;
        }

        curElem = elem;
        tree.add(elem);
        if (elem.startListener != null) 
            elem.startListener.start(attr);
        if (elem.textListener != null) 
            getChars = true;

    }

    @Override
    public void endElement(String uri, String name, String qname) {
        if (!curElem.name.equals(name)) return;
        if (curElem.text.length() > 0) {
            curElem.textListener.end(curElem.text);
            curElem.text = ""; //$NON-NLS-1$
        }
        if (curElem.endListener != null) 
            curElem.endListener.end();
        int lastIdx = tree.size() - 1;
        tree.remove(lastIdx--);
        if (lastIdx < 0) return;
        curElem = tree.get(lastIdx);
        getChars = curElem.textListener == null ? false : true;
    }

    @Override
    public void characters(char ch[], int start, int end) {
        if (getChars) 
            curElem.text += new String(ch, start, end);
    }

}
