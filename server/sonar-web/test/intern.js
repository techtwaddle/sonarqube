define({

  environments: [
    { browserName: 'firefox' },
    { browserName: 'chrome' },
    { browserName: 'safari' },
    { browserName: 'internet explorer', version: '9' },
    { browserName: 'internet explorer', version: '10' },
    { browserName: 'internet explorer', version: '11' }
  ],

  tunnel: 'BrowserStackTunnel',

  excludeInstrumentation: /(test|third-party|node_modules)\//,

  reporters: [
    { id: 'Runner' },
    { id: 'Lcov' },
    { id: 'LcovHtml', directory: 'target/web-tests/unit' }
  ],

  suites: [
    'test/unit/application.spec'
  ],

  functionalSuites: [
    'test/medium/users.spec'
  ]

});
