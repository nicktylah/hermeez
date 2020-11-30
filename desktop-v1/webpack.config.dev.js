'use strict'

let webpack = require('webpack');
const CopyWebpackPlugin = require('copy-webpack-plugin');

// const env = {
//   'process.env': {
//     NODE_ENV: JSON.stringify('development')
//   }
// };

new webpack.SourceMapDevToolPlugin({
  filename: '[file].map'
});

module.exports = {
  devtool: '#inline-source-map',

  target: 'electron',

  entry: {
    //'react-hot-loader/patch',
    //'webpack-dev-server/client?http://localhost:4200',
    //'webpack/hot/only-dev-server',
    bundle: './src/index.jsx'
  },

  output: {
    path: `${__dirname}/src`,
    filename: '[name].js',
    publicPath: '/',
    devtoolModuleFilenameTemplate: '[absolute-resource-path]'
  },

  plugins: [
    //new webpack.DefinePlugin(env),
    //new webpack.HotModuleReplacementPlugin()
    new CopyWebpackPlugin([
      {
        from: `${__dirname}/src/static/images`,
        to: `${__dirname}/build/assets/images`
      }
    ])
  ],

  resolve: {
    extensions: ['.js', '.jsx'],
    alias: {
      '_components': `${__dirname}/src/components`
    }
  },

  module: {
    rules: [
      {
        test: /\.jsx?$/,
        use: [
          {
            loader: 'babel-loader'
          }
        ],
        include: [
          `${__dirname}/src`
        ]
      },
      {
        test: /\.s?css$/,
        use: [
          'style-loader',
          'css-loader?modules&localIdentName=[local]',
          'sass-loader'
        ]
      },
      {
        test: /\.png$/,
        loader: "url-loader",
        query: { mimetype: "image/png" }
      },
      {
        test: /\.svg$/,
        loader: 'svg-inline-loader'
      }
    ]
  }
};
