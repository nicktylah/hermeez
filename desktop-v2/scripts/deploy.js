const AWS = require('aws-sdk');
const fs = require('fs');
const mime = require('mime');

const s3 = new AWS.S3({
  region: 'us-west-2'
});
const cloudfront = new AWS.CloudFront();

const BUILD_PATH = './build/';

function uploadFiles(bucket, files) {
  let count = 0;

  return new Promise(resolve => {
    files.forEach(filename => {
      const filePath = `${BUILD_PATH}/${filename}`;
      const stream = fs.createReadStream(filePath);
      const params = {
        Bucket: bucket,
        Key: filename,
        Body: stream,
        ACL: 'public-read',
        ContentType: mime.lookup(filePath)
      };

      console.log(`Uploading: ${filename}`);

      s3.upload(params, (err, data) => {
        if (err) console.log(`THEY DON'T WANT YOU TO PUBLISH! ${err}`);

        count++;
        console.log(`Finished: ${filename}`);

        if (files.length === count) {
          resolve();
        }
      });
    });
  });
}

function createInvalidation(distId, files) {
  if (!distId) {
    console.log('No distribution ID, skipping...');
    return Promise.resolve();
  }

  const params = {
    DistributionId: distId,
    InvalidationBatch: {
      CallerReference: Date.now().toString(),
      Paths: {
        Quantity: files.length,
        Items: files.map(f => `/${f}`)
      }
    }
  };

  console.log('Creating invalidation...');

  return new Promise(function(resolve, reject) {
    cloudfront.createInvalidation(params, function(err, data) {
      if (err) {
        reject(err);
      } else {
        resolve();
      }
    });
  });
}

module.exports = function(params) {
  const s3Bucket = params.s3Bucket;
  const distributionId = params.distributionId;

  const files = fs.readdirSync(BUILD_PATH);

  uploadFiles(s3Bucket, files)
    .then(function() {
      return createInvalidation(distributionId, files);
    })
    .then(function() {
      console.log('done');
    });
};
