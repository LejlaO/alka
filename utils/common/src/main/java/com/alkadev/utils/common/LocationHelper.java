package com.alkadev.utils.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URISyntaxException;

public class LocationHelper {
    private static final Logger logger = LogManager.getLogger(LocationHelper.class);

    public static String getParentPath(Class sourceClass) {
        String startingPointPath = "";
        try {
            startingPointPath = new File(sourceClass.getProtectionDomain().getCodeSource().getLocation()
                    .toURI()).getParentFile().getParent();

        } catch (URISyntaxException e) {
            logger.fatal("Starting point path error! URISyntaxException. Exiting.");
            e.printStackTrace();
            System.exit(1);
        }
        return startingPointPath;
    }

    public static String getParentJarName(Class sourceClass) {
        String jarName;
        jarName = new File(sourceClass.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath())
                .getName();

        return jarName;
    }
}
