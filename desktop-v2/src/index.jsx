import React from 'react';
import ReactDOM from 'react-dom';
import { Provider } from 'react-redux';
import { AppContainer } from 'react-hot-loader';
import 'firebase/auth';
import 'firebase/storage';

import initStore from './store';
import Root from './components';

import { initFirebase } from './actions/app';

const store = initStore();
store.dispatch(initFirebase());

const render = Component => {
  ReactDOM.render(
    <AppContainer>
      <Provider store={store}>
        <Component store={store} />
      </Provider>
    </AppContainer>,
    document.getElementById('app')
  );
};

render(Root);

if (module.hot) {
  module.hot.accept('./components', () => {
    render(Root);
  });
}
