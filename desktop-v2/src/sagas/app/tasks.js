import { call, put } from 'redux-saga/effects';
import { auth } from 'firebase/app';

import indexedDBClient from '../../lib/indexed-db-client';
import {
  AUTH_FAILURE,
  AUTH_SUCCESS,
  INIT_DB,
  INIT_DB_FAILURE,
  INIT_DB_SUCCESS,
  SIGN_OUT,
  UNSUBSCRIBE_MESSAGES
} from '../../actions/actionTypes';

import { HERMES_DB, HERMES_DB_VERSION } from '../../../config/constants';
import config from '_config';

export function* doAuth() {
  const provider = new auth.GoogleAuthProvider();
  let api = () => auth().signInWithPopup(provider);

  console.debug(`offlineMode: ${config.offlineMode}`);
  if (config.offlineMode) {
    console.warn('Skipping auth due to config value. Do not release to prod!');
    api = () => Promise.resolve();
  }

  try {
    const payload = yield call(api);
    const meta = {
      analytics: {}
    };

    yield put({
      type: AUTH_SUCCESS,
      payload,
      meta
    });
  } catch (error) {
    yield put({
      type: AUTH_FAILURE,
      payload: error,
      error: true
    });
  }
}

export function* signOut() {
  const signOut = () => auth().signOut();

  yield put({
    type: UNSUBSCRIBE_MESSAGES
  });

  try {
    const payload = yield call(signOut);
    const meta = {
      analytics: {}
    };

    yield put({
      type: SIGN_OUT,
      payload,
      meta
    });
  } catch (error) {
    yield put({
      type: SIGN_OUT,
      payload: error,
      error: true
    });
  }
}

export function* initDB() {
  const db = () => indexedDBClient(HERMES_DB, HERMES_DB_VERSION);

  yield put({ type: INIT_DB });

  try {
    const payload = yield call(db);
    const meta = {
      analytics: {}
    };

    window.db = payload;
    yield put({
      type: INIT_DB_SUCCESS,
      payload,
      meta
    });
  } catch (error) {
    yield put({
      type: INIT_DB_FAILURE,
      payload: error,
      error: true
    });
  }
}

export function* notify(
  title: string,
  options?: {
    dir?: string,
    lang?: string,
    badge?: USVString,
    body?: string,
    tag?: string,
    icon?: USVString,
    image?: USVString,
    data?: *,
    vibrate?: *,
    renotify?: boolean,
    requireInteraction?: boolean,
    actions?: Object[]
  }
) {
  if (!('Notification' in window)) {
    yield put({
      // TODO: make actual action
      type: 'NOTIFY_FAILURE',
      payload: new Error('This browser does not support system notifications'),
      error: true
    });
    return;
  }

  if (Notification.permission === 'granted') {
    const notification = new Notification(title, options);
    setTimeout(notification.close, 4000);
  }
}
