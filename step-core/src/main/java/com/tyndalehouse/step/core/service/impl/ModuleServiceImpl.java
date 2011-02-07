package com.tyndalehouse.step.core.service.impl;

import static com.tyndalehouse.step.core.utils.JSwordUtils.getSortedSerialisableList;
import static org.apache.commons.collections.CollectionUtils.subtract;
import static org.apache.commons.lang.StringUtils.split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.BookCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.tyndalehouse.step.core.exceptions.StepInternalException;
import com.tyndalehouse.step.core.models.BibleVersion;
import com.tyndalehouse.step.core.models.Definition;
import com.tyndalehouse.step.core.service.JSwordService;
import com.tyndalehouse.step.core.service.ModuleService;
import com.tyndalehouse.step.core.utils.StringConversionUtils;

/**
 * Looks up module information, for example lexicon definitions for particular references
 * 
 * @author Chris Burrell
 * 
 */
@Singleton
public class ModuleServiceImpl implements ModuleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleServiceImpl.class);
    private final Map<String, String> defaultLexiconsRefs;
    private final JSwordService jsword;

    /**
     * constructs a service to give module information and content
     * 
     * @param lexiconRefs the default references that should be used
     * @param jsword the jsword service to retrieve data
     */
    @Inject
    public ModuleServiceImpl(@Named("defaultLexiconRefs") final Map<String, String> lexiconRefs,
            final JSwordService jsword) {
        this.defaultLexiconsRefs = lexiconRefs;
        this.jsword = jsword;
    }

    @Override
    public Definition getDefinition(final String reference) {
        final String lookupModule = getLookupModule(reference);
        if (lookupModule != null) {
            return new Definition(reference, this.jsword.getOsisText(lookupModule,
                    StringConversionUtils.getAnyKey(reference, false)));
        }

        LOGGER.warn("No module could be found for [{}]", reference);
        return null;
    }

    @Override
    public List<Definition> getDefinitions(final String references) {
        // first we split the definitions in separate parts
        final String[] refs = split(references);

        if (refs == null) {
            throw new StepInternalException("No references were provided");
        }

        final List<Definition> defs = new ArrayList<Definition>();
        for (final String r : refs) {
            LOGGER.debug("Looking up {}", r);
            final Definition definition = getDefinition(r);
            if (definition != null) {
                defs.add(definition);
            }
        }

        return defs;
    }

    /**
     * Returns the module that should be used to lookup a reference
     * 
     * @param reference the reference to base the lookup option on
     * @return the initials of the module to lookup
     */
    String getLookupModule(final String reference) {
        for (final Entry<String, String> e : this.defaultLexiconsRefs.entrySet()) {
            if (reference.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    @Override
    public List<BibleVersion> getAvailableModules() {
        LOGGER.info("Getting bible versions");
        return getSortedSerialisableList(this.jsword.getInstalledModules(BookCategory.BIBLE, BookCategory.DICTIONARY,
                BookCategory.COMMENTARY));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<BibleVersion> getAllInstallableModules() {
        LOGGER.info("Returning all modules currently not installed");
        final List<BibleVersion> installedVersions = getAvailableModules();
        final List<Book> allModules = this.jsword.getAllModules(BookCategory.BIBLE, BookCategory.DICTIONARY,
                BookCategory.COMMENTARY);

        return getSortedSerialisableList(subtract(allModules, installedVersions));
    }
}