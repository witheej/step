/*******************************************************************************
 * Copyright (c) 2012, Directors of the Tyndale STEP Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 *
 * Redistributions of source code must retain the above copyright 
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright 
 * notice, this list of conditions and the following disclaimer in 
 * the documentation and/or other materials provided with the 
 * distribution.
 * Neither the name of the Tyndale House, Cambridge (www.TyndaleHouse.com)  
 * nor the names of its contributors may be used to endorse or promote 
 * products derived from this software without specific prior written 
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING 
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package com.tyndalehouse.step.core.service.impl;

import com.tyndalehouse.step.core.data.EntityDoc;
import com.tyndalehouse.step.core.data.EntityIndexReader;
import com.tyndalehouse.step.core.data.EntityManager;
import com.tyndalehouse.step.core.exceptions.LuceneSearchException;
import com.tyndalehouse.step.core.exceptions.TranslatedException;
import com.tyndalehouse.step.core.models.InterlinearMode;
import com.tyndalehouse.step.core.models.LexiconSuggestion;
import com.tyndalehouse.step.core.models.SearchToken;
import com.tyndalehouse.step.core.models.search.KeyedSearchResultSearchEntry;
import com.tyndalehouse.step.core.models.search.KeyedVerseContent;
import com.tyndalehouse.step.core.models.search.LexicalSearchEntry;
import com.tyndalehouse.step.core.models.search.SearchEntry;
import com.tyndalehouse.step.core.models.search.SearchResult;
import com.tyndalehouse.step.core.models.search.TimelineEventSearchEntry;
import com.tyndalehouse.step.core.models.search.VerseSearchEntry;
import com.tyndalehouse.step.core.service.BibleInformationService;
import com.tyndalehouse.step.core.service.SearchService;
import com.tyndalehouse.step.core.service.TimelineService;
import com.tyndalehouse.step.core.service.helpers.GlossComparator;
import com.tyndalehouse.step.core.service.helpers.OriginalSpellingComparator;
import com.tyndalehouse.step.core.service.jsword.JSwordMetadataService;
import com.tyndalehouse.step.core.service.jsword.JSwordPassageService;
import com.tyndalehouse.step.core.service.jsword.JSwordSearchService;
import com.tyndalehouse.step.core.service.search.SubjectSearchService;
import com.tyndalehouse.step.core.service.search.impl.OriginalWordSuggestionServiceImpl;
import com.tyndalehouse.step.core.utils.StringConversionUtils;
import com.tyndalehouse.step.core.utils.StringUtils;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.QueryParser.Operator;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import org.crosswire.jsword.passage.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.tyndalehouse.step.core.service.helpers.OriginalWordUtils.STRONG_NUMBER_FIELD;
import static com.tyndalehouse.step.core.service.helpers.OriginalWordUtils.convertToSuggestion;
import static com.tyndalehouse.step.core.service.helpers.OriginalWordUtils.getFilter;
import static com.tyndalehouse.step.core.service.impl.VocabularyServiceImpl.padStrongNumber;
import static com.tyndalehouse.step.core.utils.StringUtils.isNotBlank;
import static java.lang.Character.isDigit;

/**
 * A federated search service implementation. see {@link SearchService}
 *
 * @author chrisburrell
 */
@Singleton
public class SearchServiceImpl implements SearchService {
    /**
     * value representing a vocabulary sort
     */
    public static final String VOCABULARY_SORT = "VOCABULARY";
    /**
     * value representing a original spelling sort
     */
    public static final Object ORIGINAL_SPELLING_SORT = "ORIGINAL_SPELLING";

