import { all, call, fork, put, take } from 'redux-saga/effects';
import firebase from 'firebase/app';
import { auth } from 'firebase';

import config from '_config';
import { FIREBASE_CONFIG } from '../../../config/constants';

import {
  INIT_APP,
  INIT_APP_FINISH,
  INIT_DB_FAILURE,
  INIT_DB_SUCCESS,
  AUTH_REQUEST,
  AUTH_SUCCESS,
  GET_CHECKPOINT_FAILURE,
  GET_CHECKPOINT_SUCCESS,
  SUBSCRIBE_MESSAGES,
  SIGN_OUT_REQUEST,
  INIT_FIREBASE,
  INIT_FIREBASE_SUCCESS,
  INIT_FIREBASE_FAILURE
} from '../../actions/actionTypes';
import { doAuth, initDB, signOut } from './tasks';
import { addNotification } from '../../actions/app';
import { fetchMessagesCheckpoint } from '../messages/tasks';
import { fetchThreads } from '../threads/tasks';

export function* watchSignIn() {
  while (true) {
    yield take(AUTH_REQUEST);
    yield call(doAuth);
  }
}

export function* watchSignOut() {
  while (true) {
    yield take(SIGN_OUT_REQUEST);
    yield call(signOut);
  }
}

export function* watchInitFirebase() {
  while (true) {
    yield take(INIT_FIREBASE);
    const firebaseApp = firebase.initializeApp(FIREBASE_CONFIG);
    if (!firebaseApp.options.projectId) {
      yield put({
        type: INIT_FIREBASE_FAILURE,
        payload: {
          firebaseApp
        }
      });
    } else {
      yield put({
        type: INIT_FIREBASE_SUCCESS,
        payload: {
          firebaseApp
        }
      });
    }
  }
}

export function* watchAuthSuccess() {
  while (true) {
    const action = yield take([INIT_APP_FINISH, AUTH_SUCCESS]);

    switch (action.type) {
      case INIT_APP_FINISH:
        if (action.payload.signedIn === false) {
          break;
        }
      // fallthrough
      case AUTH_SUCCESS:
        // Login succeeded, initialize IndexedDB
        yield fork(initDB);
        yield fork(() => Notification.requestPermission());
        const dbAction = yield take([INIT_DB_SUCCESS, INIT_DB_FAILURE]);
        switch (dbAction.type) {
          case INIT_DB_SUCCESS:
            // Lookup the last known message checkpoint
            yield fork(fetchMessagesCheckpoint);

            const checkpointAction = yield take([
              GET_CHECKPOINT_SUCCESS,
              GET_CHECKPOINT_FAILURE
            ]);

            // Start listening for new messages. We fire an action because of
            // the nature of SSE/redux. See sse.js for implementation. "0-0"
            // is the same as saying "no checkpoint"
            const checkpoint = checkpointAction.payload.checkpoint;

            // TODO: Get uid from auth
            const user = auth().currentUser;
            if (!user) {
              console.warn('No user logged in, cannot fetch contacts');
              return;
            }

            const subscribeUrl = `${config.apiRoot}/messages?uid=${
              user.uid
            }&checkpoint=${checkpoint}`;

            if (!config.offlineMode) {
              yield put({
                type: SUBSCRIBE_MESSAGES,
                payload: {
                  url: subscribeUrl
                }
              });
            }

            // TESTING
            // yield put(addNotification({ id: 1, message: 'Test notification' }));
            // yield call(notifyForNewMessages, {messages: [{
            //   sender: 0,
            //   body: 'this is a test message',
            //   date: new Date()
            // }]});

            // Get all threads in the DB
            yield fork(fetchThreads);
            break;

          case INIT_DB_FAILURE:
            // TODO: This is fatal
            break;
        }
    }
  }
}

export function* watchInitApp() {
  while (true) {
    const result = yield all([
      take(INIT_APP),
      take([INIT_FIREBASE_SUCCESS, INIT_FIREBASE_FAILURE])
    ]);
    if (result[1].type === INIT_FIREBASE_FAILURE) {
      // TODO: Fatal error case
    }

    const isSignedIn = () =>
      new Promise(resolve =>
        firebase.auth().onAuthStateChanged(user => resolve(user))
      );
    const user = yield call(isSignedIn);

    yield put({
      type: INIT_APP_FINISH,
      payload: { signedIn: user !== null },
      meta: {}
    });
  }
}
