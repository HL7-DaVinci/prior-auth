package org.hl7.davinci.rules;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class RulesPropertyProvider {

    private String propertyFileName = "";

    public RulesPropertyProvider(String propertyFileName) {
        this.propertyFileName = propertyFileName;
    }

    public String getProperty(String property) {
        String result = null;
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(this.propertyFileName);
            Properties properties = new Properties();
            properties.load(inputStream);

            result = properties.getProperty(property);
        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }
        return result;
    }

}