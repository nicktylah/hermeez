'use strict';

var _react = require('react');

var _react2 = _interopRequireDefault(_react);

var _reactDom = require('react-dom');

var _reactRedux = require('react-redux');

var _app = require('./components/app.jsx');

var _app2 = _interopRequireDefault(_app);

var _configureStore = require('./store/configureStore');

var _configureStore2 = _interopRequireDefault(_configureStore);

var _routes = require('./_routes');

var _routes2 = _interopRequireDefault(_routes);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

//import {Store} from 'react-chrome-redux';
var firebase = window.firebase;
//import {AppContainer} from 'react-hot-loader';


var store = (0, _configureStore2.default)();

var rootElem = document.getElementById('app');

firebase.auth().onAuthStateChanged(function (user) {
  console.log('user', user);
  if (user) {
    firebase.database.enableLogging(true);
    // Required for packaged app (alternative requires iframes and there's nasty CSP issues)
    firebase.database.INTERNAL.forceWebSockets();
    //console.log('Firebase database:', userDB);
    firebase.database().ref('users/' + user.uid + '/conversations/182').on('value', function (snapshot) {
      console.log(snapshot.val());
    });
    setInterval(function () {
      //firebase.database().ref('users/' + user.uid + '/test').set(123);
    }, 10000);
  } else {
    console.log('Not signed in');
  }
});

var email = 'redacted';
var password = 'redacted';

firebase.auth().signInWithEmailAndPassword(email, password).catch(function (err) {
  console.debug(err);
  // Handle Errors here.
  var errorCode = err.code;
  var errorMessage = err.message;
  if (errorCode === 'auth/user-not-found') {
    firebase.auth().createUserWithEmailAndPassword(email, password).catch(function (err) {
      console.error(err);
    });
  } else {
    console.error(err);
  }
});

(0, _reactDom.render)(_react2.default.createElement(
  _reactRedux.Provider,
  { store: store },
  _react2.default.createElement(_app2.default, null)
), rootElem);
//render(
//<AppContainer>
////<Provider store={store}>
//<App />
//<Routes />
////</Provider>,
//</AppContainer>,
//rootElem
//);

//if (module.hot) {
//module.hot.accept('./_routes', () => {
//const NextRoot = require('./_routes').default;
//render(
//<AppContainer>
//<Provider store={store}>
//<NextRoot />
//</Provider>
//</AppContainer>,
//rootElem
//);
//});
//}

;

(function () {
  if (typeof __REACT_HOT_LOADER__ === 'undefined') {
    return;
  }

  __REACT_HOT_LOADER__.register(firebase, 'firebase', '/Users/nick/github/android-messaging/chrome-app/src/index.jsx');

  __REACT_HOT_LOADER__.register(store, 'store', '/Users/nick/github/android-messaging/chrome-app/src/index.jsx');

  __REACT_HOT_LOADER__.register(rootElem, 'rootElem', '/Users/nick/github/android-messaging/chrome-app/src/index.jsx');

  __REACT_HOT_LOADER__.register(email, 'email', '/Users/nick/github/android-messaging/chrome-app/src/index.jsx');

  __REACT_HOT_LOADER__.register(password, 'password', '/Users/nick/github/android-messaging/chrome-app/src/index.jsx');
})();

;

//# sourceMappingURL=index.js.map