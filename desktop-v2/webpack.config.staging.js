const webpack = require('webpack');
const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = {
  mode: 'development',

  devtool: 'eval',

  entry: [
    'babel-polyfill',
    './src/index'
  ],

  output: {
    path: `${__dirname}/build`,
    publicPath: '/',
    filename: 'bundle.js',
    pathinfo: true
  },

  optimization: {
    minimize: true
  },

  plugins: [
    new webpack.DefinePlugin({
      'process.env.NODE_ENV': '"staging"'
    }),
    new webpack.optimize.OccurrenceOrderPlugin(),
    new HtmlWebpackPlugin({
      template: 'src/index.html',
      inject: 'body'
    })
  ],


  resolve: {
    extensions: ['.js', '.jsx', '.scss'],
    alias: {
      '_config': `${__dirname}/config/config.staging.js`
    },
    modules: [path.resolve(__dirname, 'src'), 'node_modules']
  },

  module: {
    rules: [
      {
        test: /\.jsx?$/,
        loaders: ['babel-loader'],
        exclude: /node_modules/,
        include: [
          `${__dirname}/src`,
          `${__dirname}/config`
        ]
      },
      {
        test: /\.s?css$/,
        use: [
          {loader: 'style-loader'},
          {
            loader: 'css-loader',
            options: {
              modules: true,
              localIdentName: '[name]_[hash:base64:5]',
              sourceMap: true
            }
          },
          {loader: 'sass-loader'}
        ]
      },
      {
        test: /\.svg$/,
        loader: 'svg-inline-loader'
      }
    ]
  }
};
