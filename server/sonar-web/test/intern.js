define({

  environments: [
    { browserName: 'firefox', tunnelIdentifier: process.env['TRAVIS_JOB_NUMBER'] }
  ],

  tunnel: 'SauceLabsTunnel',

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
