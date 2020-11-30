const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const FlowWebpackPlugin = require('flow-webpack-plugin');

const env = {
  'process.env': {
    NODE_ENV: JSON.stringify('development')
  }
};

module.exports = {
  mode: 'development',

  devtool: 'eval',

  entry: [
    'babel-polyfill',
    'react-hot-loader/patch',
    'webpack-dev-server/client?http://localhost:2007',
    'webpack/hot/only-dev-server',
    './src'
  ],

  output: {
    path: `${__dirname}/src`,
    filename: 'bundle.js',
    publicPath: '/'
  },

  plugins: [
    new webpack.HotModuleReplacementPlugin(),
    new webpack.DefinePlugin(env),
    new HtmlWebpackPlugin({
      template: 'src/index.html',
      inject: 'body',
      path: '/'
    }),
    new FlowWebpackPlugin({
      failOnError: true
    })
  ],

  resolve: {
    extensions: ['.js', '.jsx', '.scss'],
    alias: {
      '_config': `${__dirname}/config/config.development.js`
    },
    modules: [path.resolve(__dirname, 'src'), 'node_modules']
  },

  module: {
    rules: [
      {
        test: /\.jsx?$/,
        loaders: ['babel-loader'],
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
