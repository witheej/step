var PassageModel = Backbone.Model.extend({
        defaults: function () {
            return {
                passageId: 0,
                version: "KJV",
                reference: "Mat 1",
                extraVersions: [],
                interlinearMode: "NONE",
                detailLevel: 0,
                options: []
            }
        },

        /**
         * Validates the attributes before they get saved to local storage
         * @param attributes the attributes we are saving
         * @returns an error or undefined if not.
         */
        validate: function (attributes) {
            return this._validateInterlinearMode(attributes.interlinearMode);
        },

        /**
         * we ensure that, A- we save the correct format of parameters, and B- that we save options that are valid
         * @param attributes the set of attributes to be stored
         * @param options the options to be saved
         * @returns {Function|o.save}
         */
        save: function (attributes, options) {
            //if either attribute is specified, then we definitely want to check interlinear mode
            if (attributes.interlinearMode || attributes.extraVersions) {
                var validInterlinearMode = this._getBestInterlinearMode(attributes.interlinearMode);
                var validExtraVersions = attributes.extraVersions || this.get("extraVersions");
                attributes.interlinearMode = this._getSafeInterlinearMode(validInterlinearMode, validExtraVersions);
            }

            if (attributes.extraVersions) {
                attributes.extraVersions = this._getSafeExtraVersions(attributes.extraVersions);
            }

            if(attributes.detailLevel) {
                attributes.detailLevel = parseInt(attributes.detailLevel);
            }

            console.log("Saving model passage", attributes);
            return Backbone.Model.prototype.save.call(this, attributes, options);
        },

        /**
         * Get interlinear mode, which depends on the detail level that is currently set.
         * @param interlinearMode the current interlinear mode, taken from the model
         * @param detailLevel the detail level
         * @returns {string} the interlinear mode
         * @private
         */
        _getProxiedInterlinearMode: function (interlinearMode, detailLevel) {
            if (detailLevel == 0) {
                //no interleaved interlinear on this side...
                return "NONE";
            } else if (detailLevel == 1) {
                //default to interlinear if we can, if not interlinear
                //prefer Interlinear to the other interleaved
                return this._getSafeInterlinearMode("INTERLINEAR", this.get("extraVersions"));
            } else {
                return this._getSafeInterlinearMode(interlinearMode, this.get("extraVersions"));
            }
        },

        /**
         * The proxied version of extra versions
         * @param extraVersions the current value
         * @param detailLevel the current level of detail
         * @returns {string} the extra versions filtered by the level detail.
         */
        getProxiedExtraVersions: function (extraVersions, detailLevel) {
            if (detailLevel == 0) {
                return "";
            }
            return extraVersions;
        },
        /**
         * We override the default functionality, since the detail affects the values returned
         * @param attributeName the name of the attribute we are wanting to return
         * @returns {string} the value of the attribute, taking account of the level of detail
         */
        get: function (attributeName) {
            var value = Backbone.Model.prototype.get.call(this, attributeName);

            if (attributeName == "interlinearMode") {
                return this._getProxiedInterlinearMode(value, this.get("detailLevel"));
            }

            if (attributeName == "extraVersions") {
                return this.getProxiedExtraVersions(value, this.get("detailLevel"));
            }

            return value;
        },

        /**
         * Simply returns the value if populated, otherwise returns the model value
         * @param potentialInterlinearMode the potential interlinear mode value
         * @private
         */
        _getBestInterlinearMode: function (potentialInterlinearMode) {
            if (potentialInterlinearMode == undefined || potentialInterlinearMode == "" || potentialInterlinearMode == "NONE") {
                return this.get("interlinearMode");
            }
            return potentialInterlinearMode;
        },

        /**
         * Returns the options that are available, with the current state of the extraVersions field
         * @returns {Array}, either empty, or with the right set of versions (i.e. including/excluding the interlinear Option)
         */
        getAvailableInterlinearOptions: function () {
            var extraVersions = this.get("extraVersions");
            if (extraVersions == undefined || extraVersions.length == 0) {
                return [];
            }

            if (this._hasStronglessVersion(extraVersions)) {
                return step.defaults.passage.interOptionsNoInterlinear;
            }
            return step.defaults.passage.interOptions;
        },

        /**
         * Gets the internationalised version of the interlinear mode value
         * @returns {string} the value to be displayed in a view
         */
        getInternationalisedInterlinearMode: function () {
            var currentValue = this.get("interlinearMode");
            if (currentValue == undefined || currentValue == "") {
                return "";
            }

            var nameIndex = step.defaults.passage.interNamedOptions.indexOf(currentValue);
            if (nameIndex == -1) {
                return "";
            }
            return step.defaults.passage.interOptions[nameIndex];
        },

        getPassageLocation: function () {
            var options = this.get("options") || [];
            var extraVersions = this.get("extraVersions") || [];
            var interlinearMode = this.get("interlinearMode") || "";

            var url = ["passage", this.get("passageId"), this.get("detailLevel"), this.get("version"), this.get("reference"),
                options.join(), extraVersions.join(), interlinearMode];

            //pop off the bits that aren't used...
            var urlTokens = url.length;
            for(var ii = urlTokens -1; ii > 0; ii--) {
                if(url[ii] == "" || (ii == urlTokens -1 && url[ii] == "NONE")) {
                    url.pop();
                } else {
                    break;
                }
            }

            return url.join("/");
        },

        /**
         * If given a String, we return an array, split by commas
         * @param extraVersions the array or string of versions we are storing.
         * @returns Array array array of versions
         * @private
         */
        _getSafeExtraVersions: function (extraVersions) {
            if (extraVersions == undefined) {
                return [];
            }

            if (_.isString(extraVersions) && !_.isEmpty(extraVersions)) {
                return extraVersions.split(",");
            }
            return extraVersions;
        },

        /**
         * If one of the versions does not have strong, we return INTERLEAVED, otherwise INTERLINEAR
         * @param hasStronglessVersion true to indicate one of the versions does not have strong
         * @returns INTERLEAVED or INTERLINEAR
         * @private
         */
        _getDefaultInterlinearModeOption: function (hasStronglessVersion) {
            return hasStronglessVersion ? "INTERLEAVED" : "INTERLINEAR";
        },

        /**
         * Runs the logic, which essentially, in a nutshell, says: If the value is correct,
         * then check if it is available. For example, INTERLINEAR is only available
         * if all versions are part of the interlinear.
         *
         * @param interlinearMode the interlinear mode we are attempting to store
         * @param extraVersions the versions that have been selected
         * @private
         */
        _getSafeInterlinearMode: function (interlinearMode, extraVersions) {
            //if we have no extra versions, then we're not going to interleaving or interlinearing...
            if (extraVersions == undefined || extraVersions.length == 0) {
                return "NONE";
            }

            var hasStronglessVersion = this._hasStronglessVersion(extraVersions);

            //so we do have some versions, therefore, check if an option has been selected
            if (interlinearMode == undefined || interlinearMode == "" || interlinearMode == "NONE") {
                //no option selected, so we return one of two defaults...
                this._getDefaultInterlinearModeOption(hasStronglessVersion);
            }

            //we reach here, if both an interlinear mode has been selected and some extra versions.
            //there is one more case where we need to override the value provide, and that is
            //if we are selecting INTERLINEAR in a non-interlinear version...
            var finalValue;
            var indexInNames = step.defaults.passage.interOptions.indexOf(interlinearMode);
            if (indexInNames == -1) {
                //no name found, so try the values array
                if (step.defaults.passage.interNamedOptions.indexOf(interlinearMode) == -1) {
                    //no name found here either, so override the value to whatever is the best default
                    return this._getDefaultInterlinearModeOption(hasStronglessVersion);
                } else {
                    //so proceed with this value
                    finalValue = interlinearMode;
                }
            } else {
                //the value is internationalised, so let's take the real one instead.
                finalValue = step.defaults.passage.interNamedOptions[indexInNames];
            }

            //so, we have found a value and it is valid, we also have versions
            //so we simply check whether the value is "INTERLINEAR" and if so, then we override it
            //if we are talking non-strong versions
            if (finalValue == "INTERLINEAR" && hasStronglessVersion) {
                return "INTERLEAVED";
            }

            //finally, we've checked final value is correct, so simply return it!
            return finalValue;
        },

        /**
         * Returns true if any of the versions passed in does not contain strong numbers
         * @param comparisonVersions the list of comparison versions
         * @return true if at least one version does not contain strong numbers
         * @private
         */
        _hasStronglessVersion: function (comparisonVersions) {
            var allVersions = [this.get("version")];
            allVersions = allVersions.concat(comparisonVersions.slice(0));

            for (var i = 0; i < allVersions.length; i++) {
                //check that each version contains strongs
                var features = this._getSelectedVersion(allVersions[i]);

                //if at least one of the versions doesn't have Strongs, then we can't do interlinears.
                if (features && !features.hasStrongs) {
                    return true;
                }
            }
            return false;
        },

        /**
         * Gets all the information possible from the selected version
         * @param versionName the name of the version we are after
         * @returns the version information
         * @private
         */
        _getSelectedVersion: function (versionName) {
            return step.keyedVersions[versionName.toUpperCase()];
        },

        _validateInterlinearMode: function (interlinearMode) {
            var option = interlinearMode;

            if (!interlinearMode || interlinearMode == "NONE") {
                return undefined;
            }

            //check valid option first
            var indexOfName = step.defaults.passage.interNamedOptions.indexOf(interlinearMode);
            if (indexOfName == -1) {
                step.util.raiseInfo(this.get("passageId"), "This is not a valid option.");
                return "This is not a valid option";
            }
            return undefined;
        }
    })
    ;



