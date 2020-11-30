const webpack = require('webpack');
const CopyWebpackPlugin = require('copy-webpack-plugin');

// const env = {
//   'process.env': {
//     NODE_ENV: JSON.stringify('production')
//   }
// };

module.exports = {
  devtool: 'eval',

  target: 'electron',

  entry: {
    //'react-hot-loader/patch',
    //'webpack-dev-server/client?http://localhost:4200',
    //'webpack/hot/only-dev-server',
    bundle: './src/index.jsx'
  },

  output: {
    path: `${__dirname}/build/`,
    filename: 'bundle.js',
    pathinfo: true
  },

  plugins: [
    // new webpack.DefinePlugin(env),
    new webpack.optimize.UglifyJsPlugin({
      compressor: {
        screw_ie8: true,
        warnings: false
      }
    }),
    new CopyWebpackPlugin([
      {
        from: `${__dirname}/src/static/images`
      },
      {
        from: `${__dirname}/main.js`
      },
      {
        from: `${__dirname}/package.json`
      },
      {
        from: `${__dirname}/keyfile.json`
      },
      {
        from: `${__dirname}/src/index.html`
      },
      {
        from: `${__dirname}/src/reload.html`
      },
      {
        from: `${__dirname}/src/reload.js`
      },
      {
        from: `${__dirname}/assets`
      }
    ])
  ],

  node: {
    __dirname: false,
    __filename: false
  },

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
