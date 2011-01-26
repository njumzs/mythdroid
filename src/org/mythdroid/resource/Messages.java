package org.mythdroid.resource;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Retrieve translatable strings that we can't retrieve from android resources
 */
public class Messages {
    private static final String BUNDLE_NAME
        = "org.mythdroid.resource.messages"; //$NON-NLS-1$

    private static final ResourceBundle RESOURCE_BUNDLE =
        ResourceBundle.getBundle(BUNDLE_NAME);

    private Messages() {}

    /**
     * Get a translatable string from messages[-<cc>].properties
     * @param key - the name of the string
     */
    public static String getString(String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }
}
