define({

  environments: [
    { browserName: 'firefox' }
  ],

  tunnel: 'NullTunnel',

  excludeInstrumentation: /(test|third-party|node_modules)\//,

  reporters: [
    { id: 'Runner' },
    { id: 'LcovHtml', directory: 'target/js/lcov' }
  ],

  suites: [
    'test/unit/application.spec'
  ],

  functionalSuites: [
    'test/medium/users.spec'
  ]

});