    private static final String BASE_GREEK_VERSION = "WHNU";
    private static final String BASE_HEBREW_VERSION = "OSMHB";
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchServiceImpl.class);
    private static final String STRONG_QUERY = "strong:";
    private final JSwordSearchService jswordSearch;
    private final JSwordPassageService jsword;
    private final TimelineService timeline;
    private final EntityIndexReader definitions;
    private final EntityIndexReader specificForms;
    private final EntityIndexReader timelineEvents;
    private final JSwordMetadataService jswordMetadata;
    private final SubjectSearchService subjects;
    private final BibleInformationService bibleInfoService;

    /**
     * @param jswordSearch     the search service
     * @param jsword           used to convert references to numerals, etc.
     * @param subjects         the service that executes Subject searches
     * @param timeline         the timeline service
     * @param bibleInfoService the service to get information about various bibles/commentaries
     * @param entityManager    the manager for all entities stored in lucene
     */
    @Inject
    public SearchServiceImpl(final JSwordSearchService jswordSearch, final JSwordPassageService jsword,
                             final JSwordMetadataService jswordMetadata,
                             final SubjectSearchService subjects, final TimelineService timeline,
                             final BibleInformationService bibleInfoService, final EntityManager entityManager) {
        this.jswordSearch = jswordSearch;
        this.jsword = jsword;
        this.jswordMetadata = jswordMetadata;
        this.subjects = subjects;
        this.timeline = timeline;
        this.bibleInfoService = bibleInfoService;
        this.definitions = entityManager.getReader("definition");
        this.specificForms = entityManager.getReader("specificForm");
        this.timelineEvents = entityManager.getReader("timelineEvent");

    }

    @Override
    public long estimateSearch(final SearchQuery sq) {
        try {
            return this.jswordSearch.estimateSearchResults(sq);
            // CHECKSTYLE:OFF
        } catch (final Exception ex) {
            // we catch any exception, trace log it
            LOGGER.warn("Unable to estimate query: [{}]. Exception message: [{}]", sq.getOriginalQuery(),
                    ex.getMessage());
            LOGGER.trace(ex.getMessage(), ex);
            return -1;
            // CHECKSTYLE:ON
        }
    }

    @Override
    public Object runQuery(final List<SearchToken> searchTokens, final String options,
                           final String display, final int page, final String filter, int context) {
        final List<String> versions = new ArrayList<String>(4);
        final StringBuilder references = new StringBuilder();
        final List<String> strongSearches = new ArrayList<String>(1);
        final List<String> textSearches = new ArrayList<String>(1);
        final List<String> meaningSearches = new ArrayList<String>(1);


        for (SearchToken token : searchTokens) {
            if (SearchToken.VERSION.equals(token.getTokenType())) {
                versions.add(token.getToken());
            } else if (SearchToken.REFERENCE.equals(token.getTokenType())) {
                if (references.length() > 0) {
                    references.append(';');
                }
                references.append(token.getToken());
            } else if (SearchToken.STRONG_NUMBER.equals(token.getTokenType())) {
                strongSearches.add(token.getToken());
            } else if (SearchToken.TEXT_SEARCH.equals(token.getTokenType())) {
                textSearches.add(token.getToken());
            } else if (SearchToken.MEANINGS.equals(token.getTokenType())) {
                meaningSearches.add(token.getToken());
            }
        }

        if (versions.size() == 0) {
            versions.add(JSwordPassageService.REFERENCE_BOOK);
        }

        return runCorrectSearch(
                versions, references.toString(),
                options, StringUtils.isBlank(display) ? InterlinearMode.NONE.name() : display,
                strongSearches, textSearches, meaningSearches, page, filter, context);
    }

    /**
     * Establishes what the correct search should be and kicks off the right type of search
     *
     * @param versions       the list of versions
     * @param references     the list of references
     * @param options        the options
     * @param displayMode    the display mode
     * @param strongSearches the strong searches
     * @param textSearches   a list of text searches
     * @param context        amount of context to be used in searhc
     * @return the results
     */
    private Object runCorrectSearch(final List<String> versions, final String references,
                                    final String options, final String displayMode,
                                    final List<String> strongSearches,
                                    final List<String> textSearches,
                                    final List<String> meaningSearches,
                                    final int pageNumber,
                                    final String filter,
                                    final int context) {
        final List<IndividualSearch> individualSearches = new ArrayList<IndividualSearch>(2);
        String[] filters = null;
        if (StringUtils.isNotBlank(filter)) {
            filters = StringUtils.split(filter);
        }

        //we will prefer a word search to anything else...
        addWordSearches(versions, references, strongSearches, filters, individualSearches);
        addSearch(SearchType.ORIGINAL_MEANING, versions, references, meaningSearches, filters, individualSearches);
        addSearch(SearchType.TEXT, versions, references, textSearches, null, individualSearches);

        if (individualSearches.size() != 0) {
            return this.search(new SearchQuery(pageNumber, context, displayMode, individualSearches.toArray(new IndividualSearch[individualSearches.size()])));
        }
        return this.bibleInfoService.getPassageText(
                versions.get(0), references, options,
                getExtraVersions(versions), displayMode);
    }

    /**
     * @param searchType         the type of search
     * @param versions           the list of versions
     * @param references         the list of references
     * @param searches           the searches that we want to perform
     * @param individualSearches the searches to perform
     */
    private void addSearch(final SearchType searchType, final List<String> versions, final String references, final List<String> searches, final String[] filter, final List<IndividualSearch> individualSearches) {
        for (final String search : searches) {
            individualSearches.add(new IndividualSearch(searchType, versions, search, references, filter));
        }
    }

    /**
     * Adds a word search to the list of searches we will perform
     *
     * @param versions           the list of versions
     * @param references         the list of references
     * @param strongSearches     the strong searches
     * @param individualSearches the searches to perform
     */
    private void addWordSearches(final List<String> versions, final String references,
                                 final List<String> strongSearches, final String[] filters,
                                 final List<IndividualSearch> individualSearches) {

        for (final String strong : strongSearches) {
            boolean isGreek = strong.charAt(0) == 'G';
            individualSearches.add(new IndividualSearch(
                    isGreek ? SearchType.ORIGINAL_GREEK_FORMS : SearchType.ORIGINAL_HEBREW_FORMS,
                    versions, strong, references, filters));
        }
    }

    /**
     * Concatenates all but the last versions
     *
     * @param versions version
     * @return the concatenated versions
     */
    private String getExtraVersions(final List<String> versions) {
        StringBuilder sb = new StringBuilder(128);
        for (int i = 1; i < versions.size(); i++) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(versions.get(i));
        }
        return sb.toString();
    }

    @Override
    public SearchResult search(final SearchQuery sq) {
        try {
            return doSearch(sq);
            // CHECKSTYLE:OFF
        } catch (final LuceneSearchException ex) {
            // CHECKSTYLE:ON
            throw new TranslatedException(ex, "search_invalid");
        }
    }

    /**
     * Carries out the required search
     *
     * @param sq the sq
     * @return the search result
     */
    private SearchResult doSearch(final SearchQuery sq) {
        final long start = System.currentTimeMillis();

        SearchResult result;
        // if we've only got one search, we want to retrieve the keys, the page, etc. all in one go
        try {

            if (sq.isIndividualSearch()) {
                result = executeOneSearch(sq);
            } else {
                result = executeJoiningSearches(sq);
            }
        } catch (final AbortQueryException ex) {
            result = new SearchResult();
        }

        // we split the query into separate searches
        // we run the search against the selected versions

        // we retrieve the keys
        // join the keys
        // return the results

        result.setSearchType(sq.getSearches()[0].getType());
        result.setPageSize(sq.getPageSize());
        result.setPageNumber(sq.getPageNumber());
        result.setTimeTookTotal(System.currentTimeMillis() - start);
        result.setQuery(sq.getOriginalQuery());

        final String[] allVersions = sq.getCurrentSearch().getVersions();
        result.setMasterVersion(allVersions[0]);
        result.setExtraVersions(StringUtils.join(allVersions, 1));
        specialSort(sq, result);
        enrichWithLanguages(sq, result);
        return result;
    }

    /**
     * Puts the languages of each module into the result returned to the UI.
     *
     * @param sq     the search query
     * @param result the result
     */
    private void enrichWithLanguages(final SearchQuery sq, final SearchResult result) {
        IndividualSearch lastSearch = sq.getCurrentSearch();
        result.setLanguageCode(jswordMetadata.getLanguages(lastSearch.getVersions()));
    }

    /**
     * We may have a special type of sort to operate
     *
     * @param sq     the search query
     * @param result the result to be sorted
     */
    private void specialSort(final SearchQuery sq, final SearchResult result) {
        // we only do this kind of sort if we have some strong numbers, and at least 2!
        if (result.getStrongHighlights() != null && result.getStrongHighlights().size() > 1) {

            result.setOrder(sq.getSortOrder());
            if (VOCABULARY_SORT.equals(sq.getSortOrder())) {
                sortByStrongNumber(sq, result, new GlossComparator());
            } else if (ORIGINAL_SPELLING_SORT.equals(sq.getSortOrder())) {
                sortByStrongNumber(sq, result, new OriginalSpellingComparator());
            }
        }
    }

    /**
     * For this kind of sort, we find out which strong number is present in a verse, then run a comparator on
     * the strong numbers sorts results by strong number
     *
     * @param sq         the search criteria
     * @param result     results
     * @param comparator the comparator to use to sort the strong numbers
     */
    private void sortByStrongNumber(final SearchQuery sq, final SearchResult result,
                                    final Comparator<? super EntityDoc> comparator) {
        // sq should have the strong numbers, if we're doing this kind of sort
        List<EntityDoc> strongNumbers = sq.getDefinitions();
        if (strongNumbers == null) {
            // stop searching
            LOGGER.warn("Attempting to sort by strong number, but no strong numbers available. ");
            return;
        }

        final Set<String> strongs = new HashSet<String>(result.getStrongHighlights());
        final List<SearchEntry> entries = result.getResults();
        final List<LexicalSearchEntry> noOrder = new ArrayList<LexicalSearchEntry>(0);

        final Map<String, List<LexicalSearchEntry>> keyedOrder = new HashMap<String, List<LexicalSearchEntry>>(
                strongs.size());

        extractAllStrongNumbers(strongs, entries, noOrder, keyedOrder);

        // now work out the order of the strong numbers, probably best in terms of the gloss...
        // order the definitions, then simply re-do the list of verse search entries
        Collections.sort(strongNumbers, comparator);

        // if we have filters, then we need to reduce further...
        strongNumbers = filterDefinitions(sq, strongNumbers);

        // now we have sorted definitions, we need to rebuild the search result
        final List<LexicalSearchEntry> newOrder = rebuildSearchResults(strongNumbers, keyedOrder);

        final String[] filter = sq.getCurrentSearch().getOriginalFilter();
        if (filter == null || filter.length == 0) {
            newOrder.addAll(noOrder);
        }
        result.setResults(specialPaging(sq, newOrder));
    }

    /**
     * Takes a new order and rebuilds a list of search results
     *
     * @param lexiconDefinitions the strong numbers, ordered
     * @param keyedOrder         the set of results to be re-ordered
     * @return a new list of results, now ordered
     */
    private List<LexicalSearchEntry> rebuildSearchResults(final List<EntityDoc> lexiconDefinitions,
                                                          final Map<String, List<LexicalSearchEntry>> keyedOrder) {
        final List<LexicalSearchEntry> newOrder = new ArrayList<LexicalSearchEntry>();
        for (final EntityDoc def : lexiconDefinitions) {
            final List<LexicalSearchEntry> list = keyedOrder.get(def.get(STRONG_NUMBER_FIELD));
            if (list != null) {
                newOrder.addAll(list);
                for (final LexicalSearchEntry e : list) {
                    e.setStepGloss(def.get("stepGloss"));
                    e.setStepTransliteration(def.get("stepTransliteration"));
                    e.setAccentedUnicode(def.get("accentedUnicode"));
                }
            }
        }
        return newOrder;
    }

    /**
     * Extracts all strong numbers from verses
     *
     * @param strongs    a place to store the strong numbers found so far in the search results, identified from
     *                   the search itself
     * @param entries    the verse entries that have been found
     * @param noOrder    the "noOrder" list which will contain all verse entries that cannot be matched
     * @param keyedOrder the new order, to be built up. In this method, we simply store the verses, which are
     *                   sorted later.
     */
    private void extractAllStrongNumbers(final Set<String> strongs, final List<SearchEntry> entries,
                                         final List<LexicalSearchEntry> noOrder, final Map<String, List<LexicalSearchEntry>> keyedOrder) {
        for (final SearchEntry entry : entries) {
            boolean added = false;
            if (entry instanceof VerseSearchEntry) {
                added = reOrderSearchEntry(strongs, keyedOrder, ((VerseSearchEntry) entry).getPreview(), (VerseSearchEntry) entry);
            } else if (entry instanceof KeyedSearchResultSearchEntry) {
                final KeyedSearchResultSearchEntry wrapperEntry = (KeyedSearchResultSearchEntry) entry;
                for (KeyedVerseContent e : wrapperEntry.getVerseContent()) {
                    added = reOrderSearchEntry(strongs, keyedOrder, e.getPreview(), wrapperEntry);
                    if (added) {
                        break;
                    }
                }
            }

            // should never happen
            if (!added) {
                if (entry instanceof LexicalSearchEntry) {
                    noOrder.add((LexicalSearchEntry) entry);
                } else {
                    LOGGER.error("Attempting to sort non LexicalSearchEntry.");
                }
            }
        }
    }

    /**
     * Re-orders the search entry
     *
     * @param strongs     the list of strongs in the verses
     * @param keyedOrder  the order that we're after
     * @param verseText   the OSIS fragment
     * @param parentEntry the entry that we're ordering in the list
     * @return true if the entry was added
     */
    private boolean reOrderSearchEntry(final Set<String> strongs, final Map<String, List<LexicalSearchEntry>> keyedOrder, final String verseText, final LexicalSearchEntry parentEntry) {
        for (final String strong : strongs) {
            if (strong == null) {
                continue;
            }

            if (verseText.contains(strong)) {
                List<LexicalSearchEntry> list = keyedOrder.get(strong);
                if (list == null) {
                    list = new ArrayList<LexicalSearchEntry>(16);
                    keyedOrder.put(strong, list);
                }
                list.add(parentEntry);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the definitions onto the result object
     *
     * @param result             the result object
     * @param lexiconDefinitions the definitions that have been included in the search
     */
    private void setDefinitionForResults(final SearchResult result, final List<EntityDoc> lexiconDefinitions) {
        final List<LexiconSuggestion> suggestions = new ArrayList<LexiconSuggestion>();
        for (final EntityDoc def : lexiconDefinitions) {
            suggestions.add(convertToSuggestion(def));
        }
        result.setDefinitions(suggestions);
    }

    /**
     * Keep definitions that are of current interest to the user... Remove all others
     *
     * @param sq                 the search criteria
     * @param lexiconDefinitions the definitions
     * @return a list of definitions to be included in the filter
     */
    private List<EntityDoc> filterDefinitions(final SearchQuery sq, final List<EntityDoc> lexiconDefinitions) {
        final String[] originalFilter = sq.getCurrentSearch().getOriginalFilter();
        if (originalFilter == null || originalFilter.length == 0) {
            return lexiconDefinitions;
        }

        // bubble intersection, acceptable, because we're only dealing with a handful of definitions
        final List<EntityDoc> keep = new ArrayList<EntityDoc>(lexiconDefinitions.size());

        for (final EntityDoc def : lexiconDefinitions) {
            for (final String filteredValue : originalFilter) {
                if (def.get("accentedUnicode").equals(filteredValue)) {
                    keep.add(def);

                    // break out of filterValues loop, and proceed with next definition
                    break;
                }
            }
        }
        return keep;
    }

    /**
     * Reduces the results to the correct page size
     *
     * @param sq       the search criteria
     * @param newOrder the elements in the new order
     * @return the new set of results, with only pageSize results
     */
    private List<SearchEntry> specialPaging(final SearchQuery sq, final List<LexicalSearchEntry> newOrder) {
        // runs paging after a special sort
        // we want
        final int firstElement = (sq.getPageNumber() - 1) * sq.getPageSize();
        final int lastElement = firstElement + sq.getPageSize();

        final List<SearchEntry> newResults = new ArrayList<SearchEntry>(sq.getPageSize());
        for (int ii = firstElement; ii < lastElement && ii < newOrder.size(); ii++) {
            newResults.add(newOrder.get(ii));
        }
        return newResults;
    }

    /**
     * Runs a number of searches, joining them together (known as "refine searches")
     *
     * @param sq the search query object
     * @return the list of search results
     */
    private SearchResult executeJoiningSearches(final SearchQuery sq) {
        // we run each individual search, and get all the keys out of each

        final Key results = runJoiningSearches(sq);

        // now retrieve the results, we need to retrieve results as per the last type of search run
        // so first of all, we set the allKeys flag to false
        sq.setAllKeys(false);

        return extractSearchResults(sq, results);
    }

    /**
     * Runs each individual search and gives us a key that can be used to retrieve every passage
     *
     * @param sq the search query
     * @return the key to all the results
     */
    private Key runJoiningSearches(final SearchQuery sq) {
        Key results = null;
        do {
            switch (sq.getCurrentSearch().getType()) {
                case TEXT:
                    results = intersect(results, this.jswordSearch.searchKeys(sq));
                    break;
                case ORIGINAL_GREEK_FORMS:
                case ORIGINAL_HEBREW_FORMS:
                    adaptQueryForStrongSearch(sq);
                    results = intersect(results, this.jswordSearch.searchKeys(sq));
                    break;
                case ORIGINAL_GREEK_RELATED:
                case ORIGINAL_HEBREW_RELATED:
                    adaptQueryForRelatedStrongSearch(sq);
                    results = intersect(results, this.jswordSearch.searchKeys(sq));
                    break;
                case ORIGINAL_MEANING:
                    adaptQueryForMeaningSearch(sq);
                    results = intersect(results, this.jswordSearch.searchKeys(sq));
                    break;
                case ORIGINAL_GREEK_EXACT:
                case ORIGINAL_HEBREW_EXACT:
                    results = intersect(results, getKeysFromOriginalText(sq));
                    break;
                default:
                    throw new TranslatedException("refinement_not_supported", sq.getOriginalQuery(), sq
                            .getCurrentSearch().getType().getLanguageKey());
            }
        } while (sq.hasMoreSearches());
        return results;
    }

    /**
     * Extracts the search results from a multi-joined search query
     *
     * @param sq      the search query
     * @param results the results
     * @return the search results ready to send back
     */
    private SearchResult extractSearchResults(final SearchQuery sq, final Key results) {
        final IndividualSearch lastSearch = sq.getLastSearch();
        switch (lastSearch.getType()) {
            case TEXT:
            case ORIGINAL_MEANING:
            case ORIGINAL_GREEK_EXACT:
            case ORIGINAL_GREEK_FORMS:
            case ORIGINAL_GREEK_RELATED:
            case ORIGINAL_HEBREW_EXACT:
            case ORIGINAL_HEBREW_RELATED:
            case ORIGINAL_HEBREW_FORMS:
                return buildCombinedVerseBasedResults(sq, results);
            default:
                throw new TranslatedException("refinement_not_supported", sq.getOriginalQuery(), lastSearch
                        .getType().getLanguageKey());

        }
    }

    /**
     * executes a single search
     *
     * @param sq the search query results
     * @return the results from the search query
     */
    private SearchResult executeOneSearch(final SearchQuery sq) {
        final IndividualSearch currentSearch = sq.getCurrentSearch();
        switch (currentSearch.getType()) {
            case TEXT:
                return runTextSearch(sq);
            case SUBJECT_SIMPLE:
            case SUBJECT_EXTENDED:
            case SUBJECT_FULL:
            case SUBJECT_RELATED:
                return this.subjects.search(sq);
            case TIMELINE_DESCRIPTION:
                return runTimelineDescriptionSearch(sq);
            case TIMELINE_REFERENCE:
                return runTimelineReferenceSearch(sq);
            case ORIGINAL_GREEK_FORMS:
            case ORIGINAL_HEBREW_FORMS:
                return runAllFormsStrongSearch(sq);
            case ORIGINAL_GREEK_RELATED:
            case ORIGINAL_HEBREW_RELATED:
                return runRelatedStrongSearch(sq);
            case ORIGINAL_GREEK_EXACT:
            case ORIGINAL_HEBREW_EXACT:
                return runExactOriginalTextSearch(sq);
            case ORIGINAL_MEANING:
                return runMeaningSearch(sq);
            default:
                throw new TranslatedException("search_unknown");
        }
    }
    /**
     * Runs a query against the JSword modules backends
     *
     * @param sq the search query contained
     * @return the search to be run
     */
    private SearchResult runTextSearch(final SearchQuery sq) {
        return runTextSearch(sq, false);
    }
    
    /**
     * Runs a query against the JSword modules backends
     *
     * @param sq the search query contained
     * @return the search to be run
     */
    private SearchResult runTextSearch(final SearchQuery sq, boolean searchOnTaggedText) {
        final IndividualSearch is = sq.getCurrentSearch();

        // for text searches, we may have a prefix of t=
        final String[] versions = is.getVersions();

        if (versions.length == 1) {
            return this.jswordSearch.search(sq, versions[0]);
        }

        // build combined results
        return buildCombinedVerseBasedResults(sq, this.jswordSearch.searchKeys(sq));
    }

    /**
     * Obtains all glosses with a particular meaning
     *
     * @param sq the search criteria
     * @return the result from the corresponding text search
     */
    private SearchResult runMeaningSearch(final SearchQuery sq) {
        final Set<String> strongs = adaptQueryForMeaningSearch(sq);

        final SearchResult result = runStrongTextSearch(sq, strongs);
        setDefinitionForResults(result, sq.getDefinitions());

        // we can now use the filter and save ourselves some effort
        return result;
    }

    /**
     * Runs the search looking for particular strongs
     *
     * @param sq the search query
     * @return the results
     */
    private SearchResult runAllFormsStrongSearch(final SearchQuery sq) {
        final Set<String> strongs = adaptQueryForStrongSearch(sq);

        // and then run the search
        return runStrongTextSearch(sq, strongs);
    }

    /**
     * Looks up all related strongs then runs the search
     *
     * @param sq the search query
     * @return the results
     */
    private SearchResult runRelatedStrongSearch(final SearchQuery sq) {
        final Set<String> strongs = adaptQueryForRelatedStrongSearch(sq);

        // and then run the search
        final SearchResult result = runStrongTextSearch(sq, strongs);
        setDefinitionForResults(result, sq.getDefinitions());
        return result;
    }

    /**
     * Runs a search using the exact form, i.e. without any lookups, a straight text search on the original
     * text
     *
     * @param sq the search criteria
     * @return the results to be shown
     */
    private SearchResult runExactOriginalTextSearch(final SearchQuery sq) {
        final Key resultKeys = getKeysFromOriginalText(sq);

        // return results from appropriate versions
        return extractSearchResults(sq, resultKeys);
    }

    /**
     * Runs the search, and adds teh strongs to the search results
     *
     * @param sq      the search criteria
     * @param strongs the list of strongs that were searched for
     * @return the search results
     */
    private SearchResult runStrongTextSearch(final SearchQuery sq, final Set<String> strongs) {
        final SearchResult textResults = runTextSearch(sq);
        textResults.setStrongHighlights(new ArrayList<String>(strongs));
        return textResults;
    }

    /**
     * Searches for all passage references matching an original text (greek or hebrew)
     *
     * @param sq the search criteria
     * @return the list of verses
     */
    private Key getKeysFromOriginalText(final SearchQuery sq) {
        final IndividualSearch currentSearch = sq.getCurrentSearch();
        final String[] soughtAfterVersions = currentSearch.getVersions();

        // overwrite version with Tisch to do the search
        if (currentSearch.getType() == SearchType.ORIGINAL_GREEK_EXACT) {
            currentSearch.setVersions(new String[]{BASE_GREEK_VERSION});
            currentSearch.setQuery(unaccent(currentSearch.getQuery(), sq));
        } else {
            currentSearch.setVersions(new String[]{BASE_HEBREW_VERSION});
        }

        final Key resultKeys = this.jswordSearch.searchKeys(sq);

        // now overwrite again and do the intersection with the normal text
        currentSearch.setVersions(soughtAfterVersions);
        return resultKeys;
    }

    /**
     * Attempts to recognise the input, whether it is a strong number, a transliteration or a hebrew/greek
     * word
     *
     * @param sq the search criteria
     * @return a list of match strong numbers
     */
    private Set<String> getStrongsFromTextCriteria(final SearchQuery sq) {
        // we can be dealing with a strong number, if so, no work required...
        final String query = sq.getCurrentSearch().getQuery();
        if (query.isEmpty()) {
            return new HashSet<String>(0);
        }

        final boolean wildcard = query.charAt(query.length() - 1) == '*';
        final String searchQuery = wildcard ? query.replace("*", "%") : query;

        Set<String> strongs;
        if (isDigit(query.charAt(0)) || (query.length() > 1 && isDigit(query.charAt(1)))) {
            // then we're dealing with a strong number, without its G/H prefix
            strongs = getStrongsFromCurrentSearch(sq);
        } else {
            // we're dealing with some sort of greek/hebrew form so we search the tables for this
            strongs = searchTextFieldsForDefinition(searchQuery);
        }

        // run rules for transliteration
        if (strongs.isEmpty()) {
            // run transliteration rules
            final SearchType type = sq.getCurrentSearch().getType();
            if (type.isGreek() || type.isHebrew()) {
                strongs = findByTransliteration(searchQuery, type.isGreek());
            }
        }

        // now filter
        return strongs;
    }

    /**
     * Looks up all the glosses for a particular word, and then adapts to strong search and continues as
     * before
     *
     * @param sq search criteria
     * @return a list of matching strongs
     */
    private Set<String> adaptQueryForMeaningSearch(final SearchQuery sq) {
        final String query = sq.getCurrentSearch().getQuery();


        final QueryParser queryParser = new QueryParser(Version.LUCENE_30, "translationsStem",
                this.definitions.getAnalyzer());
        queryParser.setDefaultOperator(Operator.OR);

        try {
            //we need to also add the step gloss, but since we need the analyser for stems,
            //we want to use the query parser that does the tokenization for us
            //could probably do better if required
            String[] terms = StringUtils.split(query);
            StringBuilder finalQuery = new StringBuilder();
            for (String term : terms) {
                final String escapedTerm = QueryParser.escape(term);
                finalQuery.append(escapedTerm);
                finalQuery.append(" stepGlossStem:");
                finalQuery.append(escapedTerm);
            }

            final Query parsed = queryParser.parse("-stopWord:true " + finalQuery.toString());
            final EntityDoc[] matchingMeanings = this.definitions.search(parsed);

            final Set<String> strongs = new HashSet<String>(matchingMeanings.length);
            for (final EntityDoc d : matchingMeanings) {
                final String strongNumber = d.get(STRONG_NUMBER_FIELD);
                if (isInFilter(strongNumber, sq)) {
                    strongs.add(strongNumber);
                }
            }

            final String textQuery = getQuerySyntaxForStrongs(strongs, sq);
            sq.getCurrentSearch().setQuery(textQuery);
            sq.setDefinitions(matchingMeanings);

            // return the strongs that the search will match
            return strongs;
        } catch (final ParseException e) {
            throw new TranslatedException(e, "search_invalid");
        }
    }

    /**
     * @param strongNumber the strong number
     * @param sq           the search query
     * @return true if the filter is empty, or if the strong number is in the filter
     */
    private boolean isInFilter(final String strongNumber, final SearchQuery sq) {
        final String[] originalFilter = sq.getCurrentSearch().getOriginalFilter();
        if (originalFilter == null || originalFilter.length == 0) {
            return true;
        }

        for (final String filterValue : originalFilter) {
            if (filterValue.equals(strongNumber)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes in a normal search query, and adapts the current search by rewriting the query syntax so that it
     * can be parsed by JSword
     *
     * @param sq the search query
     * @return a list of all matching strongs
     */
    private Set<String> adaptQueryForStrongSearch(final SearchQuery sq) {
        final Set<String> strongs = getStrongsFromTextCriteria(sq);

        final String textQuery = getQuerySyntaxForStrongs(strongs, sq);

        // we can now change the individual search query, to the real text search
        sq.getCurrentSearch().setQuery(textQuery);

        // return the strongs that the search will match
        return strongs;
    }

    /**
     * Adapts the search query to be used in a strong search
     *
     * @param sq the search query object
     * @return a list of strong numbers
     */
    private Set<String> adaptQueryForRelatedStrongSearch(final SearchQuery sq) {
        final Set<String> strongsFromQuery = getStrongsFromTextCriteria(sq);

        // look up the related strong numbers
        final Set<String> filteredStrongs = new HashSet<String>(strongsFromQuery.size());
        final StringBuilder fullQuery = new StringBuilder(64);
        final QueryParser p = new QueryParser(Version.LUCENE_30, STRONG_NUMBER_FIELD,
                this.definitions.getAnalyzer());

        // get all words suggested in query
        final String query = retrieveStrongs(strongsFromQuery);
        final EntityDoc[] results = retrieveStrongDefinitions(sq, filteredStrongs, p, query, fullQuery);

        // now get all related words:
        final String relatedQuery = getRelatedStrongQuery(results);
        final EntityDoc[] relatedResults = retrieveStrongDefinitions(sq, filteredStrongs, p, relatedQuery,
                fullQuery);

        setUniqueConsideredDefinitions(sq, results, relatedResults);

        // append range to query
        final String mainRange = sq.getCurrentSearch().getMainRange();
        if (isNotBlank(mainRange)) {
            fullQuery.insert(0, mainRange);
        }

        sq.getCurrentSearch().setQuery(fullQuery.toString().toLowerCase());
        return filteredStrongs;
    }

    /**
     * Sets up all definitions that are related, regardless of whether they have been filtered out. Makes a
     * unique set of these
     *
     * @param sq             the search query that is being
     * @param results        the results from the direct strong search
     * @param relatedResults the related results
     */
    private void setUniqueConsideredDefinitions(final SearchQuery sq, final EntityDoc[] results,
                                                final EntityDoc[] relatedResults) {
        // make entity docs unique:
        final Set<EntityDoc> joinedDocs = new HashSet<EntityDoc>(relatedResults.length + results.length);
        final Set<String> strongNumbers = new HashSet<String>(relatedResults.length + results.length);
        for (final EntityDoc queryDoc : results) {
            final String strong = queryDoc.get(STRONG_NUMBER_FIELD);
            if (!strongNumbers.contains(strong)) {
                joinedDocs.add(queryDoc);
                strongNumbers.add(strong);
            }
        }

        for (final EntityDoc relatedDoc : relatedResults) {
            final String strong = relatedDoc.get(STRONG_NUMBER_FIELD);
            if (!strongNumbers.contains(strong)) {
                joinedDocs.add(relatedDoc);
                strongNumbers.add(strong);
            }
        }
        sq.setDefinitions(new ArrayList<EntityDoc>(joinedDocs));
    }

    /**
     * Builds a query that gets the related-strong number entity documents
     *
     * @param results the source numbers
     * @return the query that gets the related numbers
     */
    private String getRelatedStrongQuery(final EntityDoc[] results) {
        final StringBuilder relatedQuery = new StringBuilder(results.length * 7);
        relatedQuery.append("-stopWord:true ");
        for (final EntityDoc doc : results) {
            relatedQuery.append(doc.get("relatedNumbers").replace(',', ' '));
        }
        return relatedQuery.toString();
    }

    /**
     * Retrieves the correct entity documents from a built up query and passed in parser
     *
     * @param sq              the search query
     * @param filteredStrongs the list of filtered strongs so far
     * @param p               the parser
     * @param query           the query
     * @param fullQuery       the full query so far
     * @return the list of matched entity documents
     */
    private EntityDoc[] retrieveStrongDefinitions(final SearchQuery sq, final Set<String> filteredStrongs,
                                                  final QueryParser p, final String query, final StringBuilder fullQuery) {
        Query q;
        try {
            q = p.parse(query);
        } catch (final ParseException e) {
            throw new TranslatedException(e, "search_invalid");
        }
        final EntityDoc[] results = this.definitions.search(q);

        for (final EntityDoc doc : results) {
            // remove from matched strong if not in filter
            final String strongNumber = doc.get(STRONG_NUMBER_FIELD);
            if (isInFilter(strongNumber, sq)) {
                filteredStrongs.add(strongNumber);
                fullQuery.append(STRONG_QUERY);
                fullQuery.append(strongNumber);
                fullQuery.append(' ');
            }
        }
        return results;
    }

    /**
     * Searches the underlying DB for the relevant entry
     *
     * @param searchQuery the query that is being passed in
     * @return the list of strongs matched
     */
    private Set<String> searchTextFieldsForDefinition(final String searchQuery) {
        // first look through the text forms
        final EntityDoc[] results = this.specificForms.search(new String[]{"accentedUnicode"},
                searchQuery, null, null, false, "-stopWord:false");
        if (results.length == 0) {
            return lookupFromLexicon(searchQuery);
        }

        // if we matched more than one, then we don't have our assumed uniqueness... log warning and
        // continue with first matched strong
        final Set<String> listOfStrongs = new HashSet<String>();
        for (final EntityDoc f : results) {
            listOfStrongs.add(f.get(STRONG_NUMBER_FIELD));
        }
        return listOfStrongs;
    }

    /**
     * Looks up the search criteria from the lexicon
     *
     * @param query the query
     * @return a list of strong numbers
     */
    private Set<String> lookupFromLexicon(final String query) {
        // if we still have nothing, then look through the definitions
        final QueryParser parser = new QueryParser(Version.LUCENE_30, "accentedUnicode",
                this.definitions.getAnalyzer());
        Query parsed;
        try {
            parsed = parser.parse(QueryParser.escape(query));
        } catch (final ParseException e) {
            throw new TranslatedException(e, "search_invalid");
        }

        final EntityDoc[] results = this.definitions.search(parsed);

        final Set<String> matchedStrongs = new HashSet<String>();
        for (final EntityDoc d : results) {
            matchedStrongs.add(d.get(STRONG_NUMBER_FIELD));
        }

        return matchedStrongs;
    }

    /**
     * removes accents, hebrew vowels, etc.
     *
     * @param query query
     * @param sq    the current query criteria
     * @return the unaccented string
     */
    private String unaccent(final String query, final SearchQuery sq) {
        final SearchType currentSearchType = sq.getCurrentSearch().getType();
        switch (currentSearchType) {
            case ORIGINAL_GREEK_EXACT:
            case ORIGINAL_GREEK_FORMS:
            case ORIGINAL_GREEK_RELATED:
                return StringConversionUtils.unAccent(query, true);
            case ORIGINAL_HEBREW_EXACT:
            case ORIGINAL_HEBREW_FORMS:
            case ORIGINAL_HEBREW_RELATED:
                return StringConversionUtils.unAccent(query, false);

            default:
                return query;
        }
    }

    /**
     * Runs the transliteration rules on the input in an attempt to match an entry in the lexicon
     *
     * @param query   the query to be found
     * @param isGreek true to indicate Greek, false to indicate Hebrew
     * @return the strongs that have been found/matched.
     */
    private Set<String> findByTransliteration(final String query, final boolean isGreek) {
        // first find by transliterations that we have
        final String lowerQuery = query.toLowerCase(Locale.ENGLISH);

        final String simplifiedTransliteration = OriginalWordSuggestionServiceImpl
                .getSimplifiedTransliterationClause(isGreek, lowerQuery, false);

        final EntityDoc[] specificFormEntities = this.specificForms.searchSingleColumn(
                "simplifiedTransliteration", simplifiedTransliteration, getFilter(isGreek));

        // finally, if we haven't found anything, then abort
        if (specificFormEntities.length != 0) {
            final Set<String> strongs = new HashSet<String>(specificFormEntities.length);
            // nothing to search for..., so abort query
            for (final EntityDoc f : specificFormEntities) {
                strongs.add(f.get(STRONG_NUMBER_FIELD));
            }
            return strongs;
        }

        final MultiFieldQueryParser queryParser = new MultiFieldQueryParser(Version.LUCENE_30, new String[]{
                "simplifiedTransliteration", "stepTransliteration", "otherTransliteration"},
                this.definitions.getAnalyzer());

        try {
            final Query luceneQuery = queryParser.parse("-stopWord:true " + lowerQuery);
            final EntityDoc[] results = this.definitions.search(luceneQuery);

            if (results.length == 0) {
                throw new AbortQueryException("No definitions found for input");
            }
            final Set<String> strongs = new HashSet<String>(results.length);
            for (final EntityDoc d : results) {
                strongs.add(d.get(STRONG_NUMBER_FIELD));
            }
            return strongs;
        } catch (final ParseException e) {
            throw new TranslatedException(e, "search_invalid");
        }

    }

    /**
     * splits up the query syntax and returns a list of all strong numbers required
     *
     * @param sq the search query
     * @return the list of strongs
     */
    private Set<String> getStrongsFromCurrentSearch(final SearchQuery sq) {
        final IndividualSearch currentSearch = sq.getCurrentSearch();
        final String searchStrong = currentSearch.getQuery();

        LOGGER.debug("Searching for strongs [{}]", searchStrong);
        return splitToStrongs(searchStrong, sq.getCurrentSearch().getType());
    }

    /**
     * Runs a timeline description search
     *
     * @param sq the search query
     * @return the search results
     */
    private SearchResult runTimelineDescriptionSearch(final SearchQuery sq) {
        return buildTimelineSearchResults(sq,
                this.timelineEvents.searchSingleColumn("name", sq.getCurrentSearch().getQuery()));
    }

    /**
     * Runs a timeline search, keyed by reference
     *
     * @param sq the search query
     * @return the search results
     */
    private SearchResult runTimelineReferenceSearch(final SearchQuery sq) {
        final EntityDoc[] events = this.timeline.lookupEventsMatchingReference(sq.getCurrentSearch()
                .getQuery());
        return buildTimelineSearchResults(sq, events);
    }

    /**
     * Construct the relevant entity structure to represent timeline search results
     *
     * @param sq     the search query
     * @param events the list of events retrieved
     * @return the search results
     */
    private SearchResult buildTimelineSearchResults(final SearchQuery sq, final EntityDoc[] events) {
        final List<SearchEntry> results = new ArrayList<SearchEntry>();
        final SearchResult r = new SearchResult();
        r.setResults(results);

        for (final EntityDoc e : events) {
            final String refs = e.get("storedReferences");
            final String[] references = StringUtils.split(refs);

            final List<VerseSearchEntry> verses = new ArrayList<VerseSearchEntry>();

            // TODO FIXME: REFACTOR to only make 1 jsword call?
            for (final String ref : references) {
                // TODO: REFACTOR only supports one version lookup
//                final OsisWrapper peakOsisText = this.jsword.peakOsisText(
//                        sq.getCurrentSearch().getVersions()[0], TimelineService.KEYED_REFERENCE_VERSION, ref);

                final VerseSearchEntry verseEntry = new VerseSearchEntry();
//                verseEntry.setKey(peakOsisText.getReference());
//                verseEntry.setPreview(peakOsisText.getValue());
                verses.add(verseEntry);
            }

            final TimelineEventSearchEntry entry = new TimelineEventSearchEntry();
            entry.setId(e.get("id"));
            entry.setDescription(e.get("name"));
            entry.setVerses(verses);
            results.add(entry);
        }
        return r;
    }

    /**
     * @param strongs a list of strongs
     * @param sq      the current search criteria containing the range of interest
     * @return the query syntax
     */
    private String getQuerySyntaxForStrongs(final Set<String> strongs, final SearchQuery sq) {
        final StringBuilder query = new StringBuilder(64);

        // adding a space in front in case we prepend a range
        query.append(' ');
        for (final String s : strongs) {
            query.append(STRONG_QUERY);
            query.append(s);
            query.append(' ');
        }

        final String mainRange = sq.getCurrentSearch().getMainRange();
        if (isNotBlank(mainRange)) {
            query.insert(0, mainRange);

        }
        return query.toString().trim().toLowerCase();
    }

    /**
     * Gets a query that retrieves a list of strong numbers. This query is used agains the lexicon definitions
     * lucene index, not the JSword-managed Bibles
     *
     * @param strongsFromQuery the strong numbers to select
     * @return a query looking up all the Strong numbers
     */
    private String retrieveStrongs(final Set<String> strongsFromQuery) {
        final StringBuilder query = new StringBuilder(strongsFromQuery.size() * 6 + 16);
        query.append("-stopWord:true ");
        for (final String strong : strongsFromQuery) {
            query.append(strong);
            query.append(' ');
        }
        return query.toString();
    }

    /**
     * Parses the search query, returned in upper case in case a database lookup is required
     *
     * @param searchStrong the search query
     * @param searchType   type of search, this includes greek vs hebrew...
     * @return the list of strongs
     */
    private Set<String> splitToStrongs(final String searchStrong, final SearchType searchType) {
        final List<String> strongs = Arrays.asList(searchStrong.split("[, ;]+"));
        final Set<String> strongList = new HashSet<String>();
        for (final String s : strongs) {
            final String prefixedStrong = isDigit(s.charAt(0)) ? getPrefixed(s, searchType) : s;
            strongList.add(padStrongNumber(prefixedStrong.toUpperCase(Locale.ENGLISH), false));
        }
        return strongList;
    }

    /**
     * @param s          the string to add a prefix to
     * @param searchType the type of search
     * @return the prefixed string with H/G
     */
    private String getPrefixed(final String s, final SearchType searchType) {
        switch (searchType) {
            case ORIGINAL_GREEK_EXACT:
            case ORIGINAL_GREEK_FORMS:
            case ORIGINAL_GREEK_RELATED:
                return 'G' + s;
            case ORIGINAL_HEBREW_EXACT:
            case ORIGINAL_HEBREW_FORMS:
            case ORIGINAL_HEBREW_RELATED:
                return 'H' + s;
            default:
                return null;

        }
    }

    /**
     * Builds the combined results
     *
     * @param sq      the search query object
     * @param results the set of keys that have been retrieved by each search
     * @return the set of results
     */
    private SearchResult buildCombinedVerseBasedResults(final SearchQuery sq, final Key results) {

        // combine the results into 1 giant keyed map
        final IndividualSearch currentSearch = sq.getCurrentSearch();

        int total = results.getCardinality();
        final Key pagedKeys = this.jswordSearch.rankAndTrimResults(sq, results);
        
        // retrieve scripture content and set up basics
        final SearchResult resultsForKeys = this.jswordSearch.getResultsFromTrimmedKeys(sq, currentSearch.getVersions(), total, pagedKeys);
        resultsForKeys.setTotal(this.jswordSearch.getTotal(results));
        resultsForKeys.setQuery(sq.getOriginalQuery());
        return resultsForKeys;
    }

    /**
     * Keeps keys of "results" where they are also in searchKeys
     *
     * @param results    the existing results that have already been obtained. If null, then searchKeys is
     *                   returned
     * @param searchKeys the search keys of the current search
     * @return the intersection of both Keys, or searchKeys if results is null
     */
    private Key intersect(final Key results, final Key searchKeys) {
        if (results == null) {
            return searchKeys;
        }

        // otherwise we interesect and adjust the "total"
        results.retainAll(searchKeys);
        return results;
    }
}
