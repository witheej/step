var PassageCriteriaList = Backbone.Collection.extend({
    model: PassageModel,
    localStorage: new Backbone.LocalStorage("passage-criteria"),
    initialize: function () {
        this.on("change", this.changePassage, this);
    },
    changePassage: function (model, val, options) {
        console.log("Change to model", model)
        if (model != null) {
            stepRouter.navigatePassage(model.getLocation(), {trigger: true});
        } else {
            console.log("Model was null so can't route to a passage location")
        }
    }
});

var MenuList = Backbone.Collection.extend({
    model : SearchMenuModel,
    localStorage: new Backbone.LocalStorage("menu-criteria"),
    initialize: function () {
        this.on("change", this.triggerModelChange, this);
    },

    triggerModelChange: function (model, val, options) {
        console.log("Change to model", model)
        if (model != null) {
            var currentSearch = model.get("selectedSearch");
            if(currentSearch == 'SEARCH_PASSAGE') {
                var passageModel = PassageModels.at(model.get("passageId"));
                passageModel.trigger("change", passageModel);
            } else {
//                step.util.getPassageContent(0).html("");
                stepRouter.navigatePassage("somewhereelse");
            }
        } else {
            console.log("Model was null so can't route to a passage location")
        }
    }
});

var SubjectList = Backbone.Collection.extend({
    model : SubjectSearchModel,
    localStorage: new Backbone.LocalStorage("subject-criteria"),

    initialize: function () {
        this.on("search", this.triggerSearch, this);
        this.on("change", this.triggerModelChange, this);
    },

    triggerModelChange: function (model, val, options) {
        //estimate search
        console.log("Estimate search");
    },

    triggerSearch : function(model, val, options) {
        console.log("Trigger SUBJECT search");
        stepRouter.navigatePassage(model.getLocation(), {trigger: true});
    }

});



//var eventBus = _.extend({}, Backbone.Events);

var stepRouter;
var PassageModels;
var MenuModels;
var SubjectModels;

//var PassageCriterias = new PassageCriteriaList;
function initApp() {
    PassageModels = new PassageCriteriaList;
    MenuModels = new MenuList;
    SubjectModels = new SubjectList;

    PassageModels.fetch();
    MenuModels.fetch();
    SubjectModels.fetch();

    //create new router
    stepRouter = new StepRouter();

    //check if we've got any models yet...
    var passageIds = 2;
    var passageModels = [];
    for(var i = 0 ; i < passageIds; i++) {
        //if i is less than the length of the stored models, then it means we already have a model
        var passageModel = i < PassageModels.length ? PassageModels.at(i) : new PassageModel({ passageId: i });
        var searchModel = i < MenuModels.length ? MenuModels.at(i) : new SearchMenuModel({ passageId : i});
        var subjectModel = i < SubjectModels.length ? SubjectModels.at(i) : new SubjectSearchModel({ passageId : i });

        var passageCriteria = new PassageCriteriaView({ model: passageModel });
        var passageDisplay = new PassageDisplayView({ model: passageModel });

        //menus
        var menu = new PassageMenuView({ model: passageModel });
        var searchMenu = new SearchMenuView({ model: searchModel});

        //control of layout
        var criteriaControlView = new CriteriaControlView({ model : searchModel });

        //subject search
        var subjectCriteria = new SubjectCriteria({ model : subjectModel });
        var subjectView = new SubjectDisplayView({ model : subjectModel });

        //add to collections to store state locally
        PassageModels.add(passageModel);
        MenuModels.add(searchModel);
        SubjectModels.add(subjectModel);

        passageModels.push(passageModel);
    }

    Backbone.history.start();

//    //trigger changes
//    for(var i = PassageModels.length -1; i >= 0 ; i--) {
//        var model = PassageModels.at(i);
//        model.trigger("change", model);
//    }
}