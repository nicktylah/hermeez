const webpack = require('webpack');
const WebpackDevServer = require('webpack-dev-server');
const config = require('./webpack.config.dev');

const host = '0.0.0.0';
const port = 2007;

new WebpackDevServer(webpack(config), {
  contentBase: 'src/',
  publicPath: config.output.publicPath,
  hot: true,
  historyApiFallback: true,
  stats: {
    colors: true,
    chunks: false
  }
}).listen(port, host, (err) => {
  if (err) return console.log(err);

  console.log(`Server started @ ${host}:${port}`);
});
