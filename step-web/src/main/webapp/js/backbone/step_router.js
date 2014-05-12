var StepRouter = Backbone.Router.extend({
    routes: {
        "search(?:query)": "doMasterSearch",
        "(?:query)": "doMasterSearch"
    },
    initialize: function () {
    },
    _addArg: function (url, argName, argValue) {
        if (url == "") {
            url += '?';
        } else if (url[url.length - 1] != '?') {
            url += '&';
        }
        url += argName;
        if (argValue != null) {
            url = url + "=" + argValue;
        }
        return url;
    },
    overwriteUrl: function (inputUrl) {
        var url = inputUrl || Backbone.history.fragment || "";
        this.navigate(url, { trigger: false, replace: true});
    },

    navigatePreserveVersions: function (partial, stripCommentaries) {
        //get versions of current active passage
        //add versions from current active passage
        var activePassage = step.util.activePassage();

        var extra = partial;
        var mainVersion = activePassage.get("masterVersion");
        if (mainVersion != "") {
            if (!stripCommentaries || step.keyedVersions[mainVersion].category == 'BIBLE') {
                extra += "|version=" + mainVersion;
            }

            var extraVersions = (activePassage.get("extraVersions") || "").split(",");
            for (var i = 0; i < extraVersions.length; i++) {
                if ((extraVersions[i] || "") != "") {
                    if (!stripCommentaries || step.keyedVersions[extraVersions[i]].category == 'BIBLE') {
                        extra += "|version=" + extraVersions[i];
                    }
                }
            }
        }
        this.navigateSearch(extra);
    },
    navigateSearch: function (args, historyOptions) {
        var activePassageId = step.util.activePassageId();
        var activePassageModel = step.passages.findWhere({ passageId: activePassageId});
        var options = activePassageModel.get("selectedOptions") || "";
        var interlinearMode = activePassageModel.get("interlinearMode") || "";
        var pageNumber = activePassageModel.get("pageNumber");
        var context = activePassageModel.get("context");
        var filter = activePassageModel.get("strongHighlights");
        var sort = activePassageModel.get("order");
        var position = activePassageModel.get("passageId");

        if (step.util.isBlank(context)) {
            activePassageModel.set({context: 0 }, { silent: true });
            context = 0;
        }
        var urlStub = "";

        if (step.util.isBlank(args) && (!historyOptions || !historyOptions.replace)) {
            var modelArgs = activePassageModel.get("args") || "";
            urlStub = this._addArg(urlStub, "q", modelArgs);
        } else {
            urlStub = this._addArg(urlStub, "q", args);
        }

        if (!step.util.isBlank(options)) {
            urlStub = this._addArg(urlStub, "options", options);
        }
        if (!step.util.isBlank(interlinearMode) && interlinearMode != "NONE") {
            urlStub = this._addArg(urlStub, "display", interlinearMode);
        }
        if (!step.util.isBlank(pageNumber) && pageNumber != "1") {
            urlStub = this._addArg(urlStub, "page", pageNumber);
        }
        if (context != 0) {
            urlStub = this._addArg(urlStub, "context", context);
        }
        if (!step.util.isBlank(filter)) {
            urlStub = this._addArg(urlStub, "qFilter", filter);
        }
        if (!step.util.isBlank(sort)) {
            urlStub = this._addArg(urlStub, "sort", sort);
        }

        if (position != 0) {
            urlStub = this._addArg(urlStub, "pos", position);
        }

        if ($.getUrlVars().indexOf("debug") != -1) {
            urlStub = this._addArg(urlStub, "debug");
        }

        if (!historyOptions) {
            historyOptions = { trigger: true};
        }

        //we will get a null-arg as part of the replacing of the URL with the correct URL
        //call back from after the routing call to rest backend call. So need
        //to avoid writing over 'args'
        if (args != null) {
            activePassageModel.save({ args: decodeURIComponent(args) }, { silent: true });
        }
        this.navigate(urlStub, historyOptions);
    },
    getShareableColumnUrl: function (passageId) {
        var shareableUrl = "http://www.stepbible.org/";
        if (passageId == null) {
            return shareableUrl;
        }

        var passageModel = step.passages.findWhere({passageId: passageId});
        if (!passageModel) {
            return shareableUrl;
        }

        var fragment = passageModel.get("urlFragment");
        return shareableUrl + fragment;
    },
    handleSearchResults: function (passageModel, partRendered) {
        require(["search", "defaults"], function (module) {
            if (passageModel.get("pageNumber") > 1) {
                passageModel.trigger("newPage");
            } else {
                passageModel.trigger("destroyViews");
                switch (passageModel.get("searchType")) {
                    case "TEXT":
                    case "RELATED_VERSES":
                        new TextDisplayView({
                            model: passageModel,
                            partRendered: partRendered
                        });
                        break;
                    case "SUBJECT_SIMPLE" :
                    case "SUBJECT_EXTENDED" :
                    case "SUBJECT_FULL" :
                    case "SUBJECT_RELATED" :
                        new SubjectDisplayView({
                            model: passageModel,
                            partRendered: partRendered
                        });
                        break;
                    case "ORIGINAL_MEANING" :
                    case "EXACT_FORM" :
                    case "ORIGINAL_GREEK_FORMS" :
                    case "ORIGINAL_GREEK_RELATED" :
                    case "ORIGINAL_HEBREW_EXACT" :
                    case "ORIGINAL_HEBREW_FORMS" :
                    case "ORIGINAL_HEBREW_RELATED":
                        new WordDisplayView({
                            model: passageModel,
                            partRendered: partRendered
                        });
                        break;
                }
            }
        });
    },
    handleRenderModel: function (passageModel, partRendered, queryArgs, totalTime) {
        var startRender = new Date().getTime();
        passageModel.save({
                args: queryArgs != null ? decodeURIComponent(queryArgs) : "",
                urlFragment: Backbone.history.getFragment()
            },
            {
                silent: true
            }
        );

        //then trigger the refresh of menu options and such like
        passageModel.trigger("sync-update", passageModel);

        var searchType = passageModel.get("searchType");
        if (searchType == 'PASSAGE') {
            //destroy all views for this column
            passageModel.trigger("destroyViews");
            new PassageDisplayView({
                model: passageModel,
                partRendered: partRendered
            });
        } else {
            this.handleSearchResults(passageModel, partRendered);
        }

        this._renderSummary(passageModel);

        var endRender = new Date().getTime();
        var totalRender = endRender - startRender;
        if (totalTime != -1) {
            step.util.trackAnalytics("search", "renderTime", totalRender);
            step.util.trackAnalytics(searchType, "renderTime", totalRender);
            step.util.trackAnalytics("search", "totalTime", totalTime + endRender - startRender);
            step.util.trackAnalytics(searchType, "totalTime", totalTime + endRender - startRender);
            step.util.trackAnalytics("search", "searchType", searchType);
            step.util.trackAnalytics("search", "masterVersion", passageModel.get("masterVersion"));

            if (passageModel.get("interlinearMode") != null) {
                step.util.trackAnalytics("search", "interlinearMode", passageModel.get("interlinearMode"));
            }

            if (searchType == 'PASSAGE') {
                step.util.trackAnalytics("search", "reference", passageModel.get("osisId"));
            } else {
                if (passageModel.get("query") != null) {
                    step.util.trackAnalytics("search", "query", passageModel.get("query"));
                }
            }
        }
    },

    _renderSummary: function (passageModel) {
        var searchTokens = passageModel.get("searchTokens");
        var container = $("<span></span>").addClass("argSummary");
        step.util.ui.renderArgs(searchTokens, container);
        var passageOptions = step.util.getPassageContainer(passageModel.get("passageId")).find(".passageOptionsGroup");
        passageOptions.find(".argSummary").remove();
        passageOptions.append(container);
    },
    doMasterSearch: function (query, options, display, pageNumber, filter, sort, context, quiet) {
        var self = this;
        if (step.util.isBlank(query)) {
            //assume URL parameters
            query = $.getUrlVar("q") || "";
            options = $.getUrlVar("options");
            display = $.getUrlVar("display");
            filter = $.getUrlVar("qFilter");
            context = $.getUrlVar("context");
            sort = $.getUrlVar("sort");
        }

        var args = query.split("&");
        if (args == null || args.length == 0) {
            return;
        }

        for (var i = 0; i < args.length; i++) {
            var myArgs = args[i];
            var splitElement = myArgs.split("=");
            if (splitElement.length < 2) {
                continue;
            }

            var key = splitElement[0];
            var value = splitElement.slice(1).join("=");
            switch (key) {
                case 'q':
                    query = value;
                    break;
                case 'options':
                    options = value;
                    break;
                case 'page':
                    pageNumber = value;
                    break;
                case 'display':
                    display = value;
                    break;
                case 'context':
                    context = value;
                    break;
                case 'qFilter':
                    filter = value;
                    break;
                case 'sort':
                    sort = value;
                    break;
            }
        }


        var startTime = new Date().getTime();
        var activePassageId = step.util.activePassageId();

        //remove debug if present
        query = encodeURIComponent(query.replace(/&debug/ig, ""));
        console.log(query, options, display, pageNumber, filter, sort, context);

        $.getPassageSafe({
            url: SEARCH_MASTER,
            args: [query, options, display, pageNumber, filter, sort, context],
            callback: function (text) {
                text.startTime = startTime;

                var searchType = text.searchType;
                var endTime = new Date().getTime();
                var serverTime = text.timeTookTotal;
                if (serverTime == null) {
                    serverTime = 0;
                }
                var totalSoFar = endTime - startTime;
                step.util.trackAnalytics("search", "serverTime", serverTime);
                step.util.trackAnalytics("search", "latency", totalSoFar - serverTime);
                step.util.trackAnalytics("search", "roundTrip", totalSoFar);

                if (searchType) {
                    step.util.trackAnalytics(searchType, "serverTime", serverTime);
                    step.util.trackAnalytics(searchType, "latency", totalSoFar - serverTime);
                    step.util.trackAnalytics(searchType, "roundTrip", totalSoFar);
                }

                step.util.unlinkThis(activePassageId);
                var passageModel = step.passages.findWhere({ passageId: activePassageId});
                if (passageModel == null) {
                    console.error("No passages defined for ", activePassageId);
                }

                passageModel.save(text, { silent: true });
                self._addBookmark({ args: query, searchTokens: text.searchTokens });
                step.util.squashErrors(passageModel);

                //don't trigger a full search, but replace the URL with the one that makes sense
                if (!quiet) {
                    step.router.overwriteUrl();
                }

                self.handleRenderModel(passageModel, false, query, totalSoFar);
            },
            passageId: activePassageId,
            level: 'error'
        });
    },
    _addBookmark: function (query) {
        var normalizedArgs = this._normalizeArgs(query.args);
        var existingModel = step.bookmarks.findWhere({ args: normalizedArgs });
        if (existingModel) {
            existingModel.save({ lastAccessed: new Date().getTime() });
            return;
        }

        var historyModel = new HistoryModel({
            args: normalizedArgs,
            lastAccessed: new Date().getTime(),
            searchTokens: query.searchTokens
        });
        step.bookmarks.add(historyModel);
        historyModel.save();
    },
    _normalizeArgs: function (args) {
        var tokens = (args || "").split("|") || [];
        tokens.sort(function (a, b) {
            var aTokens = a.split("=");
            var bTokens = b.split("=");
            var aKey = aTokens[0];
            var bKey = bTokens[0];

            if (aKey == bKey) {
                if (aKey == VERSION) {
                    return aTokens[1] < bTokens[1] ? -1 : 1;
                }
                return 0;
            } else if (aKey == VERSION) {
                return -1;
            } else if (bKey == VERSION) {
                return 1;
            } else if (aKey == REFERENCE) {
                return -1;
            } else if (bKey == REFERENCE) {
                return 1
            } else {
                //preserve the order so equal
                return 0;
            }
        });
        return tokens.join("|");
    }
});