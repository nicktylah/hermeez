import { compose, createStore, applyMiddleware } from 'redux';
import createSagaMiddleware from 'redux-saga';
import { createLogger } from 'redux-logger';
import rootReducer from './reducers';
import sagas from './sagas';
import { sse } from './sse';

const composeEnhancers = window.__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose;
const sagaMiddleware = createSagaMiddleware();

const middlewares = [sagaMiddleware, sse];

if (
  process.env.NODE_ENV === 'development' ||
  process.env.NODE_ENV === 'staging'
) {
  const logger = createLogger({
    collapsed: true,
    predicate: function(getState, action) {
      return action.type.indexOf('EFFECT_') === -1;
    }
  });
  middlewares.push(logger);
}

export default function initStore(initialState) {
  const store = createStore(
    rootReducer,
    initialState,
    composeEnhancers(applyMiddleware(...middlewares))
  );

  sagaMiddleware.run(sagas);

  if (module.hot) {
    module.hot.accept('./reducers', () => {
      store.replaceReducer(rootReducer);
    });
  }

  return store;
}
