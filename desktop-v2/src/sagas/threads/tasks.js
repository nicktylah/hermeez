import { call, put } from 'redux-saga/effects';

import indexedDBClient from '../../lib/indexed-db-client';
import {
  GET_THREADS_REQUEST,
  GET_THREADS_SUCCESS,
  GET_THREADS_FAILURE,
  SEARCH_THREADS,
  SEARCH_THREADS_SUCCESS,
  SEARCH_THREADS_FAILURE
} from '../../actions/actionTypes';

import type { Thread } from '../../types';

export function* fetchThreads(limit) {
  const db = () => indexedDBClient().fetchThreads({ limit });

  yield put({ type: GET_THREADS_REQUEST });

  try {
    const threads = yield call(db);
    const payload = { threads };

    yield put({
      type: GET_THREADS_SUCCESS,
      payload
    });
  } catch (error) {
    yield put({
      type: GET_THREADS_FAILURE,
      payload: error,
      error: true
    });
  }
}

// export function* searchThreads(query) {
//   const db = () => indexedDBClient().searchThreads({ query });

//   try {
//     const threads = yield call(db);
//     const payload = { threads };

//     yield put({
//       type: SEARCH_THREADS_SUCCESS,
//       payload
//     });
//   } catch (error) {
//     yield put({
//       type: SEARCH_THREADS_FAILURE,
//       payload: error,
//       error: true
//     });
//   }
// }
