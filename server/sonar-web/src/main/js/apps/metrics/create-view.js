define([
  './metric',
  './form-view'
], function (Metric, FormView) {

  return FormView.extend({

    sendRequest: function () {
      var that = this,
          metric = new Metric({
            key: this.$('#create-metric-key').val(),
            name: this.$('#create-metric-name').val(),
            description: this.$('#create-metric-description').val(),
            domain: this.$('#create-metric-domain').val(),
            type: this.$('#create-metric-type').val()
          });
      this.disableForm();
      return metric.save(null, {
        statusCode: {
          // do not show global error
          400: null
        }
      }).done(function () {
        that.collection.refresh();
        if (that.options.domains.indexOf(metric.get('domain')) === -1) {
          that.options.domains.push(metric.get('domain'));
        }
        that.destroy();
      }).fail(function (jqXHR) {
        that.enableForm();
        that.showErrors([{ msg: jqXHR.responseJSON.err_msg }]);
      });
    }
  });

});
