'use strict'

let webpack = require('webpack');
let nodeExternals = require('webpack-node-externals');
const envUrls = require(`${__dirname}/../../utilities/env/urls`).development;

const env = {
  'process.env': {
    NODE_ENV: JSON.stringify('development')
  }
};

Object.keys(envUrls).forEach(function (key) {
  env['process.env'][key] = JSON.stringify(envUrls[key]);
});

module.exports = {
  devtool: 'eval',

  plugins: [
    new webpack.optimize.OccurenceOrderPlugin(),
    new webpack.DefinePlugin(env),
    new webpack.optimize.UglifyJsPlugin({
      compressor: {
        screw_ie8: true,
        warnings: false
      }
    })
  ],

  resolve: {
    extensions: ['', '.js', '.jsx'],
    alias: {
      '_actions': `${__dirname}/src/actions`,
      '_components': `${__dirname}/src/components`,
      '_reducers': `${__dirname}/src/reducers`,
      '_test': `${__dirname}/test.js`
    }
  },

  externals: [nodeExternals()],

  module: {
    loaders: [
      {
        test: /\.jsx?$/,
        loader: 'babel',
        exclude: /node_modules/
      },
      {
        test: /\.s?css$/,
        loader: 'css/locals?modules!sass',
        include: `${__dirname}/src`
      }
    ]
  }
};
