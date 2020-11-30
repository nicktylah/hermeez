import { all, fork } from 'redux-saga/effects';

import * as app from './sagas/app';
import * as messages from './sagas/messages';
import * as threads from './sagas/threads';

/**
 * Combines sagas that export saga listeners
 */
function combineSagas(sagas) {
  const listeners = sagas.reduce((listeners, saga) => {
    Object.keys(saga).forEach(listenerKey => {
      listeners.push(fork(saga[listenerKey]));
    });

    return listeners;
  }, []);

  return all(listeners);
}

export default function* root() {
  yield combineSagas([app, messages, threads]);
}
