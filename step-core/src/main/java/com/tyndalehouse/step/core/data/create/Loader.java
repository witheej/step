package com.tyndalehouse.step.core.data.create;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.Transaction;
import com.google.inject.Inject;
import com.tyndalehouse.step.core.data.entities.ScriptureReference;

/**
 * The object that will be responsible for loading all the data into a database
 * 
 * @author Chris
 * 
 */
public class Loader {
    private static final int BATCH_SIZE = 1000;
    private static final Logger LOG = LoggerFactory.getLogger(Loader.class);
    private final TimelineModuleLoader timelineModuleLoader;

    /**
     * The loader is given a connection source to load the data
     * 
     * @param timelineModuleLoader loader that loads the timeline module
     */
    @Inject
    public Loader(final TimelineModuleLoader timelineModuleLoader) {
        this.timelineModuleLoader = timelineModuleLoader;
        // this.scriptureReferenceDao = scriptureReferenceDao;
    }

    /**
     * Creates the table and loads the initial data set
     */
    public void init() {
        loadData();
    }

    /**
     * Loads the data into the database
     */
    private void loadData() {
        LOG.debug("Loading initial data");
        final Transaction transaction = Ebean.beginTransaction();
        try {
            transaction.setBatchMode(true);
            transaction.setBatchSize(BATCH_SIZE);
            transaction.setReadOnly(false);
            // set up a list of scripture references that can be populated as we populate the database
            final List<ScriptureReference> scriptureReferences = new ArrayList<ScriptureReference>();
            this.timelineModuleLoader.init(scriptureReferences);

            Ebean.save(scriptureReferences);
            Ebean.commitTransaction();
        } finally {
            Ebean.endTransaction();
        }
    }
}
