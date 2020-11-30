const deploy = require('./deploy');

deploy({
  s3Bucket: 'stg-app.hermeez.co',
  distributionId: 'redacted'
});
