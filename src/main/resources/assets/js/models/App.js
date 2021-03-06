define([
  "Backbone",
  "Underscore",
  "models/TaskCollection"
], function(Backbone, _, TaskCollection) {
  return Backbone.Model.extend({
    defaults: function() {
      return {
        cmd: null,
        constraints: [],
        cpus: 0.1,
        id: _.uniqueId("app_"),
        instances: 1,
        mem: 10.0,
        uris: []
      };
    },
    initialize: function(options) {
      // If this model belongs to a collection when it is instantiated, it has
      // already been persisted to the server.
      this.persisted = (this.collection != null);

      this.tasks = new TaskCollection(null, {appId: this.id});
      this.on({
        "change:id": function(model, value, options) {
          // Inform TaskCollection of new ID so it can send requests to the new
          // endpoint.
          this.tasks.options.appId = value;
        },
        "sync": function(model, response, options) {
          this.persisted = true;
        }
      });
    },
    isNew: function() {
      return !this.persisted;
    },
    validate: function(attrs, options) {
      var errors = [];

      if (_.isNaN(attrs.mem) || !_.isNumber(attrs.mem) || attrs.mem < 0) {
        errors.push("Memory must be a non-negative Number");
      }

      if (_.isNaN(attrs.cpus) || !_.isNumber(attrs.cpus) || attrs.cpus < 0) {
        errors.push("CPUs must be a non-negative Number");
      }

      if (_.isNaN(attrs.instances) || !_.isNumber(attrs.instances) ||
          attrs.instances < 0) {
        errors.push("Instances must be a non-negative Number");
      }

      if (!_.isString(attrs.id) || attrs.id.length < 1) {
        errors.push("ID must be a non-empty String");
      }

      if (!_.isString(attrs.cmd) || attrs.cmd.length < 1) {
        // Prevent erroring out on UPDATE operations like scale/suspend. 
        // If cmd string is empty, then don't error out, if an executor and container are provide.
        if (!_.isString(attrs.executor) || attrs.executor.length < 1 || !attrs.container || !_.isString(attrs.container.image) || attrs.container.image.length < 1 || attrs.container.image.indexOf('docker') != 0) {
          errors.push("Cmd must be a non-empty String, if executor and container image are not provided");
        }
      }

      if (errors.length > 0) return errors;
    }
  });
});
